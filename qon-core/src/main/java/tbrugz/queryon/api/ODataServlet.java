package tbrugz.queryon.api;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.ResponseSpec;
import tbrugz.queryon.auth.AuthActions;
import tbrugz.queryon.auth.UserInfo;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.syntaxes.ODataJsonSyntax;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.MiscUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.datadump.DumpSyntaxInt;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.ExecutableParameter;
import tbrugz.sqldump.dbmodel.FK;
import tbrugz.sqldump.dbmodel.NamedDBObject;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.resultset.ResultSetListAdapter;

public class ODataServlet extends BaseApiServlet {
	
	public static class Entity {
		//String schema;
		final String name;
		final String kind;
		final String url;
		
		public Entity(String schema, String name, String kind) {
			//this.schema = schema;
			String fullName = (schema!=null?schema+".":"")+name;
			this.name = fullName;
			this.kind = kind;
			this.url = fullName;
		}
		
		public String getName() {
			return name;
		}
		public String getKind() {
			return kind;
		}
		public String getUrl() {
			return url;
		}
	}
	
	/**
	 * see Error Response: http://docs.oasis-open.org/odata/odata-json-format/v4.0/os/odata-json-format-v4.0-os.html#_Toc372793091
	 */
	public class ErrorResponse {
		final String code;
		final String message;
		//XXX: target, details, innererror
		
		public ErrorResponse(int code, String message) {
			this.code = String.valueOf(code);
			this.message = message;
		}
	}

	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(ODataServlet.class);
	
	static final String ODATA_VERSION = "4.0";
	
	static final String odataNS = "OData.QueryOn";
	
	// see: https://www.odata.org/getting-started/advanced-tutorial/#singleton
	static final String QUERY_CURRENTUSER = "currentUser";
	
	static final String[] singletonQueries = { QUERY_CURRENTUSER };
	static final Class<?>[] singletonQueryBeans = { UserInfo.class };
	
	/*@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		doRedir2Queryon(req, resp);
	}*/
	
	/*
	public void doRedir2Queryon(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		String redirUrl = null;
		
		if(req.getPathInfo()==null || req.getPathInfo().isEmpty() || req.getPathInfo().equals("/")) {
			redirUrl = "/relation.odata";
		}
		else {
			redirUrl = (req.getPathInfo()!=null?req.getPathInfo():"");
		}
		
		if(req.getQueryString()!=null) {
			redirUrl += "?"+req.getQueryString();
		}
		
		redirUrl = baseQonUrl + redirUrl;
		
		log.info("request: path="+req.getPathInfo()+" ; query="+req.getQueryString()+" ; redir="+redirUrl);
		RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(redirUrl);
		dispatcher.forward(req, resp);
	}
	*/
	
	/*@Override
	protected void doInitProperties(ServletConfig config) {
	}*/
	
	@Override
	protected void doInit(ServletContext context) throws ServletException {
		super.doInit(context);
		//prop.putAll((Properties) context.getAttribute(ATTR_PROP));
		//dsutils = (DumpSyntaxUtils) context.getAttribute(ATTR_DUMP_SYNTAX_UTILS);
		//servletContext = context;
		
		//log.info("context: "+servletContext.getContextPath()+" ; servletUrlContext: "+servletUrlContext);
		//initFromProperties();
	}
	
	@Override
	protected boolean isStatusObject(String name) {
		return "".equals(name);
	}
	
	@Override
	protected void doService(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if(req.getPathInfo()==null || req.getPathInfo().isEmpty()
				//|| req.getPathInfo().equals("/")
				) {
			//String redirUrl = "/relation";
			//String redirUrl = "/object";
			String redirPath = "/";
			if(req.getQueryString()!=null) {
				redirPath += "?"+req.getQueryString();
			}
			String redirUrl = MiscUtils.removeMultiSlash("/" + req.getServletPath() + redirPath);
			
			log.info("forward: path="+req.getPathInfo()+" ; query="+req.getQueryString()+" ; redir="+redirUrl);
			RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(redirUrl);
			dispatcher.forward(req, resp);
			return;
		}
		
		String path = req.getPathInfo().substring(1); //removes 1st "/"
		
		if("$metadata".equals(path)) {
			// http://services.odata.org/V4/(S(frp3ql3rlcjpvorgy0422buq))/TripPinServiceRW/$metadata#Photos
			// EnumType, ComplexType, *EntityType, *Function, ?Action, EntityContainer(EntitySet, FunctionImport, ActionImport, Annotation), Annotations
			try {
				DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
				DOMImplementation domImpl = docBuilder.getDOMImplementation();
				
				Document doc = makeMetadata(domImpl, req);
				resp.setContentType(ResponseSpec.MIME_TYPE_XML);
				serialize(domImpl, doc, resp.getWriter());
				
				return;
			}
			catch(Exception e) {
				log.warn("Error making $metadata", e);
				throw new InternalServerException("Error making $metadata", e);
			}
		}

		log.info(">> path: '"+path+"' ; method: "+req.getMethod());
		
		// ? header: Content-Type: application/json;odata.metadata=minimal;odata.streaming=true;IEEE754Compatible=false;charset=utf-8
		// x header: OData-Version: 4.0
		resp.setHeader("OData-Version", ODATA_VERSION);

		for(String sq: singletonQueries) {
			if(sq.equals(path)) {
				log.debug("singletonQuery: "+sq);
				RequestSpec reqspec = getRequestSpec(req);
				Object bean = getBeanValue(sq, reqspec, req);
				//String context = getBaseHref(reqspec)+"$metadata#"+sq;
				String context = ODataJsonSyntax.getContext(getBaseHref(req), sq);
				DumpSyntaxInt ds = reqspec.getOutputSyntax();
				
				dumpBean(bean, context, ds.getMimeType(), (String) reqspec.getAttribute(RequestSpec.ATTR_CONTENTLOCATION), resp);
				return;
			}
		}
		
		super.doService(req, resp);
	}
	
	@Override
	protected RequestSpec getRequestSpec(HttpServletRequest req) throws ServletException, IOException {
		//return new ODataRequest(dsutils, req, prop, 0, ODataJsonSyntax.ODATA_ID, false, 0, "relation");
		return new ODataRequest(dsutils, req, prop);
	}
	
	static final List<String> statusUniqueColumns = Arrays.asList(new String[]{"name"});
	static final List<String> statusXtraColumns = Arrays.asList(new String[]{"kind", "url"});
	//XXX kind: EntitySet, Singleton, FunctionImport
	
	/*
	 * service document?
	 * http://docs.oasis-open.org/odata/odata-json-format/v4.0/errata03/os/odata-json-format-v4.0-errata03-os-complete.html#_Toc453766639
	 * http://docs.oasis-open.org/odata/odata/v4.0/errata03/os/complete/part1-protocol/odata-v4.0-errata03-os-part1-protocol-complete.html#_Service_Document_Request
	 */
	@SuppressWarnings("resource")
	@Override
	protected void doStatus(SchemaModel model, String statusTypeStr, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws IntrospectionException, SQLException, IOException, ServletException, ClassNotFoundException, NamingException {
		ResultSet rs = null;
		List<FK> importedFKs = null;
		List<Constraint> uks = null;
		
		//final DBObjectType statusType = DBObjectType.valueOf(statusTypeStr);
		final String objectName = ""; // statusTypeStr
		PrivilegeType privilege = PrivilegeType.SELECT;

		List<Entity> list = new ArrayList<Entity>();
		//XXX: sort objects?
		list.addAll(getEntities(model.getViews(), "EntitySet"));
		list.addAll(getEntities(model.getTables(), "EntitySet"));
		list.addAll(getEntitiesFromStringList(Arrays.asList(singletonQueries), "Singleton"));
		//list.addAll(getEntities(model.getExecutables(), "ActionImport")); //looks like ActionImport should not appear in the "Service Document"
		rs = new ResultSetListAdapter<Entity>(objectName, statusUniqueColumns, statusXtraColumns, list, Entity.class);

		//List<ExecutableObject> list = new ArrayList<ExecutableObject>(); list.addAll(model.getExecutables());
		//rs = new ResultSetListAdapter<ExecutableObject>(objectName, statusUniqueColumns, list, ExecutableObject.class);
		
		//List<FK> list = new ArrayList<FK>(); list.addAll(model.getForeignKeys());
		//rs = new ResultSetListAdapter<FK>(objectName, statusUniqueColumns, list, FK.class);
		
		rs = filterStatus(rs, reqspec, currentUser, privilege);
		
		dumpResultSet(rs, reqspec, null, objectName, statusUniqueColumns, importedFKs, uks, true, resp);
		if(rs!=null) { rs.close(); }
	}
	
	List<Entity> getEntities(Set<? extends NamedDBObject> list, String kind) {
		List<Entity> ret = new ArrayList<Entity>();
		for(NamedDBObject o: list) {
			ret.add(new Entity(o.getSchemaName(), o.getName(), kind));
		}
		return ret;
	}

	List<Entity> getEntitiesFromStringList(List<String> list, String kind) {
		List<Entity> ret = new ArrayList<Entity>();
		for(String o: list) {
			ret.add(new Entity(null, o, kind));
		}
		return ret;
	}
	
	/*@Override
	protected void preprocessParameters(RequestSpec reqspec, Relation relation, Constraint pk) {
		// set params based on keyValues & relation.getParameters?
		Map<String, String> keymap = reqspec.keyValues;
		//log.debug("req: "+reqspec+" keymap: "+keymap+" reqspec.getParams(): "+reqspec.getParams()+" pk: "+pk);
		if(keymap == null || keymap.size()==0 || keymap.size()==1 ||
				pk==null || pk.getUniqueColumns()==null) { return; }
		
		// ordering params by pk key cols order
		reqspec.getParams().clear();
		for(String col: pk.getUniqueColumns()) {
			String v = keymap.get(col);
			//log.debug("c: "+col+" ; v: "+v);
			reqspec.getParams().add(v);
		}
	}*/
	
	/*@Override
	protected DBObjectType statusObject(String name) {
		if(name.isEmpty()) {
			return DBObjectType.RELATION;
		}
		return super.statusObject(name);
	}*/
	
	/*
	 * see: https://stackoverflow.com/a/528512/616413
	 */
	Document makeMetadata(DOMImplementation domImpl, HttpServletRequest req) throws ParserConfigurationException, IntrospectionException {
		Document doc = domImpl.createDocument("http://docs.oasis-open.org/odata/ns/edmx", "edmx:Edmx", null);
		doc.getDocumentElement().setAttribute("Version", "4.0");

		//Document doc = docBuilder.newDocument();
		//Element rootElement = doc.createElement("Edmx");
		
		Element dataServices = doc.createElement("edmx:DataServices");
		Element schema = doc.createElementNS("http://docs.oasis-open.org/odata/ns/edm", "Schema");
		schema.setAttribute("Namespace", odataNS);
		dataServices.appendChild(schema);
		
		String modelId = SchemaModelUtils.getModelId(req);
		SchemaModel model = SchemaModelUtils.getModel(req.getServletContext(), modelId);
		//Set<String> schemaNames = new LinkedHashSet<String>();
		//Set<String> relationNames = new LinkedHashSet<String>();

		Element entityContainer = doc.createElement("EntityContainer");
		entityContainer.setAttribute("Name", "Container");
		
		Set<Table> ts = model.getTables();
		for(Table t: ts) {
			Element entity = createEntityType(doc, t);
			schema.appendChild(entity);
			Element entitySet = createEntitySet(doc, t);
			entityContainer.appendChild(entitySet);
			//schemaNames.add(t.getSchemaName());
			//relationNames.add(t.getQualifiedName());
		}
		Set<View> vs = model.getViews();
		for(View v: vs) {
			Element entity = createEntityType(doc, v);
			schema.appendChild(entity);
			Element entitySet = createEntitySet(doc, v);
			entityContainer.appendChild(entitySet);
			//schemaNames.add(v.getSchemaName());
			//relationNames.add(v.getQualifiedName());
		}
		//beans/singletons
		for(int i=0;i<singletonQueries.length;i++) {
			Class<?> beanClazz = singletonQueryBeans[i];
			Element entity = createEntityType(doc, beanClazz);
			schema.appendChild(entity);
			Element singleton = createSingleton(doc, singletonQueries[i], beanClazz.getSimpleName());
			entityContainer.appendChild(singleton);
		}

		//XXXdone add actions
		Set<ExecutableObject> eos = model.getExecutables();
		for(ExecutableObject eo: eos) {
			Element action = createAction(doc, eo);
			schema.appendChild(action);
			Element actionImport = createActionImport(doc, eo);
			entityContainer.appendChild(actionImport);
		}
		
		schema.appendChild(entityContainer);
		/*for(String s: schemaNames) {
			Element entityContainer = doc.createElement("EntityContainer");
		}*/
		
		doc.getDocumentElement().appendChild(dataServices);
		return doc;
	}
	
	Element createEntityType(Document doc, Relation r) {
		Element entity = doc.createElement("EntityType");
		entity.setAttribute("Name", r.getQualifiedName());
		Constraint pk = SchemaModelUtils.getPK(r);
		if(pk!=null) {
			Element key = doc.createElement("Key");
			for(String col: pk.getUniqueColumns()) {
				Element propRef = doc.createElement("PropertyRef");
				propRef.setAttribute("Name", col);
				key.appendChild(propRef);
			}
			entity.appendChild(key);
		}
		for(int i=0;i<r.getColumnCount();i++) {
			Element prop = doc.createElement("Property");
			prop.setAttribute("Name", r.getColumnNames().get(i));
			prop.setAttribute("Type", getPropertyType(r.getColumnTypes().get(i)) );
			entity.appendChild(prop);
		}
		return entity;
	}

	Element createEntityType(Document doc, Class<?> clazz) throws IntrospectionException {
		Element entity = doc.createElement("EntityType");
		entity.setAttribute("Name", clazz.getSimpleName());
		
		BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
		PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
		
		for(int i=0;i<pds.length;i++) {
			String cName = pds[i].getName();
			if("class".equals(cName)) { continue; }
			
			Element prop = doc.createElement("Property");
			prop.setAttribute("Name", cName);
			prop.setAttribute("Type", getPropertyType(pds[i].getReadMethod().getReturnType()) );
			entity.appendChild(prop);
		}

		return entity;
	}
	
	Element createEntitySet(Document doc, Relation r) {
		return createEntitySet(doc, r.getQualifiedName());
	}
	
	Element createEntitySet(Document doc, String qualifiedName) {
		Element entitySet = doc.createElement("EntitySet");
		entitySet.setAttribute("Name", qualifiedName);
		entitySet.setAttribute("EntityType", odataNS+"."+qualifiedName);
		return entitySet;
	}

	Element createSingleton(Document doc, String name, String type) {
		Element singleton = doc.createElement("Singleton");
		singleton.setAttribute("Name", name);
		singleton.setAttribute("Type", odataNS+"."+type);
		return singleton;
	}
	
	Element createAction(Document doc, ExecutableObject eo) {
		Element action = doc.createElement("Action");
		action.setAttribute("Name", eo.getQualifiedName());
		action.setAttribute("IsBound", "false");
		for(int i=0;i<eo.getParams().size();i++) {
			ExecutableParameter ep = eo.getParams().get(i);
			
			Element parameter = doc.createElement("Parameter");
			// test if IN parameter
			if(ep.getName()!=null) {
				parameter.setAttribute("Name", ep.getName());
			}
			// or: "p"+(i+1)
			parameter.setAttribute("Type", getPropertyType(ep.getDataType()) );
			action.appendChild(parameter);
		}
		return action;
	}

	Element createActionImport(Document doc, ExecutableObject eo) {
		Element actionImport = doc.createElement("ActionImport");
		actionImport.setAttribute("Name", eo.getQualifiedName());
		actionImport.setAttribute("Action", odataNS+"."+eo.getQualifiedName());
		return actionImport;
	}
	
	/*
	 * see: http://docs.oasis-open.org/odata/odata/v4.0/errata03/os/complete/part3-csdl/odata-v4.0-errata03-os-part3-csdl-complete.html#_The_edm:Documentation_Element
	 */
	String getPropertyType(String ctype) {
		if(ctype==null) { return "Edm.String"; }
		String upper = ctype.toUpperCase();
		
		boolean isInt = DBUtil.INT_COL_TYPES_LIST.contains(upper);
		if(isInt) {
			return "Edm.Int32";
		}
		boolean isFloat = DBUtil.FLOAT_COL_TYPES_LIST.contains(upper);
		if(isFloat) {
			return "Edm.Double";
		}
		boolean isDate = DBUtil.DATE_COL_TYPES_LIST.contains(upper);
		if(isDate) {
			return "Edm.Date"; //XXX: Edm.DateTimeOffset? "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" format... DateTimeFormatter.ISO_OFFSET_DATE_TIME;
		}
		boolean isBoolean = DBUtil.BOOLEAN_COL_TYPES_LIST.contains(upper);
		if(isBoolean) {
			return "Edm.Boolean";
		}
		boolean isBlob = DBUtil.BLOB_COL_TYPES_LIST.contains(upper);
		if(isBlob) {
			return "Edm.Binary";
		}
		
		return "Edm.String";
	}
	
	String getPropertyType(Class<?> clazz) {
		if(Boolean.TYPE.equals(clazz)) {
			return "Edm.Boolean";
		}
		if(String.class.equals(clazz)) {
			return "Edm.String";
		}
		
		log.warn("getPropertyType: unknown class: "+clazz);
		return "Edm.String";
	}
	
	Object getBeanValue(String beanQuery, RequestSpec reqspec, HttpServletRequest request) {
		AuthActions beanActions = new AuthActions(prop);
		if(beanQuery.equals(ODataServlet.QUERY_CURRENTUSER)) {
			return beanActions.getCurrentUser(request);
		}
		
		throw new InternalServerException("unknown bean query: "+beanQuery);
	}
	
	void dumpBean(Object bean, String context, String mimeType, String contentLocation, HttpServletResponse resp) throws IOException {
		// see: https://github.com/google/gson/issues/678
		Gson gson = new Gson();
		JsonObject ret = new JsonObject();
		ret.addProperty(ODataJsonSyntax.HEADER_ODATA_CONTEXT, context);
		JsonObject el = gson.toJsonTree(bean).getAsJsonObject();
		
		for (Map.Entry<String, JsonElement> entry : el.entrySet()) {
			ret.add(entry.getKey(), entry.getValue());
		}
		
		resp.setContentType(mimeType);
		if(contentLocation!=null) {
			resp.addHeader(ResponseSpec.HEADER_CONTENT_LOCATION, contentLocation);
		}
		
		//JsonPrimitive elContext = new JsonPrimitive(context);
		//el.getAsJsonObject().addProperty(ODataJsonSyntax.HEADER_ODATA_CONTEXT, context);
		resp.getWriter().write(ret.toString());
	}

	/*
	 * see: http://www.chipkillmar.net/2009/03/25/pretty-print-xml-from-a-dom/
	 */
	public static void serialize(DOMImplementation domImpl, Node node, Writer w) {
		DOMImplementationLS ls = (DOMImplementationLS) domImpl;
		LSSerializer lss = ls.createLSSerializer();
		DOMConfiguration domConfig = lss.getDomConfig();
		domConfig.setParameter("format-pretty-print", Boolean.TRUE);
		LSOutput lso = ls.createLSOutput();
		lso.setCharacterStream(w);
		lss.write(node, lso);
	}
	
	@Override
	protected void handleException(HttpServletRequest req, HttpServletResponse resp, BadRequestException e) throws IOException {
		String message = e.getMessage();
		if(message==null) { message = e.toString(); }
		ErrorResponse error = new ErrorResponse(e.getCode(), message);
	
		writeErrorResponse(error, e.getCode(), resp);
	}
	
	void writeErrorResponse(ErrorResponse error, int code, HttpServletResponse resp) throws IOException {
		Gson gson = new Gson();
		Map<String,Object> errMap = new HashMap<String, Object>();
		errMap.put("error", error);
		String json = gson.toJson(errMap);
		
		resp.reset();
		resp.setStatus(code);
		resp.setContentType(ResponseSpec.MIME_TYPE_JSON);
		resp.getWriter().write(json);
	}

	@Override
	public String getDefaultUrlMapping() {
		return "/odata/*";
	}

}
