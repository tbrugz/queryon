package tbrugz.queryon.soap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.ResponseSpec;
import tbrugz.queryon.api.BaseApiServlet;
import tbrugz.queryon.api.ODataServlet;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.MiscUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;

public class QonSoapServlet extends BaseApiServlet {

	private static final long serialVersionUID = 1L;

	private static final Log log = LogFactory.getLog(QonSoapServlet.class);
	
	public static final String NS_QON_PREFIX = "http://bitbucket.org/tbrugz/queryon/";
	
	public static final String NS_WSDL = "http://schemas.xmlsoap.org/wsdl/";
	public static final String NS_XMLSCHEMA = "http://www.w3.org/2001/XMLSchema"; // xmlns:xs 
	public static final String NS_SOAP = "http://schemas.xmlsoap.org/wsdl/soap/";
	public static final String NS_SOAP_ENVELOPE = "http://schemas.xmlsoap.org/soap/envelope/"; // "http://www.w3.org/2003/05/soap-envelope"; "http://www.w3.org/2001/12/soap-envelope";
	
	public static final String PREFIX_INSERT_ELEMENT = "insert";
	public static final String PREFIX_UPDATE_ELEMENT = "update";
	public static final String PREFIX_DELETE_ELEMENT = "delete";

	public static final String SUFFIX_REQUEST_ELEMENT = "Request";
	
	public static final String[] UNIQUE_FILTERS = { "filterEquals", "filterNotEquals", "filterGreaterThan", "filterGreaterOrEqual", "filterLessThan", "filterLessOrEqual" };
	public static final String[] MULTI_FILTERS = { "filterIn", "filterNotIn", "filterLike", "filterNotLike" };
	public static final String[] BOOLEAN_FILTERS = { "filterNull", "filterNotNull" };
	
	DocumentBuilderFactory dbFactory;
	DocumentBuilder dBuilder;
	
	String serviceName;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		serviceName = "queryOnService";
		
		try {
			dbFactory = DocumentBuilderFactory.newInstance();
			dBuilder = dbFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new ServletException(e); 
		}
	}
	
	@Override
	protected void doService(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if(req.getMethod().equalsIgnoreCase(METHOD_GET)) {
			if(req.getQueryString()!=null && req.getQueryString().startsWith("wsdl")) {
				log.info("doGet: wsdl: pathInfo = "+req.getPathInfo()+" ; queryString = "+req.getQueryString());
				try {
					//DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
					//DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
					DOMImplementation domImpl = dBuilder.getDOMImplementation();
					
					Document doc = makeWsdl(domImpl, req);
					resp.setContentType(ResponseSpec.MIME_TYPE_XML);
					ODataServlet.serialize(domImpl, doc, resp.getWriter());
				}
				catch(ParserConfigurationException e) {
					log.warn("doGet: wsdl: "+e);
					throw new BadRequestException("Error retrieving WSDL: "+e);
				}
				catch(IOException e) {
					log.warn("doGet: wsdl: "+e);
					throw new BadRequestException("Error retrieving WSDL: "+e);
				}
				return;
			}
			else {
				log.info("doGet: [no-wsdl] pathInfo = "+req.getPathInfo()+" ; queryString = "+req.getQueryString());
				throw new BadRequestException("GET method only supported for retrieving wsdl");
			}
			//super.doGet(req, resp);
		}
		else if(req.getMethod().equalsIgnoreCase(METHOD_POST)) {
			String prefixSoapenv = "soapenv1";
			String prefixQueryon = null;
			
			try {
				Document doc = dBuilder.parse(req.getInputStream());
				log.debug("doc: "+doc);
				
				//StringWriter sw = new StringWriter();
				//ODataServlet.serialize(dBuilder.getDOMImplementation(), doc, sw);
				//log.info("xml::\n"+sw);
				
				Element el = doc.getDocumentElement();
				log.debug("el: "+el.getTagName() + " // " + el.getNamespaceURI() + " // "+el.getPrefix() + " // "+el.getLocalName());
				/*NamedNodeMap nnm = el.getAttributes();
				int substrIdx = XmlUtils.XMLNS.length()+1;
				for(int i=0;i<nnm.getLength();i++) {
					Node n = nnm.item(i);
					log.info("- ["+i+"]: "+n.getNodeName()+" // "+n.getNodeValue());
					if(n.getNodeName().startsWith(XmlUtils.XMLNS+":")) {
						if(n.getNodeValue().equals(NS_SOAP_ENVELOPE)) {
							prefixSoapenv = n.getNodeName().substring(substrIdx);
						}
						if(n.getNodeValue().startsWith(NS_QON_PREFIX)) {
							prefixQueryon = n.getNodeName().substring(substrIdx);
						}
					}
				}*/
				prefixSoapenv = XmlUtils.searchForNamespace(el, NS_SOAP_ENVELOPE, false);
				prefixQueryon = XmlUtils.searchForNamespace(el, NS_QON_PREFIX, true);
				log.debug("prefixSoapenv: "+prefixSoapenv + " / prefixQueryon: " + prefixQueryon);
				if(prefixSoapenv!=null) {
					NodeList nl = el.getElementsByTagName(prefixSoapenv+":"+"Body");
					//log.debug("body:: nl.getLength(): "+nl.getLength());
					List<Element> els = XmlUtils.getElementsFromNodeList( nl.item(0).getChildNodes() );
					//log.debug("body content:: els.size: "+els.size());
					if(els.size()==1) {
						SoapRequest.setAttributesOnRequest(req, els.get(0), prefixQueryon);
						doServiceIntern(req, resp);
					}
					else {
						throw new BadRequestException("Soap Body has more than 1 element [size="+els.size()+"]");
					}
				}
			}
			catch (SAXException e) {
				log.warn("Exception parsing xml: "+e); //, e);
				XmlUtils.writeSoapFault(resp, e, debugMode);
				//throw new BadRequestException("Error parsing xml: "+e.getMessage(), e);
			}
		}
		else {
			throw new BadRequestException("Method not allowed: "+req.getMethod());
		}
	}
	
	@Override
	protected SoapRequest getRequestSpec(HttpServletRequest req) throws ServletException, IOException {
		//SoapRequest.setAttributes(req, requestEl, nsPrefix);
		return new SoapRequest(/*requestEl, nsPrefix,*/ dsutils, req, prop);
	}
	
	protected void doServiceIntern(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		//processSoapMessage(els.get(0), prefixQueryon, req, resp);
		
		// on exception throw soap fault... override doFacade() ? only for POST
		try {
			super.doService(req, resp);
		}
		catch(Exception e) {
			log.warn("doServiceIntern: Exeption: "+e);
			if(! (e instanceof BadRequestException)) {
				log.info("doServiceIntern: Exeption: "+e.getMessage(), e);
			}
			XmlUtils.writeSoapFault(resp, e, debugMode);
		}
	}
	
	@Override
	protected ActionType getActionType(RequestSpec reqspec, DBIdentifiable dbobj) {
		return ((SoapRequest) reqspec).atype;
	}
	
	/*void processSoapMessage(Element requestEl, String nsPrefix, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		SoapRequest.setAttributesOnRequest(req, requestEl, nsPrefix);
		SoapRequest reqspec = getRequestSpec(req);
		//SoapRequest reqspec = new SoapRequest(dsutils, req, prop);
		//log.info("request: "+reqspec.toStringDebug());
	}*/

	@Override
	protected void postService(SchemaModel model, RequestSpec reqspec, HttpServletRequest req, HttpServletResponse resp) {
		if(reqspec instanceof SoapRequest) {
			log.info("request: "+ ((SoapRequest)reqspec).toStringDebug());
		}
		else {
			log.info("request should be instanceof SoapRequest: "+ reqspec.getClass());
		}
	}
	
	/*String getBaseNamespace() {
		return "http://bitbucket.org/tbrugz/queryon/"; // + serviceName + wsdl|xsd
	}*/

	String getWsdlNamespace() {
		return NS_QON_PREFIX + serviceName + ".wsdl";
	}

	String getXsdNamespace() {
		return NS_QON_PREFIX + serviceName + ".xsd";
	}
	
	Document makeWsdl(DOMImplementation domImpl, HttpServletRequest req) throws ParserConfigurationException {
		
		String modelId = SoapRequest.getSoapModelId(req);
		SchemaModel model = SchemaModelUtils.getModel(req.getServletContext(), modelId);
		if(model==null) {
			throw new BadRequestException("Invalid model ["+modelId+"]");
		}
		String serviceName = this.serviceName + (modelId!=null ? MiscUtils.initCaps(modelId) : "");
		
		Document doc = domImpl.createDocument(NS_WSDL, "wsdl:"+"definitions", null);
		Element definitions = doc.getDocumentElement();
		
		definitions.setAttribute("name", serviceName);
		definitions.setAttribute("targetNamespace", getWsdlNamespace());
		// xmlns:tns="http://example.com/stockquote.wsdl"
		definitions.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:wsdl", NS_WSDL);
		definitions.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xs", NS_XMLSCHEMA);
		definitions.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:soap", NS_SOAP);
		definitions.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsd1", getXsdNamespace());
		definitions.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:tns", getWsdlNamespace());
		// xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"

		/*
		xmlns:tns="http://example.com/stockquote.wsdl"
		xmlns:xsd1="http://example.com/stockquote.xsd"
		xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
		xmlns="http://schemas.xmlsoap.org/wsdl/"
		*/

		Element types = doc.createElement("wsdl:"+"types");
		//Element schema = doc.createElementNS("http://www.w3.org/2000/10/XMLSchema", "xs:"+"schema");
		Element schema = doc.createElement("xs:"+"schema");
		schema.setAttribute("targetNamespace", getXsdNamespace());
		schema.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsd1", getXsdNamespace());
		//schema.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:tns", getXsdNamespace());
		
		types.appendChild(schema);

		//Set<String> schemaNames = new LinkedHashSet<String>();
		//Set<String> relationNames = new LinkedHashSet<String>();
		
		Set<View> vs = model.getViews();
		Set<Table> ts = model.getTables();
		
		// -- TYPES --
		
		{
			// generic 'select' types
			schema.appendChild(createFieldsType(doc));
			schema.appendChild(createFieldWithDirectionType(doc));
			schema.appendChild(createOrderType(doc));
			createFiltersTypes(doc, schema);
			//XXX: add generic types
			// generic insert/update/delete types
			schema.appendChild(createGeneratedKeyType(doc));
			schema.appendChild(createUpdateInfoType(doc));
			
			for(View v: vs) {
				schema.appendChild(createElementRequest(doc, v));
				Element entity = createElementType(doc, v);
				schema.appendChild(entity);
				schema.appendChild(createListOfElement(doc, v));
				//schemaNames.add(v.getSchemaName());
				//relationNames.add(v.getQualifiedName());
			}
			for(Table t: ts) {
				schema.appendChild(createElementRequest(doc, t));
				// relation
				Element entity = createElementType(doc, t);
				schema.appendChild(entity);
				// uk
				// for each table, create <Relation>KeyType to be used by update & delete - could also be returned by insert, update & exec
				Constraint uk = SchemaModelUtils.getPK(t);
				boolean hasUk = uk!=null && uk.getUniqueColumns()!=null;
				if(hasUk) {
					schema.appendChild(createElementKeyType(doc, t, uk));
				}
				// listOfRelation
				schema.appendChild(createListOfElement(doc, t));
				// insert/update/delete
				schema.appendChild(createElementInsert(doc, t));
				if(hasUk) {
					schema.appendChild(createElementUpdate(doc, t));
				}
				
				//schemaNames.add(t.getSchemaName());
				//relationNames.add(t.getQualifiedName());
			}
			
			/*Set<ExecutableObject> eos = model.getExecutables();
			for(ExecutableObject eo: eos) {
				Element action = createAction(doc, eo);
				schema.appendChild(action);
				Element actionImport = createActionImport(doc, eo);
				schema.appendChild(actionImport);
			}*/
		}

		definitions.appendChild(types);
		
		// -- MESSAGES --
		
		for(View v: vs) {
			String relName = normalize( v.getQualifiedName() );
			definitions.appendChild(createMessage(doc, relName+"Input", relName+SUFFIX_REQUEST_ELEMENT));
			definitions.appendChild(createMessage(doc, relName+"Output", "listOf" + relName));
		}
		for(Table t: ts) {
			String relName = normalize( t.getQualifiedName() );
			definitions.appendChild(createMessage(doc, relName+"Input", relName+SUFFIX_REQUEST_ELEMENT));
			definitions.appendChild(createMessage(doc, relName+"Output", "listOf" + relName));

			// insert
			definitions.appendChild(createMessage(doc, PREFIX_INSERT_ELEMENT + relName + "Input", PREFIX_INSERT_ELEMENT + relName ));
			definitions.appendChild(createMessage(doc, PREFIX_INSERT_ELEMENT + relName + "Output", "updateInfoType"));
			// update
			if(hasUniqueKey(t)) {
				definitions.appendChild(createMessage(doc, PREFIX_UPDATE_ELEMENT + relName + "Input", PREFIX_UPDATE_ELEMENT + relName ));
				definitions.appendChild(createMessage(doc, PREFIX_UPDATE_ELEMENT + relName + "Output", "updateInfoType"));
			}
		}

		// -- PORTTYPE/OPERATIONS --

		{
		Element portType = doc.createElement("wsdl:"+"portType");
		portType.setAttribute("name", serviceName+"PortType");
		
		for(View v: vs) {
			portType.appendChild(createOperation(doc, normalize( v.getQualifiedName() ), "get"));
		}
		for(Table t: ts) {
			portType.appendChild(createOperation(doc, normalize( t.getQualifiedName() ), "get"));
			portType.appendChild(createOperation(doc, normalize( t.getQualifiedName() ), "insert", "insert"));
			if(hasUniqueKey(t)) {
				portType.appendChild(createOperation(doc, normalizeRelationName(t), "update", "update"));
			}
		}
		
		definitions.appendChild(portType);
		}
		
		// -- BINDING --

		{
		Element binding = doc.createElement("wsdl:"+"binding");
		binding.setAttribute("name", serviceName+"Binding");
		binding.setAttribute("type", "tns:"+serviceName+"PortType");

		Element soapBinding = doc.createElement("soap:binding");
		soapBinding.setAttribute("style", "document");
		soapBinding.setAttribute("transport", "http://schemas.xmlsoap.org/soap/http");
		binding.appendChild(soapBinding);
		
		for(View v: vs) {
			binding.appendChild(createBindingOperation(doc, normalize( v.getQualifiedName() ), "get"));
		}
		for(Table t: ts) {
			binding.appendChild(createBindingOperation(doc, normalize( t.getQualifiedName() ), "get"));
			binding.appendChild(createBindingOperation(doc, normalize( t.getQualifiedName() ), "insert", "insert"));
			if(hasUniqueKey(t)) {
				binding.appendChild(createBindingOperation(doc, normalizeRelationName(t), "update", "update"));
			}
		}
		
		
		definitions.appendChild(binding);
		}
		
		// -- SERVICE --

		{
		String host = getServiceHost(req, true, false);
		String contextPath = getServletContext().getContextPath();
		
		Element service = doc.createElement("wsdl:"+"service");
		service.setAttribute("name", serviceName);

		Element documentation = doc.createElement("wsdl:"+"documentation");
		documentation.setTextContent(""); //XXX documentation
		service.appendChild(documentation);
		
		Element port = doc.createElement("wsdl:"+"port");
		port.setAttribute("name", serviceName+"Port");
		port.setAttribute("binding", "tns:"+serviceName+"Binding");
		service.appendChild(port);

		Element address = doc.createElement("soap:address");
		address.setAttribute("location", host+contextPath+req.getServletPath()
			+(modelId!=null?req.getPathInfo():"")
			);
		port.appendChild(address);
		
		definitions.appendChild(service);
		}
		
		return doc;
	}
	
	Element createElementType(Document doc, Relation r) {
		//Element element = doc.createElement("xs:"+"element");
		//element.setAttribute("name", normalize( r.getQualifiedName() ));
		Element complexType = doc.createElement("xs:"+"complexType");
		complexType.setAttribute("name", normalize( r.getQualifiedName() ) + "Type");
		//element.appendChild(complexType);
		Element all = doc.createElement("xs:"+"all");
		complexType.appendChild(all);
		/*Constraint pk = SchemaModelUtils.getPK(r);
		if(pk!=null) {
			Element key = doc.createElement("Key");
			for(String col: pk.getUniqueColumns()) {
				Element propRef = doc.createElement("PropertyRef");
				propRef.setAttribute("Name", col);
				key.appendChild(propRef);
			}
			element.appendChild(key);
		}*/
		for(int i=0;i<r.getColumnCount();i++) {
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", r.getColumnNames().get(i));
			el.setAttribute("type", "xs:" + getElementType(r.getColumnTypes().get(i)) );
			el.setAttribute("minOccurs", "0");
			el.setAttribute("maxOccurs", "1");
			all.appendChild(el);
		}
		return complexType;
	}

	Element createElementKeyType(Document doc, Relation r, Constraint uk) {
		Element complexType = doc.createElement("xs:"+"complexType");
		complexType.setAttribute("name", normalizeRelationName(r) + "KeyType");
		Element all = doc.createElement("xs:"+"all");
		complexType.appendChild(all);
		for(int i=0;i<uk.getUniqueColumns().size();i++) {
			String ukColName = uk.getUniqueColumns().get(i);
			int idx = r.getColumnNames().indexOf(ukColName);
			if(idx==-1) {
				log.warn("column "+ukColName+" not found in relation "+r.getQualifiedName());
				continue;
			}
			
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", ukColName);
			el.setAttribute("type", "xs:" + getElementType(r.getColumnTypes().get(idx)) );
			all.appendChild(el);
		}
		return complexType;
	}
	
	Element createListOfElement(Document doc, Relation r) {
		Element listElement = doc.createElement("xs:"+"element");
		listElement.setAttribute("name", "listOf" + normalize( r.getQualifiedName() ));
		
		Element complexType = doc.createElement("xs:"+"complexType");
		listElement.appendChild(complexType);
		
		Element sequence = doc.createElement("xs:"+"sequence");
		complexType.appendChild(sequence);

		Element element = doc.createElement("xs:"+"element");
		element.setAttribute("name", normalize( r.getQualifiedName() ));
		element.setAttribute("type", "xsd1:" + normalize( r.getQualifiedName() ) + "Type" );
		element.setAttribute("minOccurs", "0");
		element.setAttribute("maxOccurs", "unbounded");
		sequence.appendChild(element);
		
		return listElement;
	}

	Element createElementRequest(Document doc, Relation r) {
		Element element = doc.createElement("xs:"+"element");
		element.setAttribute("name", normalize( r.getQualifiedName() )+SUFFIX_REQUEST_ELEMENT);
		Element complexType = doc.createElement("xs:"+"complexType");
		element.appendChild(complexType);
		Element all = doc.createElement("xs:"+"all");
		complexType.appendChild(all);
		
		// XXX request parameters...
		
		if(r instanceof Query) {
			Query q = (Query) r;
			List<String> namedParameters = q.getNamedParameterNames();
			if(namedParameters!=null) {
				Set<String> addedParams = new HashSet<>();
				//log.info("query [np] "+q.getName()+": parameterCount=="+q.getParameterCount()+" / pnames=="+q.getNamedParameterNames()+" / ptypes=="+q.getParameterTypes());
				for(int i=0;i<q.getParameterCount();i++) {
					String param = namedParameters.get(i);
					if(addedParams.contains(param)) { continue; }
					addedParams.add(param);
					
					String type = "string";
					List<String> pTypes = q.getParameterTypes();
					if(pTypes!=null && i<pTypes.size()) {
						type = getElementType(q.getParameterTypes().get(i));
					}
					Element el = doc.createElement("xs:"+"element");
					el.setAttribute("name", param);
					el.setAttribute("type", "xs:" + type );
					el.setAttribute("minOccurs", "1");
					el.setAttribute("maxOccurs", "1");
					all.appendChild(el);
				}
			}
			else {
				//log.info("query [pp] "+q.getName()+" count=="+q.getParameterCount());
				if(q.getParameterCount()!=null) {
					for(int i=0;i<q.getParameterCount();i++) {
						String type = "string";
						List<String> pTypes = q.getParameterTypes();
						if(pTypes!=null && i<pTypes.size()) {
							type = getElementType(q.getParameterTypes().get(i));
						}
						Element el = doc.createElement("xs:"+"element");
						el.setAttribute("name", "parameter"+(i+1));
						el.setAttribute("type", "xs:" + type );
						el.setAttribute("minOccurs", "1");
						el.setAttribute("maxOccurs", "1");
						all.appendChild(el);
					}
				}
			}
		}
		
		{
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", RequestSpec.PARAM_LIMIT);
			el.setAttribute("type", "xs:" + "int" );
			el.setAttribute("minOccurs", "0");
			el.setAttribute("maxOccurs", "1");
			all.appendChild(el);
		}
		{
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", RequestSpec.PARAM_OFFSET);
			el.setAttribute("type", "xs:" + "int" );
			el.setAttribute("minOccurs", "0");
			el.setAttribute("maxOccurs", "1");
			all.appendChild(el);
		}
		{
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", RequestSpec.PARAM_DISTINCT);
			el.setAttribute("type", "xs:" + "boolean" );
			el.setAttribute("minOccurs", "0");
			el.setAttribute("maxOccurs", "1");
			all.appendChild(el);
		}
		{
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", RequestSpec.PARAM_FIELDS);
			el.setAttribute("type", "xsd1:" + "fieldsType" );
			el.setAttribute("minOccurs", "0");
			el.setAttribute("maxOccurs", "1");
			all.appendChild(el);
		}
		{
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", RequestSpec.PARAM_ORDER);
			el.setAttribute("type", "xsd1:" + "orderType" );
			el.setAttribute("minOccurs", "0");
			el.setAttribute("maxOccurs", "1");
			all.appendChild(el);
		}
		{
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", "filters");
			el.setAttribute("type", "xsd1:" + "filtersType" );
			el.setAttribute("minOccurs", "0");
			el.setAttribute("maxOccurs", "1");
			all.appendChild(el);
		}
		return element;
	}

	Element createFieldsType(Document doc) {
		Element complexType = doc.createElement("xs:"+"complexType");
		complexType.setAttribute("name", "fieldsType");
		Element all = doc.createElement("xs:"+"sequence");
		complexType.appendChild(all);
		{
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", "field");
			el.setAttribute("type", "xs:" + "string" );
			el.setAttribute("minOccurs", "1");
			all.appendChild(el);
		}
		return complexType;
	}

	Element createFieldWithDirectionType(Document doc) {
		// https://stackoverflow.com/q/376582/616413
		Element complexType = doc.createElement("xs:"+"complexType");
		complexType.setAttribute("name", "fieldWithDirectionType");
		
		Element simpleContent = doc.createElement("xs:"+"simpleContent");
		complexType.appendChild(simpleContent);
		
		Element extension = doc.createElement("xs:"+"extension");
		extension.setAttribute("base", "xs:"+"string");
		simpleContent.appendChild(extension);

		Element attribute = doc.createElement("xs:"+"attribute");
		attribute.setAttribute("name", "direction");
		attribute.setAttribute("type", "xs:"+"string");
		extension.appendChild(attribute);
		
		return complexType;
	}
	
	Element createOrderType(Document doc) {
		Element complexType = doc.createElement("xs:"+"complexType");
		complexType.setAttribute("name", "orderType");
		Element all = doc.createElement("xs:"+"sequence");
		complexType.appendChild(all);
		{
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", "field");
			el.setAttribute("type", "xsd1:" + "fieldWithDirectionType" );
			el.setAttribute("minOccurs", "1");
			all.appendChild(el);
		}
		return complexType;
	}

	Element createGeneratedKeyType(Document doc) {
		Element complexType = doc.createElement("xs:"+"complexType");
		complexType.setAttribute("name", "generatedKeyType");
		Element all = doc.createElement("xs:"+"sequence");
		complexType.appendChild(all);
		{
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", "value");
			el.setAttribute("type", "xs:"+"string" );
			el.setAttribute("minOccurs", "1");
			all.appendChild(el);
		}
		return complexType;
	}

	Element createUpdateInfoType(Document doc) {
		Element infoElement = doc.createElement("xs:"+"element");
		infoElement.setAttribute("name", "updateInfoType");
		
		Element complexType = doc.createElement("xs:"+"complexType");
		infoElement.appendChild(complexType);
		
		Element all = doc.createElement("xs:"+"all");
		complexType.appendChild(all);
		
		{
			Element element = doc.createElement("xs:"+"element");
			element.setAttribute("name", "generatedKey");
			element.setAttribute("type", "xsd1:" + "generatedKeyType" );
			element.setAttribute("maxOccurs", "1");
			all.appendChild(element);
		}
		{
			Element element = doc.createElement("xs:"+"element");
			element.setAttribute("name", "updateCount");
			//element.setAttribute("type", "xsd1:" + "updateCountType" );
			element.setAttribute("type", "xs:" + "int" );
			element.setAttribute("maxOccurs", "1");
			all.appendChild(element);
		}
		
		return infoElement;
	}
	
	Element createElementInsert(Document doc, Relation r) {
		return createElementUpdateType(doc, PREFIX_INSERT_ELEMENT, r, false);
	}

	Element createElementUpdate(Document doc, Relation r) {
		return createElementUpdateType(doc, PREFIX_UPDATE_ELEMENT, r, true);
	}
	
	Element createElementUpdateType(Document doc, String prefix, Relation r, boolean addKey) {
		Element element = doc.createElement("xs:"+"element");
		element.setAttribute("name", prefix + normalizeRelationName(r));
		Element complexType = doc.createElement("xs:"+"complexType");
		element.appendChild(complexType);
		Element all = doc.createElement("xs:"+"all");
		complexType.appendChild(all);
		
		Element innerElem = doc.createElement("xs:"+"element");
		innerElem.setAttribute("name", "entity");
		innerElem.setAttribute("type", "xsd1:" + normalizeRelationName(r) + "Type" );
		all.appendChild(innerElem);
		
		if(addKey) {
			Element innerKeyElem = doc.createElement("xs:"+"element");
			innerKeyElem.setAttribute("name", "filterKey");
			innerKeyElem.setAttribute("type", "xsd1:" + normalizeRelationName(r) + "KeyType" );
			all.appendChild(innerKeyElem);
		}
		
		/*
		for(int i=0;i<r.getColumnCount();i++) {
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", r.getColumnNames().get(i));
			el.setAttribute("type", "xs:" + getElementType(r.getColumnTypes().get(i)) );
			all.appendChild(el);
		}
		*/
		
		return element;
	}

	void createFiltersTypes(Document doc, Element schema) {
		schema.appendChild(createFilterType(doc, "uniqueValueFilterType", true, 1, 1));
		schema.appendChild(createFilterType(doc, "multiValueFilterType", true, 1, null));
		schema.appendChild(createFilterType(doc, "booleanValuedFilterType", false, null, null));

		Element filtersContainer = null;
		{
			Element filtersType = doc.createElement("xs:"+"complexType");
			filtersType.setAttribute("name", "filtersType");
			filtersContainer = doc.createElement("xs:"+"all");
			filtersType.appendChild(filtersContainer);
			schema.appendChild(filtersType);
		}
		
		for(String filter: UNIQUE_FILTERS) {
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", filter);
			el.setAttribute("type", "xsd1:" + "uniqueValueFilterType" );
			filtersContainer.appendChild(el);
		}
		for(String filter: MULTI_FILTERS) {
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", filter);
			el.setAttribute("type", "xsd1:" + "multiValueFilterType" );
			filtersContainer.appendChild(el);
		}
		for(String filter: BOOLEAN_FILTERS) {
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", filter);
			el.setAttribute("type", "xsd1:" + "booleanValuedFilterType" );
			filtersContainer.appendChild(el);
		}
	}
	
	Element createFilterType(Document doc, String name, boolean includeValues, Integer minValueOccurs, Integer maxValueOccurs) {
		Element complexType = doc.createElement("xs:"+"complexType");
		complexType.setAttribute("name", name);
		Element all = doc.createElement("xs:"+"sequence");
		complexType.appendChild(all);
		{
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", "field");
			el.setAttribute("type", "xs:" + "string" );
			el.setAttribute("minOccurs", "1");
			el.setAttribute("maxOccurs", "1");
			all.appendChild(el);
		}
		if(includeValues) {
			Element el = doc.createElement("xs:"+"element");
			el.setAttribute("name", "value");
			el.setAttribute("type", "xs:" + "string" ); // boolean, integer, float, date, string based on field type?
			if(minValueOccurs!=null) {
				el.setAttribute("minOccurs", String.valueOf(minValueOccurs));
			}
			if(maxValueOccurs!=null) {
				el.setAttribute("maxOccurs", String.valueOf(maxValueOccurs));
			}
			all.appendChild(el);
		}
		return complexType;
	}
	
	Element createMessage(Document doc, String name, String elementName) {
		Element element = doc.createElement("wsdl:"+"message");
		element.setAttribute("name", name);
		Element part = doc.createElement("wsdl:"+"part");
		part.setAttribute("name", "body");
		part.setAttribute("element", "xsd1:"+elementName);
		element.appendChild(part);
		return element;
	}

	Element createOperation(Document doc, String name, String namePrefix) {
		return createOperation(doc, name, namePrefix, "");
	}
	
	Element createOperation(Document doc, String name, String namePrefix, String typePrefix) {
		Element operation = doc.createElement("wsdl:"+"operation");
		operation.setAttribute("name", namePrefix + name);
		
		Element input = doc.createElement("wsdl:"+"input");
		input.setAttribute("name", typePrefix+name+"Input");
		input.setAttribute("message", "tns:"+typePrefix+name+"Input");
		operation.appendChild(input);

		Element output = doc.createElement("wsdl:"+"output");
		output.setAttribute("name", typePrefix+name+"Output");
		output.setAttribute("message", "tns:"+typePrefix+name+"Output");
		operation.appendChild(output);
		
		return operation;
	}

	Element createBindingOperation(Document doc, String name, String namePrefix) {
		return createBindingOperation(doc, name, namePrefix, "");
	}
	
	Element createBindingOperation(Document doc, String name, String namePrefix, String typePrefix) {
		Element operation = doc.createElement("wsdl:"+"operation");
		operation.setAttribute("name", namePrefix + name);
		
		Element soapOperation = doc.createElement("soap:operation");
		//soapOperation.setAttribute("style", "document");
		soapOperation.setAttribute("soapAction", ""); // soapAction?
		operation.appendChild(soapOperation);

		{
			Element input = doc.createElement("wsdl:"+"input");
			input.setAttribute("name", typePrefix+name+"Input");
			Element soapBody = doc.createElement("soap:body");
			soapBody.setAttribute("use", "literal");
			input.appendChild(soapBody);
			operation.appendChild(input);
		}

		{
			Element output = doc.createElement("wsdl:"+"output");
			output.setAttribute("name", typePrefix+name+"Output");
			Element soapBody = doc.createElement("soap:body");
			soapBody.setAttribute("use", "literal");
			output.appendChild(soapBody);
			operation.appendChild(output);
		}
		
		/*{
			Element fault = doc.createElement("wsdl:"+"fault");
			fault.setAttribute("name", "Exception");
			Element soapFault = doc.createElement("soap:fault");
			soapFault.setAttribute("name", "Exception");
			soapFault.setAttribute("use", "literal");
			fault.appendChild(soapFault);
			operation.appendChild(fault);
		}*/
		
		return operation;
	}
	
	// https://www.w3.org/TR/xmlschema-2/
	String getElementType(String ctype) {
		if(ctype==null) { return "string"; }
		String upper = ctype.toUpperCase();
		
		boolean isInt = DBUtil.INT_COL_TYPES_LIST.contains(upper);
		if(isInt) {
			return "int";
		}
		boolean isFloat = DBUtil.FLOAT_COL_TYPES_LIST.contains(upper);
		if(isFloat) {
			return "float";
		}
		boolean isDate = DBUtil.DATE_COL_TYPES_LIST.contains(upper);
		if(isDate) {
			// http://www.datypic.com/sc/xsd/t-xsd_date.html
			// http://www.datypic.com/sc/xsd/t-xsd_dateTime.html
			return "dateTime"; //XXX: date or dateTime?
		}
		boolean isBoolean = DBUtil.BOOLEAN_COL_TYPES_LIST.contains(upper);
		if(isBoolean) {
			return "boolean";
		}
		boolean isBlob = DBUtil.BLOB_COL_TYPES_LIST.contains(upper);
		if(isBlob) {
			return "hexBinary"; // XXX blob: hexBinary or base64Binary?
		}
		
		return "string";
	}
	
	static String normalizeRelationName(Relation r) {
		return normalize( r.getQualifiedName() );
	}
	
	static String normalize(String s) {
		return s.replaceAll(" ", "_");
		//return s.replaceAll("\\.", "_");
	}
	
	static String getServiceHost(HttpServletRequest req, boolean addScheme, boolean useCanonicalHost) {
		String scheme = req.getScheme();
		String serverName = req.getServerName();
		//String localHostname = InetAddress.getLocalHost().getHostName().toLowerCase();
		
		String hostname = serverName;
		if(useCanonicalHost) {
			try {
				String canonicalLocalHostname = InetAddress.getLocalHost().getCanonicalHostName().toLowerCase();
				hostname = canonicalLocalHostname;
			} catch (UnknownHostException e) {
				log.warn("Error in getCanonicalHostName()", e);
			}
		}
		//log.info("serverName: "+serverName+" ; localHostname: "+localHostname+" ; canonicalLocalHostname: "+canonicalLocalHostname+" ; -> hostname: "+hostname);
		
		int port = req.getServerPort();
		//String contextPath = req.getServletContext().getContextPath();
		//log.info("request: scheme="+scheme+" ; hostname="+hostname+" ; port="+port+" ; contextPath="+contextPath);
		
		String host = hostname;
		if( ("http".equals(scheme) && port==80) ||
			("https".equals(scheme) && port==443)) {
			// nothing to do yet
		}
		else {
			host += ":"+port;
		}
		
		return (addScheme?scheme+"://":"") + host;
	}
	
	@Override
	protected void setResponseStatus(HttpServletResponse resp, int status) {
	}
	
	@Override
	protected void writeUpdateCount(RequestSpec reqspec, HttpServletResponse resp, int count, String action) throws IOException {
		resp.setContentType(SoapDumpSyntax.SOAP_MIMETYPE);
		String envPrefix = SoapDumpSyntax.DEFAULT_SOAPENV_PREFIX;
		String qonPrefix = "ns1";
		
		StringBuilder sb = new StringBuilder();
		sb.append(SoapDumpSyntax.getSoapHeader(envPrefix));
		
		sb.append("<"+qonPrefix+":"+"updateInfo"+" xmlns:"+qonPrefix+"=\""+QonSoapServlet.NS_QON_PREFIX+"\">\n");
		//XXX generatedKey...
		sb.append("<updateCount>"+count+"</updateCount>\n");
		sb.append("</"+qonPrefix+":"+"updateInfo"+">\n");
		
		sb.append(SoapDumpSyntax.getSoapFooter(envPrefix));
		resp.getWriter().write(sb.toString());
		
		log.debug("writeUpdateCount: "+ count + " " + (count>1?"rows":"row") + " " + action);
		//log.info("writeUpdateCount::\n"+sb.toString());
	}

	static boolean hasUniqueKey(Relation r) {
		Constraint uk = SchemaModelUtils.getPK(r);
		return uk!=null && uk.getUniqueColumns()!=null;
	}
	
}
