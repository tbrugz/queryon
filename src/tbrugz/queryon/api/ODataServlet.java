package tbrugz.queryon.api;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.naming.NamingException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
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
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import tbrugz.queryon.QueryOn;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.ResponseSpec;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.syntaxes.ODataJsonSyntax;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.FK;
import tbrugz.sqldump.dbmodel.NamedDBObject;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.resultset.ResultSetListAdapter;

public class ODataServlet extends QueryOn {
	
	public static class Entity {
		//String schema;
		String name;
		String kind;
		String url;
		
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

	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(ODataServlet.class);
	
	static final String DEFAULT_ODATA_CONTEXT = "odata";
	static final String ODATA_VERSION = "4.0";
	
	static final String odataNS = "OData.QueryOn";
	
	//static String baseQonUrl = "/q";
	//static String baseODataUrl = "/odata";
	
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
	
	@Override
	protected void doInitProperties(ServletConfig config) {
	}
	
	@Override
	protected void doInit(ServletContext context) throws ServletException {
		prop.putAll((Properties) context.getAttribute(ATTR_PROP));
		dsutils = (DumpSyntaxUtils) context.getAttribute(ATTR_DUMP_SYNTAX_UTILS);
		servletContext = context;
		servletUrlContext = DEFAULT_ODATA_CONTEXT;
		//log.info("context: "+servletContext.getContextPath()+" ; servletUrlContext: "+servletUrlContext);
		
		initFromProperties();
	}
	
	@Override
	protected void doService(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if(req.getPathInfo()==null || req.getPathInfo().isEmpty()
				//|| req.getPathInfo().equals("/")
				) {
			//String redirUrl = "/relation";
			//String redirUrl = "/object";
			String redirUrl = "/";
			if(req.getQueryString()!=null) {
				redirUrl += "?"+req.getQueryString();
			}
			//redirUrl = baseODataUrl + redirUrl;
			redirUrl = "/" + servletUrlContext + redirUrl;
			
			log.info("forward: path="+req.getPathInfo()+" ; query="+req.getQueryString()+" ; redir="+redirUrl);
			RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(redirUrl);
			dispatcher.forward(req, resp);
			return;
		}
		
		if("/$metadata".equals(req.getPathInfo())) {
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

		log.info(">> pathInfo: "+req.getPathInfo()+" ; method: "+req.getMethod());
		
		// ? header: Content-Type: application/json;odata.metadata=minimal;odata.streaming=true;IEEE754Compatible=false;charset=utf-8
		// x header: OData-Version: 4.0
		resp.setHeader("OData-Version", ODATA_VERSION);
		
		super.doService(req, resp);
	}
	
	@Override
	protected RequestSpec getRequestSpec(HttpServletRequest req) throws ServletException, IOException {
		return new ODataRequest(dsutils, req, prop, 0, ODataJsonSyntax.ODATA_ID, false, 0, "relation");
	}
	
	static final List<String> statusUniqueColumns = Arrays.asList(new String[]{"name"});
	static final List<String> statusXtraColumns = Arrays.asList(new String[]{"kind", "url"});
	//XXX kind: EntitySet, Singleton, FunctionImport
	
	@SuppressWarnings("resource")
	@Override
	protected void doStatus(SchemaModel model, DBObjectType statusType, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws IntrospectionException, SQLException, IOException, ServletException, ClassNotFoundException, NamingException {
		ResultSet rs = null;
		List<FK> importedFKs = null;
		List<Constraint> uks = null;
		
		final String objectName = statusType.desc();
		PrivilegeType privilege = PrivilegeType.SELECT;

		List<Entity> list = new ArrayList<Entity>();
		//XXX: sort objects?
		list.addAll(getEntities(model.getViews(), "EntitySet"));
		list.addAll(getEntities(model.getTables(), "EntitySet"));
		list.addAll(getEntities(model.getExecutables(), "FunctionImport"));
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
	
	@Override
	protected void preprocessParameters(RequestSpec reqspec, Constraint pk) {
		if(! (reqspec instanceof ODataRequest)) { return; }
		ODataRequest req = (ODataRequest) reqspec;
		Map<String, String> keymap = req.keyValues;
		//log.debug("req: "+req+" keymap: "+keymap);
		if(keymap == null || keymap.size()==1) { return; }
		
		//List<Object> origPar = new ArrayList<Object>();
		//origPar.addAll(req.getParams());
		
		req.getParams().clear();
		for(String col: pk.getUniqueColumns()) {
			String v = keymap.get(col);
			//log.debug("c: "+col+" ; v: "+v);
			req.getParams().add(v);
		}
	}
	
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
	Document makeMetadata(DOMImplementation domImpl, HttpServletRequest req) throws ParserConfigurationException {
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
			//schemaNames.add(v.getSchemaName());
			//relationNames.add(v.getQualifiedName());
		}
		//XXX add actions, functions
		
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
	
	Element createEntitySet(Document doc, Relation r) {
		Element entitySet = doc.createElement("EntitySet");
		entitySet.setAttribute("Name", r.getQualifiedName());
		entitySet.setAttribute("EntityType", odataNS+"."+r.getQualifiedName());
		return entitySet;
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
			return "Edm.Date";
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

	/*
	 * see: http://www.chipkillmar.net/2009/03/25/pretty-print-xml-from-a-dom/
	 */
	void serialize(DOMImplementation domImpl, Document document, Writer w) {
		DOMImplementationLS ls = (DOMImplementationLS) domImpl;
		LSSerializer lss = ls.createLSSerializer();
		DOMConfiguration domConfig = lss.getDomConfig();
		domConfig.setParameter("format-pretty-print", Boolean.TRUE);
		LSOutput lso = ls.createLSOutput();
		lso.setCharacterStream(w);
		lss.write(document, lso);
	}

}
