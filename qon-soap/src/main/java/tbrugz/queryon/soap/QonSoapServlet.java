package tbrugz.queryon.soap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.ResponseSpec;
import tbrugz.queryon.api.BaseApiServlet;
import tbrugz.queryon.api.ODataServlet;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.SchemaModelUtils;
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
	
	public static final String XMLNS = "xmlns";
	
	public static final String SUFFIX_REQUEST_ELEMENT = "Request";
	
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
				log.info("doGet: wsdl: queryString = "+req.getQueryString());
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
				log.info("doGet: pathInfo = "+req.getPathInfo());
				throw new BadRequestException("GET method only supported for retrieving wsdl");
			}
			//super.doGet(req, resp);
		}
		else if(req.getMethod().equalsIgnoreCase(METHOD_POST)) {
			String prefixSoapenv = "soapenv1";
			String prefixQueryon = null;
			
			try {
				Document doc = dBuilder.parse(req.getInputStream());
				log.info("doc: "+doc);
				Element el = doc.getDocumentElement();
				log.info("el: "+el.getTagName() + " // " + el.getNamespaceURI() + " // "+el.getPrefix() + " // "+el.getLocalName());
				NamedNodeMap nnm = el.getAttributes();
				int substrIdx = XMLNS.length()+1;
				for(int i=0;i<nnm.getLength();i++) {
					Node n = nnm.item(i);
					log.info("- ["+i+"]: "+n.getNodeName()+" // "+n.getNodeValue());
					if(n.getNodeName().startsWith(XMLNS+":")) {
						if(n.getNodeValue().equals(NS_SOAP_ENVELOPE)) {
							prefixSoapenv = n.getNodeName().substring(substrIdx);
						}
						if(n.getNodeValue().startsWith(NS_QON_PREFIX)) {
							prefixQueryon = n.getNodeName().substring(substrIdx);
						}
					}
				}
				log.info("prefixSoapenv: "+prefixSoapenv + " / prefixQueryon: " + prefixQueryon);
				if(prefixSoapenv!=null) {
					NodeList nl = el.getElementsByTagName(prefixSoapenv+":"+"Body");
					log.info("body:: nl.getLength(): "+nl.getLength());
					List<Element> els = XmlUtils.getElementsFromNodeList( nl.item(0).getChildNodes() );
					log.info("body content:: els.size: "+els.size());
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
				e.printStackTrace();
				throw new BadRequestException("Error parsing xml: "+e.getMessage(), e);
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
		//XXX: on exception throw soap fault... override doFacade() ? only for POST
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
		
		String modelId = SchemaModelUtils.getModelId(req);
		SchemaModel model = SchemaModelUtils.getModel(req.getServletContext(), modelId);
		if(model==null) {
			throw new BadRequestException("Invalid model");
		}

		//Set<String> schemaNames = new LinkedHashSet<String>();
		//Set<String> relationNames = new LinkedHashSet<String>();
		
		Set<View> vs = model.getViews();
		Set<Table> ts = model.getTables();
		
		// -- TYPES --
		
		{
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
			Element entity = createElementType(doc, t);
			schema.appendChild(entity);
			schema.appendChild(createListOfElement(doc, t));
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
			definitions.appendChild(createMessage(doc, normalize( v.getQualifiedName() )+"Input", normalize( v.getQualifiedName() )+SUFFIX_REQUEST_ELEMENT));
			definitions.appendChild(createMessage(doc, normalize( v.getQualifiedName() )+"Output", "listOf" + normalize( v.getQualifiedName() )));
		}
		for(Table t: ts) {
			definitions.appendChild(createMessage(doc, normalize( t.getQualifiedName() )+"Input", normalize( t.getQualifiedName() )+SUFFIX_REQUEST_ELEMENT));
			definitions.appendChild(createMessage(doc, normalize( t.getQualifiedName() )+"Output", "listOf" + normalize( t.getQualifiedName() )));
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
		address.setAttribute("location", host+contextPath+req.getServletPath());
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
		Element el = doc.createElement("xs:"+"element");
		el.setAttribute("name", "limit");
		el.setAttribute("type", "xs:" + "int" );
		all.appendChild(el);
		return element;
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

	Element createOperation(Document doc, String name, String prefix) {
		Element operation = doc.createElement("wsdl:"+"operation");
		operation.setAttribute("name", prefix + name);
		
		Element input = doc.createElement("wsdl:"+"input");
		input.setAttribute("name", name+"Input");
		input.setAttribute("message", "tns:"+name+"Input");
		operation.appendChild(input);

		Element output = doc.createElement("wsdl:"+"output");
		output.setAttribute("name", name+"Output");
		output.setAttribute("message", "tns:"+name+"Output");
		operation.appendChild(output);
		
		return operation;
	}
	
	Element createBindingOperation(Document doc, String name, String prefix) {
		Element operation = doc.createElement("wsdl:"+"operation");
		operation.setAttribute("name", prefix + name);
		
		Element soapOperation = doc.createElement("soap:operation");
		//soapOperation.setAttribute("style", "document");
		soapOperation.setAttribute("soapAction", ""); // soapAction?
		operation.appendChild(soapOperation);

		{
			Element input = doc.createElement("wsdl:"+"input");
			input.setAttribute("name", name+"Input");
			Element soapBody = doc.createElement("soap:body");
			soapBody.setAttribute("use", "literal");
			input.appendChild(soapBody);
			operation.appendChild(input);
		}

		{
			Element output = doc.createElement("wsdl:"+"output");
			output.setAttribute("name", name+"Output");
			Element soapBody = doc.createElement("soap:body");
			soapBody.setAttribute("use", "literal");
			output.appendChild(soapBody);
			operation.appendChild(output);
		}
		
		{
			Element fault = doc.createElement("wsdl:"+"fault");
			fault.setAttribute("name", "Exception");
			Element soapFault = doc.createElement("soap:fault");
			soapFault.setAttribute("name", "Exception");
			soapFault.setAttribute("use", "literal");
			fault.appendChild(soapFault);
			operation.appendChild(fault);
		}
		
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
	
}