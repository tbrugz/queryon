package tbrugz.queryon;

//import java.nio.charset.Charset;
import java.beans.IntrospectionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.exception.ForbiddenException;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.exception.NotFoundException;
import tbrugz.queryon.resultset.ResultSetFilterDecorator;
import tbrugz.queryon.resultset.ResultSetGrantsFilterDecorator;
import tbrugz.queryon.resultset.ResultSetLimitOffsetDecorator;
import tbrugz.queryon.resultset.ResultSetMetadata2RsAdapter;
import tbrugz.queryon.resultset.ResultSetPermissionFilterDecorator;
import tbrugz.queryon.sqlcmd.ShowColumns;
import tbrugz.queryon.sqlcmd.ShowExportedKeys;
import tbrugz.queryon.sqlcmd.ShowImportedKeys;
import tbrugz.queryon.sqlcmd.ShowMetadata;
import tbrugz.queryon.sqlcmd.ShowSchemas;
import tbrugz.queryon.sqlcmd.ShowTables;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.queryon.util.QOnModelUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.queryon.util.ShiroUtils;
import tbrugz.sqldump.resultset.ResultSetListAdapter;
import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.datadump.DumpSyntaxInt;
import tbrugz.sqldump.datadump.DumpSyntaxRegistry;
import tbrugz.sqldump.datadump.RDFAbstractSyntax;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.ExecutableParameter;
import tbrugz.sqldump.dbmodel.FK;
import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.ModelUtils;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.def.SchemaModelGrabber;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.IOUtil;
import tbrugz.sqldump.util.ParametrizedProperties;
import tbrugz.sqldump.util.StringUtils;
import tbrugz.sqldump.util.Utils;

/**
 * @see Web API Design - http://info.apigee.com/Portals/62317/docs/web%20api.pdf
 */
/*
 * TODO r2rml: option to understand URLs like: Department/name=accounting;city=Cambridge
 */
@MultipartConfig
public class QueryOn extends HttpServlet {
	private static final long serialVersionUID = 1L;

	public enum ActionType {
		SELECT,  //done
		INSERT,
		//UPSERT? which http-method is suitable? POST?
		UPDATE,
		DELETE,
		EXECUTE, //~TODOne: execute action!
		//QUERY,   //TODOne: SQLQueries action!
		STATUS,   //~TODOne: or CONFIG? show model, user, vars...
		//XXXxx: FINDBYKEY action? return only the first result
		SELECT_ANY,
		VALIDATE_ANY,
		EXPLAIN_ANY,
		MANAGE,
		// not actions but special (global) permissions:
		INSERT_ANY,
		UPDATE_ANY,
		DELETE_ANY
	}
	
	// 'status objects' (SO)
	public static final DBObjectType[] STATUS_OBJECTS = {
		DBObjectType.TABLE, DBObjectType.VIEW, DBObjectType.RELATION, DBObjectType.EXECUTABLE, DBObjectType.FK
	};

	public static final String[] DEFAULT_CLASSLOADING_PACKAGES = { "tbrugz.queryon", "tbrugz.queryon.processor", "tbrugz.sqldump", "tbrugz.sqldump.datadump", "tbrugz.sqldump.processors", "tbrugz", "" };
	
	public static final String ACTION_QUERY_ANY = "QueryAny";
	public static final String ACTION_VALIDATE_ANY = "ValidateAny";
	public static final String ACTION_EXPLAIN_ANY = "ExplainAny";
	
	public static final String CONST_QUERY = "QUERY";
	public static final String CONST_RELATION = "RELATION";
	
	public static final String MIME_TEXT = "text/plain";
	
	/*public enum StatusObject {
		TABLE,
		VIEW,
		EXECUTABLE,
		FK
	}*/

	public enum LimitOffsetStrategy {
		RESULTSET_CONTROL,
		SQL_LIMIT_OFFSET,
		SQL_ROWNUM,
		SQL_FETCH_FIRST //ANSI:2008? offset?
		;
		// XXX TOP ? - sqlserver... http://www.tutorialspoint.com/sql/sql-top-clause.htm / http://stackoverflow.com/questions/11226153/oracle-equivalent-rownum-for-sql-server-2005
		
		static final String PROPFILE_DBMS_SPECIFIC = "/dbms-specific-queryon.properties";
		static final ParametrizedProperties prop = new ParametrizedProperties();
		static {
			try {
				prop.load(LimitOffsetStrategy.class.getResourceAsStream(PROPFILE_DBMS_SPECIFIC));
			} catch (IOException e) {
				e.printStackTrace();
				throw new ExceptionInInitializerError(e);
			}
		}
		
		public static LimitOffsetStrategy getDefaultStrategy(String dbid) {
			String strategyStr = prop.getProperty("dbid."+dbid+".limitoffsetstretegy", RESULTSET_CONTROL.toString());
			log.debug("getLOStrategy["+dbid+"]: "+strategyStr);
			LimitOffsetStrategy strat = LimitOffsetStrategy.valueOf(strategyStr);
			return strat;
		}
		
		/*public boolean mustChangeQuery() {
			switch (this) {
			case RESULTSET_CONTROL:
				return false;
			default:
				return true;
			}
		}*/
	}
	
	private static final Log log = LogFactory.getLog(QueryOn.class);
	private static final Log logFilter = LogFactory.getLog(QueryOn.class.getName()+".filter");
	
	static final String INITP_PROPERTIES_PATH = "properties-resource";
	//static final String INITP_MODEL_ID = "model-id";
	static final String DEFAULT_PROPERTIES_RESOURCE = "/queryon.properties";
	static final String DEFAULT_PROPERTIES_VALUES_RESOURCE = "/queryon-defaults.properties";
	
	public static final String CONN_PROPS_PREFIX = "queryon";
	
	static final String PROP_MODELS = "queryon.models";
	static final String PROP_MODELS_DEFAULT = "queryon.models.default";
	
	public static final String PROP_DEFAULT_LIMIT = "queryon.limit.default";
	public static final String PROP_MAX_LIMIT = "queryon.limit.max";
	static final String PROP_BASE_URL = "queryon.baseurl";
	static final String PROP_CONTEXT_PATH = "queryon.context-path";
	static final String PROP_HEADERS_ADDCONTENTLOCATION = "queryon.headers.addcontentlocation";
	static final String PROP_XTRASYNTAXES = "queryon.xtrasyntaxes";
	static final String PROP_UPDATE_PLUGINS = "queryon.update-plugins";
	static final String PROP_PROCESSORS_ON_STARTUP = "queryon.processors-on-startup";
	static final String PROP_SQLDIALECT = "queryon.sqldialect"; //TODO: sqldialect per model...
	static final String PROP_VALIDATE_GETMETADATA = "queryon.validate.x-getmetadata";
	static final String PROP_VALIDATE_ORDERCOLNAME = "queryon.validate.x-ordercolumnname";
	static final String PROP_VALIDATE_FILTERCOLNAME = "queryon.validate.x-filtercolumnname";
	public static final String PROP_SCHEMAS_TO_IGNORE = "queryon.schemas-to-ignore";
	
	static final String DEFAULT_XTRA_SYNTAXES = "tbrugz.queryon.syntaxes.HTMLAttrSyntax";
	
	static final String PROP_X_REQUEST_UTF8 = "queryon.x-request-utf8";
	
	static final String SUFFIX_GRABCLASS = ".grabclass";
	//static final String SUFFIX_SQLDIALECT = ".sqldialect";
	static final String PROP_GRABCLASS = "queryon.grabclass";
	
	static final String REQ_ATTR_CONTENTLOCATION = "attr.contentlocation";

	static final String DEFAULT_OUTPUT_SYNTAX = "html";
	
	public static final String ATTR_PROP = "prop";
	public static final String ATTR_MODEL_MAP = "modelmap";
	public static final String ATTR_DEFAULT_MODEL = "defaultmodel";
	public static final String ATTR_SCHEMAS_MAP = "schemasmap";
	public static final String ATTR_INIT_ERROR = "initerror";
	public static final String ATTR_DUMP_SYNTAX_UTILS = "dsutils";
	
	public static final String METHOD_GET = "GET";
	public static final String METHOD_POST = "POST";
	public static final String METHOD_PUT = "PUT";
	public static final String METHOD_DELETE = "DELETE";
	
	final Properties prop = new ParametrizedProperties();
	DumpSyntaxUtils dsutils;
	//SchemaModel model;
	//final Map<String, SchemaModel> models = new HashMap<String, SchemaModel>();
	
	String propertiesResource = null;
	
	final List<UpdatePlugin> updatePlugins = new ArrayList<UpdatePlugin>();
	//String modelId;
	
	boolean doFilterStatusByPermission = true; //XXX: add prop for doFilterStatusByPermission ?
	boolean doFilterStatusByQueryGrants = true; //XXX: add prop for doFilterStatusByQueryGrants ?
	static boolean validateFilterColumnNames = true;
	boolean xSetRequestUtf8 = false;
	boolean validateUpdateColumnPermissions = false; //XXX: add prop for validateUpdateColumnPermissions
	Integer defaultLimit;
	int maxLimit;
	
	public static final String doNotCheckGrantsPermission = ActionType.SELECT_ANY.name();
	
	ServletContext servletContext = null;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		propertiesResource = config.getInitParameter(INITP_PROPERTIES_PATH);
		if(propertiesResource==null) { propertiesResource = DEFAULT_PROPERTIES_RESOURCE; }

		//modelId = config.getInitParameter(INITP_MODEL_ID);
		
		doInit(config.getServletContext());
	}
	
	void doInit(ServletContext context) throws ServletException {
		try {
			prop.clear();
			//XXX: protocol: add from ServletRequest?
			String protocol = "http://";
			//XXX: path: add host port (request - ServletRequest - object needed?)? servlet mapping url-pattern?
			String path = protocol + InetAddress.getLocalHost().getHostName().toLowerCase();
			String contextPath = getServletContext().getContextPath();
			String rdfBase = path +
					((!path.endsWith("/") && (!contextPath.startsWith("/"))?"/":"")) +
					contextPath;
			// scheme://domain:port/path?query_string#fragment_id - http://en.wikipedia.org/wiki/Uniform_resource_locator
			//String path = "http://"+InetAddress.getLocalHost().getHostName()+"/"+getServletContext().getContextPath();
			prop.setProperty(PROP_CONTEXT_PATH, contextPath);
			prop.setProperty(RDFAbstractSyntax.PROP_RDF_BASE, rdfBase);
			log.info(PROP_CONTEXT_PATH+": "+contextPath+" ; "+RDFAbstractSyntax.PROP_RDF_BASE+": "+rdfBase);
			
			prop.load(QueryOn.class.getResourceAsStream(DEFAULT_PROPERTIES_VALUES_RESOURCE));
			
			log.info("loading properties: "+propertiesResource);
			prop.load(QueryOn.class.getResourceAsStream(propertiesResource));

			DumpSyntaxRegistry.addSyntaxes(prop.getProperty(PROP_XTRASYNTAXES, DEFAULT_XTRA_SYNTAXES));
			log.info("syntaxes: "+StringUtils.getClassSimpleNameList(DumpSyntaxRegistry.getSyntaxes()) );
			
			Map<String, SchemaModel> models = new LinkedHashMap<String, SchemaModel>();
			List<String> modelIds = Utils.getStringListFromProp(prop, PROP_MODELS, ",");
			log.debug("modelIds="+modelIds);
			if(modelIds!=null) {
				for(String id: modelIds) {
					models.put(id, modelGrabber(prop, id));
				}
				String defaultModel = prop.getProperty(PROP_MODELS_DEFAULT);
				if(defaultModel==null && models.size()>0) {
					defaultModel = modelIds.get(0);
				}
				context.setAttribute(ATTR_DEFAULT_MODEL, defaultModel);
			}
			else {
				models.put(null, modelGrabber(prop, null));
				context.setAttribute(ATTR_DEFAULT_MODEL, null);
			}
			//log.debug("charset: "+Charset.defaultCharset());
			context.setAttribute(ATTR_MODEL_MAP, models);
			servletContext = context;
			//model = SchemaModelUtils.getDefaultModel(context);
			dsutils = new DumpSyntaxUtils(prop);
			
			//log.debug("quote:: "+DBMSResources.instance().getIdentifierQuoteString());
			validateFilterColumnNames = Utils.getPropBool(prop, PROP_VALIDATE_FILTERCOLNAME, validateFilterColumnNames);
			
			//SQL.sqlIdDecorator = new StringDecorator.StringQuoterDecorator(DBMSResources.instance().getIdentifierQuoteString());
			SQL.validateOrderColumnNames = Utils.getPropBool(prop, PROP_VALIDATE_ORDERCOLNAME, SQL.validateOrderColumnNames);
			
			xSetRequestUtf8 = Utils.getPropBool(prop, PROP_X_REQUEST_UTF8, xSetRequestUtf8);
			
			defaultLimit = Utils.getPropInt(prop, QueryOn.PROP_DEFAULT_LIMIT);
			maxLimit = Utils.getPropInt(prop, QueryOn.PROP_MAX_LIMIT, RequestSpec.DEFAULT_LIMIT);
			
			context.setAttribute(ATTR_PROP, prop);
			context.setAttribute(ATTR_DUMP_SYNTAX_UTILS, dsutils);
			
			Map<String, List<String>> schemasByModel = new HashMap<String, List<String>>();
			context.setAttribute(ATTR_SCHEMAS_MAP, schemasByModel);
			
			List<String> updatePluginsStrList = Utils.getStringListFromProp(prop, PROP_UPDATE_PLUGINS, ",");
			setupUpdatePlugins(context, updatePluginsStrList);
			
			runOnStartupProcessors(context);
			
			initModelsMetadata(models);
		} catch (Exception e) {
			String message = e.toString()+" [prop resource: "+propertiesResource+"]";
			log.error(message);
			log.debug(message, e);
			context.setAttribute(ATTR_INIT_ERROR, e);
			throw new ServletException(message, e);
		} catch (Error e) {
			log.error(e.toString());
			log.debug(e.toString(), e);
			context.setAttribute(ATTR_INIT_ERROR, e);
			throw e;
		}
	}
	
	void initModelsMetadata(Map<String, SchemaModel> models) throws ClassNotFoundException, SQLException, NamingException {
		for(Entry<String, SchemaModel> e: models.entrySet()) {
			if(! QOnModelUtils.isModelMetadataSet(e.getValue())) {
				Connection conn = null;
				try {
					conn = DBUtil.initDBConn(prop, e.getKey());
					QOnModelUtils.setModelMetadata(e.getValue(), e.getKey(), conn);
				}
				finally {
					ConnectionUtil.closeConnection(conn);
				}
			}
		}
	}
	
	void setupUpdatePlugins(ServletContext context, List<String> updatePluginList) {
		List<String> updatePluginsStr = new ArrayList<String>();
		updatePlugins.clear();
		if(updatePluginList==null) { return; }
		
		for(String upPluginStr: updatePluginList) {
			UpdatePlugin up = (UpdatePlugin) Utils.getClassInstance(upPluginStr, DEFAULT_CLASSLOADING_PACKAGES);
			up.setProperties(prop);
			updatePlugins.add(up);
			updatePluginsStr.add(up.getClass().getSimpleName());
		}
		
		log.info("update-plugins: "+updatePluginsStr);

		Map<String, SchemaModel> models = SchemaModelUtils.getModels(context);
		for(Map.Entry<String,SchemaModel> entry: models.entrySet()) {
			String modelId = entry.getKey();
			try {
				SchemaModel sm = entry.getValue();
				Connection conn = DBUtil.initDBConn(prop, modelId, sm);
				
				for(UpdatePlugin up: updatePlugins) {
					up.setConnection(conn);
					up.setSchemaModel(sm);
					up.onInit();
				}
				
				ConnectionUtil.closeConnection(conn);
			}
			catch(Exception e) {
				log.warn("Exception starting update-plugin [model="+modelId+"]: "+e, e);
			}
		}
	}
	
	void runOnStartupProcessors(ServletContext context) {
		//XXX option to reload properties & re-execute processors?
		//XXX run for every model?
		boolean run4EveryModel = true;
		List<String> procsOnStartup = Utils.getStringListFromProp(prop, PROP_PROCESSORS_ON_STARTUP, ",");
		if(procsOnStartup!=null) {
			for(String p: procsOnStartup) {
				if(run4EveryModel) {
					Map<String, SchemaModel> models = SchemaModelUtils.getModels(context);
					for(Map.Entry<String,SchemaModel> entry: models.entrySet()) {
						try {
							ProcessorServlet.doProcess(p, context, entry.getKey());
						}
						catch(Exception e) {
							log.warn("Exception executing processor on startup [proc="+p+"; model="+entry.getKey()+"]: "+e);
							log.debug("Exception executing processor on startup [proc="+p+"; model="+entry.getKey()+"]: "+e.getMessage(), e);
							//XXX: fail on error?
						}
					}
				}
				else {
					try {
						ProcessorServlet.doProcess(p, context, null);
					}
					catch(Exception e) {
						log.warn("Exception executing processor on startup [proc="+p+"]: "+e);
						log.debug("Exception executing processor on startup [proc="+p+"]: "+e.getMessage(), e);
						//XXX: fail on error?
					}
				}
			}
		}
	}
	
	//XXX: move to SchemaModelUtils?
	static SchemaModel modelGrabber(Properties prop, String modelId) throws ClassNotFoundException, SQLException, NamingException {
		final String prefix = DBUtil.getDBConnPrefix(prop, modelId);
		final String grabClassProp = prefix+SUFFIX_GRABCLASS;
		String grabClassName = prop.getProperty(grabClassProp, prop.getProperty(PROP_GRABCLASS));
		
		SchemaModelGrabber schemaGrabber = (SchemaModelGrabber) Utils.getClassInstance(grabClassName, DEFAULT_CLASSLOADING_PACKAGES);
		if(schemaGrabber==null) {
			String message = "schema grabber class '"+grabClassName+"' not found [prop '"
					+(!PROP_GRABCLASS.equals(grabClassProp)?grabClassProp+"' or '":"")
					+PROP_GRABCLASS+"']";
			log.warn(message);
			throw new RuntimeException(message);
		}
		
		DBMSResources.instance().setup(prop);
		schemaGrabber.setProperties(prop);
		
		Connection conn = null;
		if(schemaGrabber.needsConnection()) {
			conn = DBUtil.initDBConn(prop, modelId);
			//DBMSResources.instance().updateMetaData(conn.getMetaData());
			schemaGrabber.setConnection(conn);
		}
		SchemaModel sm = schemaGrabber.grabSchema();
		sm.setModelId(modelId);
		//String dialect = prop.getProperty(prefix+SUFFIX_SQLDIALECT);
		String dialect = prop.getProperty(PROP_SQLDIALECT);
		if(dialect!=null) {
			log.info("setting sql-dialect: "+dialect);
			sm.setSqlDialect(dialect);
		}
		/*else if(sm.getSqlDialect()==null && conn!=null) {
			dialect = DBMSResources.instance().detectDbId(conn.getMetaData(), false);
			sm.setSqlDialect(dialect);
		}*/
		//DBMSResources.instance().updateDbId(sm.getSqlDialect()); //XXX: should NOT be a singleton
		
		if(conn!=null) {
			QOnModelUtils.setModelMetadata(sm, modelId, conn);
			ConnectionUtil.closeConnection(conn);
		}
		return sm;
	}

	//TODO?: prevent sql injection
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			doService(req, resp);
		}
		catch(InternalServerException e) {
			e.printStackTrace();
			resp.setStatus(e.getCode());
			resp.setContentType(MIME_TEXT);
			resp.getWriter().write(e.getMessage());
		}
		catch(BadRequestException e) {
			//e.printStackTrace();
			resp.setStatus(e.getCode());
			resp.setContentType(MIME_TEXT);
			resp.getWriter().write(e.getMessage());
			//log.warn("BRE: "+e.getMessage()+
			//		(e.internalMessage!=null?" ; internal="+e.internalMessage:"")); 
		}
		catch(ServletException e) {
			//e.printStackTrace();
			throw e;
		}
	}
	
	void doService(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		log.info(">> pathInfo: "+req.getPathInfo()+" ; method: "+req.getMethod());
		
		try {

		if(xSetRequestUtf8) {
			try {
				String origCharset = req.getCharacterEncoding();
				log.debug("setting request encoding UTF-8 [was: "+origCharset+"]");
				req.setCharacterEncoding("UTF-8");
			} catch (UnsupportedEncodingException e) {
				log.warn("error setCharacterEncoding: "+e.getMessage(), e);
			}
		}
		
		RequestSpec reqspec = new RequestSpec(dsutils, req, prop);
		//XXX app-specific xtra parameters, like auth properties? app should extend QueryOn & implement addXtraParameters
		
		final String otype;
		final ActionType atype;
		DBIdentifiable dbobj = null;
		SchemaModel model = SchemaModelUtils.getModel(req.getServletContext(), reqspec.modelId);
		//StatusObject sobject = StatusObject.valueOf(reqspec.object)
		//XXX should status object names have special syntax? like meta:table, meta:fk
		
		DBObjectType statusType = statusObject(reqspec.object.toUpperCase());
		//if(statusType!=null && Arrays.asList(STATUS_OBJECTS).contains(statusType)) { //test if STATUS_OBJECTS contains statusType ?
		if(statusType!=null) {
			atype = ActionType.STATUS;
			otype = statusType.name();
		}
		else if(ACTION_QUERY_ANY.equals(reqspec.object) && METHOD_POST.equals(reqspec.httpMethod)) {
			atype = ActionType.SELECT_ANY;
			otype = ActionType.SELECT_ANY.name();
		}
		else if(ACTION_VALIDATE_ANY.equals(reqspec.object) && METHOD_POST.equals(reqspec.httpMethod)) {
			atype = ActionType.VALIDATE_ANY;
			otype = ActionType.VALIDATE_ANY.name();
		}
		else if(ACTION_EXPLAIN_ANY.equals(reqspec.object) && METHOD_POST.equals(reqspec.httpMethod)) {
			atype = ActionType.EXPLAIN_ANY;
			otype = ActionType.EXPLAIN_ANY.name();
		}
		else if(ActionType.MANAGE.name().equals(reqspec.object)) {
			atype = ActionType.MANAGE;
			otype = ActionType.MANAGE.name();
		}
		else {
			dbobj = SchemaModelUtils.getDBIdentifiableBySchemaAndName(model, reqspec);
			if(dbobj==null) {
				throw new NotFoundException("object not found: "+reqspec.object);
			}
			
			if(dbobj instanceof Relation) {
				if(reqspec.httpMethod.equals(METHOD_GET)) {
					atype = ActionType.SELECT;
				}
				else if(reqspec.httpMethod.equals(METHOD_POST)) {
					atype = ActionType.INSERT; //upsert?
				}
				else if(reqspec.httpMethod.equals(METHOD_PUT)) {
					//XXX: PUT should be idempotent ... maybe should be used for INSERT?
					atype = ActionType.UPDATE;
				}
				else if(reqspec.httpMethod.equals(METHOD_DELETE)) {
					atype = ActionType.DELETE;
				}
				else {
					throw new BadRequestException("unknown http method: "+reqspec.httpMethod+" [obj="+reqspec.object+"]");
				}
				
				otype = QueryOn.getObjectType(dbobj);
			}
			else if(dbobj instanceof ExecutableObject) {
				//XXX only if POST method?
				atype = ActionType.EXECUTE;
				otype = DBObjectType.EXECUTABLE.name();
			}
			else {
				throw new BadRequestException("unknown object type: "+dbobj.getClass().getName()+" [obj="+reqspec.object+"]");
			}
		}
		
			Subject currentUser = ShiroUtils.getSubject(prop);
			
			ShiroUtils.checkPermission(currentUser, otype+":"+atype, reqspec.object);
			switch (atype) {
			case SELECT: {
				Relation rel = (Relation) dbobj;
				if(rel==null) {
					log.warn("strange... rel is null");
					rel = SchemaModelUtils.getRelation(model, reqspec, true); //XXX: option to search views based on property?
				}
				if(! ShiroUtils.isPermitted(currentUser, doNotCheckGrantsPermission)) {
					checkGrantsAndRolesMatches(currentUser, PrivilegeType.SELECT, rel);
				}
				doSelect(model, rel, reqspec, currentUser, resp);
				}
				break;
			case SELECT_ANY:
				try {
					Query relation = getQuery(req);
					//XXXxx: validate first & return number of parameters?
					relation.setParameterCount( reqspec.params.size() ); //maybe not good... anyway
					resp.addHeader(ResponseSpec.HEADER_CONTENT_DISPOSITION, "attachment; filename=queryon_"
						+relation.getName() //XXX add parameter values? filters? -- ,maybe filters is too much
						+"."+reqspec.outputSyntax.getDefaultFileExtension());
					
					boolean sqlCommandExecuted = trySqlCommand(relation, reqspec, resp);
					if(!sqlCommandExecuted) {
						doSelect(model, relation, reqspec, currentUser, resp);
					}
				}
				catch(SQLException e) {
					throw new BadRequestException(e.getMessage(), e);
				}
				break;
			case VALIDATE_ANY:
				try {
					Query relation = getQuery(req);
					doValidate(relation, reqspec, currentUser, resp);
				}
				catch(SQLException e) {
					throw new BadRequestException(e.getMessage(), e);
				}
				break;
			case EXPLAIN_ANY:
				try {
					Query relation = getQuery(req);
					doExplain(relation, reqspec, currentUser, resp);
				}
				catch(SQLException e) {
					throw new BadRequestException(e.getMessage(), e);
				}
				break;
			case EXECUTE:
				ExecutableObject eo = (ExecutableObject) dbobj;
				if(eo==null) {
					throw new IllegalStateException("Executable must not be null [object="+reqspec.object+"]");
					//log.warn("strange... eo is null");
					//eo = SchemaModelUtils.getExecutable(model, reqspec);
				}
				checkGrantsAndRolesMatches(currentUser, PrivilegeType.EXECUTE, eo);
				doExecute(eo, reqspec, currentUser, resp);
				break;
			case INSERT: {
				doInsert((Relation) dbobj, reqspec, currentUser, resp);
				}
				break;
			case UPDATE: {
				doUpdate((Relation) dbobj, reqspec, currentUser, resp);
				}
				break;
			case DELETE: {
				doDelete((Relation) dbobj, reqspec, currentUser, resp);
				}
				break;
			case STATUS:
				doStatus(model, statusType, reqspec, currentUser, resp);
				break;
			case MANAGE:
				doManage(reqspec, req, resp);
				break;
			default:
				throw new BadRequestException("Unknown action type: "+atype); 
			}
		}
		catch(InternalServerException e) {
			log.warn(e.getClass().getSimpleName()+" ["+e.getCode()+"]: "+e.getMessage(), e);
			throw e;
		}
		catch(BadRequestException e) {
			//XXX: do not log exception!
			log.warn(e.getClass().getSimpleName()+" ["+e.getCode()+"]: "+e.getMessage());
			throw e;
		}
		catch(SQLException e) {
			throw new ServletException(e);
		}
		catch(IOException e) {
			throw new ServletException(e);
		}
		catch(ClassNotFoundException e) {
			throw new ServletException(e);
		}
		catch(NamingException e) {
			throw new ServletException(e);
		}
		catch(IntrospectionException e) {
			throw new ServletException(e);
		}
		catch(Throwable e) {
			throw new ServletException(e);
		}
	}
	
	public static void checkGrantsAndRolesMatches(Subject subject, PrivilegeType privilege, Relation rel) {
		boolean check = grantsAndRolesMatches(subject, privilege, rel.getGrants());
		if(!check) {
			String schema = rel.getSchemaName();
			throw new ForbiddenException("no "+privilege+" permission on "+(schema!=null?schema+".":"")+rel.getName());
		}
	}

	public static void checkGrantsAndRolesMatches(Subject subject, PrivilegeType privilege, ExecutableObject eo) {
		boolean check = grantsAndRolesMatches(subject, privilege, eo.getGrants());
		if(!check) {
			throw new ForbiddenException("no "+privilege+" permission on "+eo.getQualifiedName());
		}
	}
	
	public static boolean grantsAndRolesMatches(Subject subject, PrivilegeType privilege, List<Grant> grants) {
		grants = QOnModelUtils.filterGrantsByPrivilegeType(grants, privilege);
		if(grants==null || grants.size()==0) {
			return true;
		}
		Set<String> roles = ShiroUtils.getSubjectRoles(subject);
		//log.info("grantsAndRolesMatches:: grants: "+grants);
		//log.info("grantsAndRolesMatches:: roles: "+roles);
		for(Grant grant: grants) {
			if( privilege.equals(grant.getPrivilege()) && roles.contains(grant.getGrantee()) ) {
				return true;
			}
		}
		
		return false;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doGet(req, resp);
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doGet(req, resp);
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doGet(req, resp);
	}
	
	// XXX should use RequestSpec for parameters?
	static Query getQuery(HttpServletRequest req) {
		Query relation = new Query();
		String name = req.getParameter("name");
		/*if(name==null || name.equals("")) {
			throw new BadRequestException("parameter 'name' undefined");
		}*/
		String sql = req.getParameter("sql");
		if(sql==null || sql.equals("")) {
			throw new BadRequestException("parameter 'sql' undefined");
		}
		relation.setName(name);
		relation.setQuery(sql);
		return relation;
	}
	
	public static SQL getSelectQuery(SchemaModel model, Relation relation, RequestSpec reqspec, Constraint pk, LimitOffsetStrategy loStrategy,
			String username, Integer defaultLimit, int maxLimit, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		
		SQL sql = SQL.createSQL(relation, reqspec, username);
		
		// add parameters for Query
		addOriginalParameters(reqspec, sql);
		
		filterByKey(relation, reqspec, pk, sql);

		// TODO parameters: remove reqspec.params in excess of #parametersToBind ?

		// xtra filters
		List<String> warnings = filterByXtraParams(relation, reqspec, sql);
		if(warnings!=null && warnings.size()>0) {
			String warns = Utils.join(warnings, ", ");
			resp.addHeader(ResponseSpec.HEADER_WARNING_UNKNOWN_COLUMN, warns);
		}
		
		//XXX app-specific xtra filters, like auth filters? app should extend QueryOn & implement addXtraConstraints
		//appXtraConstraints(relation, sql, reqspec, req);
		
		//XXX: apply order or projection first? order last seems more natural...
		
		// projection (select columns) - also adds 'distinct' if requested
		sql.applyProjection(reqspec, relation);

		// order by
		sql.applyOrder(reqspec);

		//limit-offset
		//how to decide strategy? default is LimitOffsetStrategy.RESULTSET_CONTROL
		//query type (table, view, query), resultsetType? (not available at this point), database type
		if(! sql.allowEncapsulation) {
			loStrategy = LimitOffsetStrategy.RESULTSET_CONTROL;
			log.debug("query '"+relation.getName()+"': allowEncapsulation == false - loStrategy = "+loStrategy);
		}
		if(reqspec.loStrategy!=null) {
			//TODO: add permission check
			loStrategy = LimitOffsetStrategy.valueOf(reqspec.loStrategy);
		}
		if(loStrategy!=LimitOffsetStrategy.RESULTSET_CONTROL) {
			log.debug("pre-sql:\n"+sql.getSql());
		}
		//int limit = (sql.limitMax!=null && sql.limitMax < reqspec.limit) ? sql.limitMax : reqspec.limit;
		int finalMaxLimit = maxLimit;
		if(sql.limitMax!=null) {
			finalMaxLimit = Math.min(finalMaxLimit, sql.limitMax);
		}
		sql.addLimitOffset(loStrategy, getLimit(sql.limit, defaultLimit, finalMaxLimit), reqspec.offset);
		
		//query finished!
		
		return sql;
		//log.info("sql:\n"+sql);
	}
		
	void doSelect(SchemaModel model, Relation relation, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		if(relation.getName()==null) {
			throw new BadRequestException("select: relation name must not be null");
		}
		
		Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		String finalSql = null;
		try {
			
		if(log.isDebugEnabled()) {
			ConnectionUtil.showDBInfo(conn.getMetaData());
		}
		
		Constraint pk = SchemaModelUtils.getPK(relation);
		LimitOffsetStrategy loStrategy = LimitOffsetStrategy.getDefaultStrategy(model.getSqlDialect());
		
		SQL sql = getSelectQuery(model, relation, reqspec, pk, loStrategy, getUsername(currentUser), defaultLimit, maxLimit, resp);
		finalSql = sql.getFinalSql();
		PreparedStatement st = conn.prepareStatement(finalSql);
		bindParameters(st, sql);
		
		ResultSet rs = st.executeQuery();
		
		boolean applyLimitOffsetInResultSet = loStrategy==LimitOffsetStrategy.RESULTSET_CONTROL;

		List<FK> fks = ModelUtils.getImportedKeys(relation, model.getForeignKeys());
		List<Constraint> uks = ModelUtils.getUKs(relation);
		
		if(Utils.getPropBool(prop, PROP_HEADERS_ADDCONTENTLOCATION, false)) {
			String contentLocation = reqspec.getCanonicalUrl(prop);
			//log.info("content-location header: "+contentLocation);
			reqspec.request.setAttribute(REQ_ATTR_CONTENTLOCATION, contentLocation);
		}
		
		if(reqspec.uniValueCol!=null) {
			dumpBlob(rs, reqspec, relation.getName(), applyLimitOffsetInResultSet, resp);
		}
		else {
			dumpResultSet(rs, reqspec, relation.getSchemaName(), relation.getName(), pk!=null?pk.getUniqueColumns():null, fks, uks, applyLimitOffsetInResultSet, resp, sql.limit);
		}
		
		}
		catch(SQLException e) {
			DBUtil.doRollback(conn);
			log.warn("exception in 'doSelect': "+e+" ; sql:\n"+finalSql);
			//XXX: create new SQLException including the query string?
			throw e;
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}
	
	/*
	 * XXX: option to select different validate strategies (drivers may validate queries differently)
	 * - current impl
	 * - no stmt.getMetaData()
	 * - run query with limit of 0 or 1? set parameters with what? null? random?
	 */
	void doValidate(Relation relation, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		try {
			SQL sql = SQL.createSQL(relation, reqspec, getUsername(currentUser));
			PreparedStatement stmt = conn.prepareStatement(sql.getFinalSql());

			ParameterMetaData pmd = stmt.getParameterMetaData();
			int params = pmd.getParameterCount();
			List<String> paramsTypes = new ArrayList<String>();
			try {
				for(int i=1;i<=params;i++) {
					paramsTypes.add(pmd.getParameterTypeName(i));
				}
			}
			catch(SQLException e) {
				log.warn("exception: ParameterMetaData.getParameterTypeName: "+e.getMessage());
			}
			//log.info("doValidate: #params="+params+" ; types = "+paramsTypes);
			resp.setIntHeader(ResponseSpec.HEADER_VALIDATE_PARAMCOUNT, params);
			resp.setHeader(ResponseSpec.HEADER_VALIDATE_PARAMTYPES, paramsTypes.toString());
			boolean doGetMetadata = Utils.getPropBool(prop, PROP_VALIDATE_GETMETADATA, true);
			if(doGetMetadata) {
				//XXX: (also) return number of bind parameters? return as ResultSet? stmt.getParameterMetaData()...
				ResultSetMetaData rsmd = stmt.getMetaData(); // needed to *really* validate query (at least on oracle)
				if(rsmd==null) {
					String message = "can't get metadata of: <code>"+sql.getFinalSql().trim()+"</code>";
					throw new BadRequestException(message);
				}
				// dumping ResultSetMetaData as a ResultSet ;)
				ResultSet rs = new ResultSetMetadata2RsAdapter(rsmd);
				dumpResultSet(rs, reqspec, relation.getSchemaName(), relation.getName(),
						null, //pk!=null?pk.getUniqueColumns():null,
						null, null, //fks, uks,
						false, //applyLimitOffsetInResultSet,
						resp);
			}
			else {
				resp.setContentType(MIME_TEXT);
				resp.getWriter().write(String.valueOf(params));
			}
		}
		catch(SQLException e) {
			log.info("doValidate: error validating: "+e);
			//log.debug("doValidate: error validating: "+e.getMessage(), e);
			DBUtil.doRollback(conn);
			throw e;
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}

	void doExplain(Relation relation, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		final Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		try {
			final DBMSResources res = DBMSResources.instance();
			final DBMSFeatures feat = res.getSpecificFeatures(conn.getMetaData());
			
			if(!feat.supportsExplainPlan()) {
				throw new BadRequestException("Explain plan not available for database: "+feat.getClass().getSimpleName());
			}
			
			SQL sql = SQL.createSQL(relation, reqspec, getUsername(currentUser));
			ResultSet rs = feat.explainPlan(sql.getFinalSql(), conn);

			dumpResultSet(rs, reqspec, relation.getSchemaName(), relation.getName(),
					null, //pk!=null?pk.getUniqueColumns():null,
					null, null, //fks, uks,
					false, //applyLimitOffsetInResultSet,
					resp);
		}
		catch(SQLException e) {
			log.info("doExplain: error explaining: "+e);
			DBUtil.doRollback(conn);
			throw e;
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}
	
	/*
	 * http://docs.oracle.com/javase/6/docs/api/java/sql/CallableStatement.html
	 *  
	 * {?= call <procedure-name>[(<arg1>,<arg2>, ...)]}
	 * {call <procedure-name>[(<arg1>,<arg2>, ...)]}
	 * 
	 * The type of all OUT parameters must be registered prior to executing the stored procedure; their values are retrieved after execution via the get methods provided here.
	 * 
	 * XXX: add multipart(/form-data) return?
	 * http://stackoverflow.com/questions/4526273/what-does-enctype-multipart-form-data-mean
	 * http://stackoverflow.com/questions/4007969/application-x-www-form-urlencoded-or-multipart-form-data
	 */
	void doExecute(ExecutableObject eo, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		log.info("eo: "+eo+" ; currentUser: "+currentUser.getPrincipal() + " ; remote: "+reqspec.getRemoteInfo());
		Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		
		//XXXdone: test for Subject's permissions
		try {
		
		int outParamCount = 0;
		Object retObject = null;
		String sql = SQL.createExecuteSqlFromBody(eo, getUsername(currentUser) );
		if(eo.getType()==DBObjectType.EXECUTABLE && sql!=null && !sql.equals("")) {
		
			//log.info("executing BODY: "+sql);
			PreparedStatement stmt = conn.prepareStatement(sql);
			ParameterMetaData pmd = stmt.getParameterMetaData();
			int pc = pmd.getParameterCount();
			if(reqspec.params.size() < pc) {
				throw new BadRequestException("Number of request parameters ["+reqspec.params.size()+"] less than number of executable's parameters [pc="+pc+"]");
			}
			for(int i=0;i<pc;i++) {
				stmt.setObject(i+1, reqspec.params.get(i));
			}
			stmt.execute();

			int updatecount = stmt.getUpdateCount();
			//log.info("updateCount: "+updatecount);
			resp.addIntHeader(ResponseSpec.HEADER_UPDATECOUNT, updatecount);

			try {
				ResultSet generatedKeys = stmt.getGeneratedKeys();
				if(generatedKeys!=null && generatedKeys.next()) {
					List<String> colVals = getGeneratedKeys(generatedKeys);
					resp.setHeader(ResponseSpec.HEADER_RELATION_UK_VALUES, Utils.join(colVals, ", "));
				}
			}
			catch(SQLException e) {
				log.warn("getGeneratedKeys: "+e.getMessage());
			}
			
			retObject = stmt.getResultSet();
			/*if(result!=null) {
				dumpResultSet(result, reqspec, eo.getSchemaName(), eo.getName(), null, null, null, false, resp, 1000);
			}*/
			/*if(updatecount>0) {
				conn.commit();
			}*/
		}
		else {
		
		sql = SQL.createExecuteSQLstr(eo);
		CallableStatement stmt = conn.prepareCall(sql.toString());
		int paramOffset = 1 + (eo.getType()==DBObjectType.FUNCTION?1:0);
		int inParamCount = 0;
		if(reqspec.params.size() < eo.getParams().size()) {
			throw new BadRequestException("Number of request parameters ["+reqspec.params.size()+"] less than number of executable's parameters ["+eo.getParams().size()+"]");
		}
		for(int i=0;i<eo.getParams().size();i++) {
			ExecutableParameter ep = eo.getParams().get(i);
			if(ep.getInout()==null || ep.getInout()==ExecutableParameter.INOUT.IN || ep.getInout()==ExecutableParameter.INOUT.INOUT) {
				/*if(reqspec.params.size() <= i) {
					throw new BadRequestException("Number of request parameters ["+reqspec.params.size()+"] less than index of executable's parameter [i="+i+" ; inParamCount="+(inParamCount)+" ; size="+eo.getParams().size()+"]");
				}*/
				//XXX: oracle: when using IN OUT parameters, driver may require to use specific type (stmt.setDouble()) // stmt.setDouble(i+paramOffset, ...);
				stmt.setObject(i+paramOffset, reqspec.params.get(i));
				//log.info("["+i+"/"+(inParamCount+paramOffset)+"] setObject: "+reqspec.params.get(inParamCount));
				inParamCount++;
			}
			if(ep.getInout()==ExecutableParameter.INOUT.OUT || ep.getInout()==ExecutableParameter.INOUT.INOUT) {
				stmt.registerOutParameter(i+paramOffset, DBUtil.getSQLTypeForColumnType(ep.getDataType()));
				//log.info("["+i+"/"+(outParamCount+paramOffset)+"] registerOutParameter ; type="+DBUtil.getSQLTypeForColumnType(ep.getDataType()));
				outParamCount++;
			}
			//log.info("["+i+"] param: "+ep);
		}
		if(eo.getType()==DBObjectType.FUNCTION) { // is function !?! // eo.getReturnParam()!=null
			stmt.registerOutParameter(1, DBUtil.getSQLTypeForColumnType(eo.getReturnParam().getDataType()));
			//log.info("[return] registerOutParameter ; type="+DBUtil.getSQLTypeForColumnType(eo.getReturnParam().getDataType()));
			outParamCount++;
		}
		log.info("sql exec: "+sql+" [executable="+eo+" ; return="+eo.getReturnParam()+" ; inParamCount="+inParamCount+" ; outParamCount="+outParamCount+"]");
		stmt.execute();
		boolean gotReturn = false;
		if(eo.getType()==DBObjectType.FUNCTION) { // is function !?! // eo.getReturnParam()!=null
			retObject = stmt.getObject(1);
			gotReturn = true;
		}
		boolean warnedManyOutParams = false;
		for(int i=0;i<eo.getParams().size();i++) {
			ExecutableParameter ep = eo.getParams().get(i);
			if(ep.getInout()==ExecutableParameter.INOUT.OUT || ep.getInout()==ExecutableParameter.INOUT.INOUT) {
				if(gotReturn) {
					if(outParamCount>1 && !warnedManyOutParams) {
						log.warn("there are "+outParamCount+" out parameter. Only the first will be returned");
						resp.addHeader(ResponseSpec.HEADER_WARNING, "Execute-TooManyReturnParams ReturnCount="+outParamCount);
						warnedManyOutParams = true;
					}
					break; //got first result
					//log.info("ret["+i+";"+(i+paramOffset)+"]: "+stmt.getObject(i+paramOffset));
				}
				else {
					retObject = stmt.getObject(i+paramOffset);
					gotReturn = true;
				}
			}
		}
		resp.addHeader(ResponseSpec.HEADER_EXECUTE_RETURNCOUNT, String.valueOf(outParamCount));
		
		}
		
		if(retObject!=null) {
			if(retObject instanceof ResultSet) {
				dumpResultSet((ResultSet)retObject, reqspec, null, reqspec.object, null, null, null, true, resp);
			}
			else {
				resp.setContentType(MIME_TEXT);
				resp.getWriter().write(retObject.toString());
			}
		}
		else {
			resp.setContentType(MIME_TEXT);
			if(outParamCount==0) {
				resp.getWriter().write("execution successful - no return");
			}
			else {
				resp.getWriter().write("execution successful - null return");
			}
		}

		}
		catch(SQLException e) {
			DBUtil.doRollback(conn);
			throw new InternalServerException("Error executing procedure/fuction: "+e.getMessage(), e);
		}
		finally {
			conn.commit();
			ConnectionUtil.closeConnection(conn);
		}
	}

	static final List<String> statusUniqueColumns = Arrays.asList(new String[]{"schemaName", "name"});
	// XXX: add "columns"? add columnNulls/columnSizes?
	static final List<String> relationCommonCols =  Arrays.asList(new String[]{"relationType", "columnNames", "columnTypes", "columnRemarks","constraints", "remarks", "grants"});
	//XXX: qualifiedName needed? (no body & dumpable) 
	static final List<String> executableCols =  Arrays.asList(new String[]{"type", "packageName", "qualifiedName", "params", "returnParam","remarks", "grants"});

	static final List<String> tableAllColumns;// =     Arrays.asList(new String[]{"columnNames", "constraints", "remarks", "relationType", "grants", "PKConstraint"});
	static final List<String> viewAllColumns;//  =     Arrays.asList(new String[]{"columnNames", "constraints", "remarks", "relationType", "grants", "parameterCount"});
	static final List<String> relationAllColumns;//  = Arrays.asList(new String[]{"columnNames", "constraints", "remarks", "relationType", "grants", "parameterCount"});
	static final List<String> executableAllColumns;
	
	static {
		tableAllColumns = new ArrayList<String>(); tableAllColumns.addAll(relationCommonCols); tableAllColumns.addAll(Arrays.asList(new String[]{"PKConstraint"}));
		viewAllColumns = new ArrayList<String>(); viewAllColumns.addAll(relationCommonCols); viewAllColumns.addAll(Arrays.asList(new String[]{"parameterCount", "parameterTypes"}));
		relationAllColumns = new ArrayList<String>(); relationAllColumns.addAll(relationCommonCols); relationAllColumns.addAll(Arrays.asList(new String[]{"parameterCount", "parameterTypes"}));
		executableAllColumns = new ArrayList<String>(); executableAllColumns.addAll(executableCols);
	}
	
	@SuppressWarnings("resource")
	void doStatus(SchemaModel model, DBObjectType statusType, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws IntrospectionException, SQLException, IOException, ServletException, ClassNotFoundException, NamingException {
		ResultSet rs = null;
		List<FK> importedFKs = null;
		List<Constraint> uks = null;
		final String objectName = statusType.desc();
		PrivilegeType privilege = PrivilegeType.SELECT;
		//XXX: filter by schemaName, name? ResultSetFilterDecorator(rs, colpositions, colvalues)?
		//log.info("doStatus: "+statusType);
		switch (statusType) {
		case TABLE: {
			List<Table> list = new ArrayList<Table>(); list.addAll(model.getTables());
			rs = new ResultSetListAdapter<Table>(objectName, statusUniqueColumns, tableAllColumns, list, Table.class);
			//XXX importedFKs = ...
			break;
		}
		case VIEW: {
			List<View> list = new ArrayList<View>(); list.addAll(model.getViews());
			rs = new ResultSetListAdapter<View>(objectName, statusUniqueColumns, viewAllColumns, list, View.class);
			//XXX importedFKs = ...
			break;
		}
		case RELATION: {
			List<Relation> list = new ArrayList<Relation>(); list.addAll(model.getViews()); list.addAll(model.getTables()); //XXX: sort relations?
			rs = new ResultSetListAdapter<Relation>(objectName, statusUniqueColumns, relationAllColumns, list, Relation.class);
			//XXX importedFKs = ... ?
			break;
		}
		case EXECUTABLE: {
			List<ExecutableObject> list = new ArrayList<ExecutableObject>(); list.addAll(model.getExecutables());
			rs = new ResultSetListAdapter<ExecutableObject>(objectName, statusUniqueColumns, executableAllColumns, list, ExecutableObject.class);
			privilege = PrivilegeType.EXECUTE;
			break;
		}
		case FK: {
			List<FK> list = new ArrayList<FK>(); list.addAll(model.getForeignKeys());
			rs = new ResultSetListAdapter<FK>(objectName, statusUniqueColumns, list, FK.class);
			break;
		}
		default: {
			throw new BadRequestException("unknown status object: "+statusType);
		}
		}
		
		rs = filterStatus(rs, reqspec, currentUser, privilege);
		
		dumpResultSet(rs, reqspec, null, objectName, statusUniqueColumns, importedFKs, uks, true, resp);
		if(rs!=null) { rs.close(); }
	}
	
	ResultSet filterStatus(ResultSet rs, RequestSpec reqspec, Subject currentUser, PrivilegeType privilege) throws SQLException {
		if(reqspec.params!=null && reqspec.params.size()>0) {
			rs = new ResultSetFilterDecorator(rs, Arrays.asList(new Integer[]{1,2}), reqspec.params);
		}
		//log.info("doFilterStatusByQueryGrants: "+doFilterStatusByPermission+" / "+doFilterStatusByQueryGrants+" / "
		//		+ShiroUtils.isPermitted(currentUser, doNotCheckGrantsPermission)+":"+doNotCheckGrantsPermission);
		if(doFilterStatusByPermission) {
			// filter by [(relation)Type]:SELECT|EXECUTE:[schemaName]:[name]
			rs = new ResultSetPermissionFilterDecorator(rs, currentUser, "[3]:"+privilege+":[1]:[2]");
		}
		if(doFilterStatusByQueryGrants && (! ShiroUtils.isPermitted(currentUser, doNotCheckGrantsPermission)) ) { // XXX: doNotCheckGrantsPermission: SELECT_ANY and/or EXECUTE_ANY ?
			rs = new ResultSetGrantsFilterDecorator(rs, ShiroUtils.getSubjectRoles(currentUser), privilege, "name", "grants");
		}
		return rs;
	}
	
	void doDelete(Relation relation, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException, ServletException {
		Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		try {
		// roles permission
		/*Set<String> roles = ShiroUtils.getSubjectRoles(currentUser);
		List<Grant> deleteGrants = QOnModelUtils.filterGrantsByPrivilegeType(relation.getGrants(), PrivilegeType.DELETE);
		boolean hasRelationDeletePermission = QOnModelUtils.hasPermissionWithoutColumn(deleteGrants, roles)
				|| ShiroUtils.isPermitted(currentUser, ActionType.DELETE_ANY.name());
		if(!hasRelationDeletePermission) {
			log.info("delete grants: "+deleteGrants);
			throw new ForbiddenException("no delete permission on relation: "+relation.getName());
		}*/

		SQL sql = SQL.createDeleteSQL(relation);

		Constraint pk = SchemaModelUtils.getPK(relation);
		filterByKey(relation, reqspec, pk, sql);

		// xtra filters
		List<String> warnings = filterByXtraParams(relation, reqspec, sql);
		//delete warnings should not be ignored...
		if(warnings!=null && warnings.size()>0) {
			String warns = Utils.join(warnings, ", ");
			throw new BadRequestException("Filter error: "+warns);
		}
		
		PreparedStatement st = conn.prepareStatement(sql.getFinalSql());
		bindParameters(st, sql);
		
		log.info("sql delete: "+sql);
		
		int count = st.executeUpdate();
		
		if(fullKeyDefined(reqspec, pk)) {
			if(count==0) {
				throw new NotFoundException("Element not found");
			}
			if(count>1) {
				//may never occur...
				DBUtil.doRollback(conn);
				throw new InternalServerException("Full key defined but "+count+" elements deleted");
			}
		}
		//XXXdone: boundaries for # of updated (deleted) rows?
		if(reqspec.maxUpdates!=null && count > reqspec.maxUpdates) {
			throw new BadRequestException("Delete count ["+count+"] greater than update-max ["+reqspec.maxUpdates+"]");
		}
		if(reqspec.minUpdates!=null && count < reqspec.minUpdates) {
			throw new BadRequestException("Delete count ["+count+"] less than update-min ["+reqspec.minUpdates+"]");
		}
		
		//XXX: should 'onDelete' come before sql-delete? so SQL error/rollback has no influence on it?
		SchemaModel model = SchemaModelUtils.getModel(servletContext, reqspec.modelId);
		for(UpdatePlugin up: updatePlugins) {
			up.setProperties(prop);
			up.setSchemaModel(model);
			//up.setConnection(conn); //XXX UpdatePlugin.onDelete may need connection?
			up.onDelete(relation, reqspec);
		}
		
		//XXXxxx ??: (heterogeneous) array to ResultSet adapter? (?!?)
		conn.commit();
		resp.addIntHeader(ResponseSpec.HEADER_UPDATECOUNT, count);
		resp.setContentType(MIME_TEXT);
		resp.getWriter().write(count+" rows deleted");
		
		}
		catch(BadRequestException e) {
			DBUtil.doRollback(conn);
			throw e;
		}
		catch(SQLException e) {
			DBUtil.doRollback(conn);
			throw e;
			//XXX throw new InternalServerException("SQL Error: "+e);
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}

	void doUpdate(Relation relation, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		SQL sql = null;
		try {

		sql = SQL.createUpdateSQL(relation);
		
		Set<String> columns = new HashSet<String>();
		columns.addAll(relation.getColumnNames());

		Set<String> roles = ShiroUtils.getSubjectRoles(currentUser);
		List<Grant> updateGrants = QOnModelUtils.filterGrantsByPrivilegeType(relation.getGrants(), PrivilegeType.UPDATE);
		boolean hasRelationUpdatePermission = QOnModelUtils.hasPermissionWithoutColumn(updateGrants, roles)
				|| ShiroUtils.isPermitted(currentUser, ActionType.UPDATE_ANY.name());
		
		StringBuilder sb = new StringBuilder();
		int colsCount = 0;
		{
		Iterator<String> cols = reqspec.updateValues.keySet().iterator();
		for(; cols.hasNext();) {
			String col = cols.next();
			if(! columns.contains(col)) {
				log.warn("unknown column: "+col);
				throw new BadRequestException("[update] unknown column: "+col);
			}
			//TODOne: check UPDATE permission on each row, based on grants
			if(validateUpdateColumnPermissions && !hasRelationUpdatePermission && !QOnModelUtils.hasPermissionOnColumn(updateGrants, roles, col)) {
				throw new ForbiddenException("no update permission on column: "+relation.getName()+"."+col);
			}
			//XXX date ''? timestamp '' ? http://blog.tanelpoder.com/2012/12/29/a-tip-for-lazy-oracle-users-type-less-with-ansi-date-and-timestamp-sql-syntax/
			sb.append((colsCount!=0?", ":"")+col+" = ?");
			sql.bindParameterValues.add(reqspec.updateValues.get(col));
			colsCount++;
		}
		//log.debug("bindpars [#"+sql.bindParameterValues.size()+"]: "+sql.bindParameterValues);
		}
		// blobs
		{
			Iterator<String> pcols = reqspec.updatePartValues.keySet().iterator();
			while(pcols.hasNext()) {
				String col = pcols.next();
				if(! columns.contains(col)) {
					log.warn("unknown column: "+col);
					throw new BadRequestException("[update] unknown column: "+col);
				}
				//TODOne: check UPDATE permission on each row, based on grants
				if(validateUpdateColumnPermissions && !hasRelationUpdatePermission && !QOnModelUtils.hasPermissionOnColumn(updateGrants, roles, col)) {
					throw new ForbiddenException("no update permission on column: "+relation.getName()+"."+col);
				}
				//XXX date ''? timestamp '' ? http://blog.tanelpoder.com/2012/12/29/a-tip-for-lazy-oracle-users-type-less-with-ansi-date-and-timestamp-sql-syntax/
				sb.append((colsCount!=0?", ":"")+col+" = ?");
				
				int colindex = relation.getColumnNames().indexOf(col);
				String ctype = relation.getColumnTypes().get(colindex);
				
				boolean isBinary = DBUtil.BLOB_COL_TYPES_LIST.contains(ctype.toUpperCase());
				if(isBinary) {
					sql.bindParameterValues.add(reqspec.updatePartValues.get(col).getInputStream());
				}
				else {
					sql.bindParameterValues.add(new InputStreamReader( reqspec.updatePartValues.get(col).getInputStream() ));
				}
				colsCount++;
				log.info("col["+colindex+"] "+col+": "+ctype+" [isBinary="+isBinary+"]");
			}
		}

		if("".equals(sb.toString())) {
			throw new BadRequestException("[update] No valid columns");
		}
		sql.applyUpdate(sb.toString());

		Constraint pk = SchemaModelUtils.getPK(relation);
		filterByKey(relation, reqspec, pk, sql);

		// xtra filters
		List<String> warnings = filterByXtraParams(relation, reqspec, sql);
		//update warnings should not be ignored...
		if(warnings!=null && warnings.size()>0) {
			String warns = Utils.join(warnings, ", ");
			throw new BadRequestException("Filter error: "+warns);
		}

		//log.info("pre-sql update: "+sql);
		
		PreparedStatement st = conn.prepareStatement(sql.getFinalSql());
		bindParameters(st, sql);

		log.debug("sql update: "+sql);
		
		int count = st.executeUpdate();
		
		//XXXdone: boundaries for # of updated rows?
		if(reqspec.maxUpdates!=null && count > reqspec.maxUpdates) {
			throw new BadRequestException("Update count ["+count+"] greater than update-max ["+reqspec.maxUpdates+"]");
		}
		if(reqspec.minUpdates!=null && count < reqspec.minUpdates) {
			throw new BadRequestException("Update count ["+count+"] less than update-min ["+reqspec.minUpdates+"]");
		}
		
		//XXX add ResultSet generatedKeys = st.getGeneratedKeys(); //?
		
		//XXX plugin should be called before execute()/bindParameters()?
		SchemaModel model = SchemaModelUtils.getModel(servletContext, reqspec.modelId);
		for(UpdatePlugin up: updatePlugins) {
			up.setProperties(prop);
			up.setSchemaModel(model);
			up.setConnection(conn);
			up.onUpdate(relation, reqspec);
		}
		
		//XXX: (heterogeneous) array / map to ResultSet adapter?
		conn.commit();
		resp.addIntHeader(ResponseSpec.HEADER_UPDATECOUNT, count);
		resp.setContentType(MIME_TEXT);
		resp.getWriter().write(count+" rows updated");

		}
		catch(BadRequestException e) {
			DBUtil.doRollback(conn);
			throw e;
		}
		catch(SQLException e) {
			DBUtil.doRollback(conn);
			log.warn("Update error, ex="+e.getMessage()+" ; sql=\n"+sql,e); //e.printStackTrace();
			throw new InternalServerException("SQL Error: "+e);
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}

	void doInsert(Relation relation, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		try {

		SQL sql = SQL.createInsertSQL(relation);

		Set<String> columns = new HashSet<String>();
		columns.addAll(relation.getColumnNames());

		//use url params to set PK cols values
		Constraint pk = SchemaModelUtils.getPK(relation);
		String[] pkcols = null;
		if(pk!=null) {
			for(int i=0;i<pk.getUniqueColumns().size() && i<reqspec.params.size();i++) {
				String pkcol = pk.getUniqueColumns().get(i);
				if(! columns.contains(pkcol)) {
					log.warn("unknown PK column: "+pkcol);
					continue;
				}
				String pkval = reqspec.params.get(i);
				if(pkcol!=null && pkval!=null) {
					reqspec.updateValues.put(pkcol, pkval);
				}
			}
			pkcols = pk.getUniqueColumns().toArray(new String[]{});
		}
		
		Set<String> roles = ShiroUtils.getSubjectRoles(currentUser);
		List<Grant> insertGrants = QOnModelUtils.filterGrantsByPrivilegeType(relation.getGrants(), PrivilegeType.INSERT);
		boolean hasRelationInsertPermission = QOnModelUtils.hasPermissionWithoutColumn(insertGrants, roles)
				|| ShiroUtils.isPermitted(currentUser, ActionType.INSERT_ANY.name());
		
		StringBuilder sbCols = new StringBuilder();
		StringBuilder sbVals = new StringBuilder();
		int colsCount = 0;
		{
		Iterator<String> cols = reqspec.updateValues.keySet().iterator();
		for(; cols.hasNext();) {
			String col = cols.next();
			if(! columns.contains(col)) {
				log.warn("unknown 'value' column: "+col);
				throw new BadRequestException("[insert] unknown column: "+col);
			}
			if(validateUpdateColumnPermissions && !hasRelationInsertPermission && !QOnModelUtils.hasPermissionOnColumn(insertGrants, roles, col)) {
				//log.warn("user: "+currentUser+" ; principal: "+currentUser.getPrincipal()+" ; roles: "+roles);
				throw new ForbiddenException("no insert permission on column: "+relation.getName()+"."+col);
			}
			//XXX timestamp '' ?
			sbCols.append((colsCount!=0?", ":"")+col);
			sbVals.append((colsCount!=0?", ":"")+"?");
			sql.bindParameterValues.add(reqspec.updateValues.get(col));
			colsCount++;
		}
		}

		// blobs
		{
			Iterator<String> pcols = reqspec.updatePartValues.keySet().iterator();
			while(pcols.hasNext()) {
				String pcol = pcols.next();
				if(! columns.contains(pcol)) {
					log.warn("unknown 'value' column: "+pcol);
					throw new BadRequestException("[insert] unknown column: "+pcol);
				}
				if(validateUpdateColumnPermissions && !hasRelationInsertPermission && !QOnModelUtils.hasPermissionOnColumn(insertGrants, roles, pcol)) {
					throw new ForbiddenException("no insert permission on column: "+relation.getName()+"."+pcol);
				}
				int colindex = relation.getColumnNames().indexOf(pcol);
				if(colindex>=0) {
					sbCols.append((colsCount!=0?", ":"")+pcol);
					sbVals.append((colsCount!=0?", ":"")+"?");
					
					String ctype = relation.getColumnTypes().get(colindex);
					boolean isBinary = DBUtil.BLOB_COL_TYPES_LIST.contains(ctype.toUpperCase());
					if(isBinary) {
						sql.bindParameterValues.add(reqspec.updatePartValues.get(pcol).getInputStream());
					}
					else {
						sql.bindParameterValues.add(new InputStreamReader( reqspec.updatePartValues.get(pcol).getInputStream() ));
					}
					log.info("col["+colindex+"] "+pcol+": "+ctype+" [isBinary="+isBinary+"]");
					colsCount++;
				}
				else {
					log.warn("column "+pcol+" not found on relation "+relation);
				}
			}
		}
		
		if("".equals(sbCols.toString())) {
			throw new BadRequestException("[insert] No valid columns");
		}
		sql.applyInsert(sbCols.toString(), sbVals.toString());

		PreparedStatement st = pkcols!=null? 
			conn.prepareStatement(sql.getFinalSql(), pkcols):
			conn.prepareStatement(sql.getFinalSql());
		bindParameters(st, sql);

		log.info("sql insert: "+sql);
		
		int count = st.executeUpdate();
		
		//XXXdone: boundaries for # of updated (inserted) rows?
		if(reqspec.maxUpdates!=null && count > reqspec.maxUpdates) {
			throw new BadRequestException("Insert count ["+count+"] greater than update-max ["+reqspec.maxUpdates+"]");
		}
		if(reqspec.minUpdates!=null && count < reqspec.minUpdates) {
			throw new BadRequestException("Insert count ["+count+"] less than update-min ["+reqspec.minUpdates+"]");
		}
		
		// http://stackoverflow.com/questions/1915166/how-to-get-the-insert-id-in-jdbc
		ResultSet generatedKeys = st.getGeneratedKeys();
		if (generatedKeys.next()) {
			List<String> colVals = getGeneratedKeys(generatedKeys);
			/*int colCount = generatedKeys.getMetaData().getColumnCount();
			List<String> colVals = new ArrayList<String>();
			for(int i=0;i<colCount;i++) {
				colVals.add(generatedKeys.getString(i+1));
			}*/
			resp.setHeader(ResponseSpec.HEADER_RELATION_UK_VALUES, Utils.join(colVals, ", "));
			//log.info("generatedKeys[pk="+Arrays.toString(pkcols)+";#="+colCount+"]: "+ Utils.join(colVals, ", "));
		}
		
		SchemaModel model = SchemaModelUtils.getModel(servletContext, reqspec.modelId);
		for(UpdatePlugin up: updatePlugins) {
			up.setProperties(prop);
			up.setSchemaModel(model);
			up.setConnection(conn);
			up.onInsert(relation, reqspec);
		}
		
		//XXX: (heterogeneous) array / map to ResultSet adapter?
		conn.commit();
		resp.addIntHeader(ResponseSpec.HEADER_UPDATECOUNT, count);
		resp.setStatus(HttpServletResponse.SC_CREATED);
		resp.setContentType(MIME_TEXT);
		resp.getWriter().write(count+" rows inserted");
		
		}
		catch(BadRequestException e) {
			DBUtil.doRollback(conn);
			throw e;
		}
		catch(SQLException e) {
			DBUtil.doRollback(conn);
			//e.printStackTrace();
			throw new InternalServerException("SQL Error: "+e);
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}
	
	void doManage(RequestSpec reqspec, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		//TODO: only reloads model for now...
		// - reload-config, reload-movel, rerun-processors
		doInit(req.getServletContext());
		resp.setContentType(MIME_TEXT);
		resp.getWriter().write("queryon config reloaded");
	}
	
	static boolean fullKeyDefined(RequestSpec reqspec, Constraint pk) {
		if(pk==null) {
			return false;
		}
		//log.info("#cols: pk="+pk.getUniqueColumns().size()+", req="+reqspec.params.size());
		return pk.getUniqueColumns().size() <= reqspec.params.size();
	}
	
	List<String> getGeneratedKeys(ResultSet generatedKeys) throws SQLException {
		int colCount = generatedKeys.getMetaData().getColumnCount();
		List<String> colVals = new ArrayList<String>();
		for(int i=0;i<colCount;i++) {
			colVals.add(generatedKeys.getString(i+1));
		}
		return colVals;
	}
	
	static void filterByKey(Relation relation, RequestSpec reqspec, Constraint pk, SQL sql) {
		String filter = "";
		// TODOxxx: what if parameters already defined in query?
		if(reqspec.params.size()>0) {
			if(relation.getParameterCount()!=null && relation.getParameterCount()>0) {
				// query parameters already added in 'addOriginalParameters()'
				if(relation.getParameterCount() > reqspec.params.size()) {
					log.warn("parameters defined "+reqspec.params+" but query '"+relation.getName()+"' expects more ["+relation.getParameterCount()+"] parameters");
				}
				else if(relation.getParameterCount() < reqspec.params.size()) {
					log.warn("parameters defined "+reqspec.params+" but query '"+relation.getName()+"' expects less ["+relation.getParameterCount()+"] parameters");
				}
			}
			else if(pk==null) {
				log.warn("filter params defined "+reqspec.params+" but table '"+relation.getName()+"' has no PK or UNIQUE constraint");
			}
			else {
				if(reqspec.params.size()>pk.getUniqueColumns().size()) {
					log.warn("parameter count [#"+reqspec.params.size()+"] bigger than pk size [#"+pk.getUniqueColumns().size()+"]");
					log.debug("parameters: "+reqspec.params);
					//XXX throw BadRequest ?
				}
				for(int i=0;i<pk.getUniqueColumns().size();i++) {
					if(reqspec.params.size()<=i) { break; }
					//String s = reqspec.params.get(i);
					filter += (i!=0?" and ":"")+SQL.sqlIdDecorator.get(pk.getUniqueColumns().get(i))+" = ?"; //+reqspec.params.get(i)
					sql.bindParameterValues.add(reqspec.params.get(i));
					logFilter.info("filterByKey: value["+i+"]="+reqspec.params.get(i));
				}
			}
		}
		sql.addFilter(filter);
	}
	
	/**
	 * @param relation
	 * @param reqspec
	 * @param sql
	 * @return List of warnings (filter columns not found) 
	 */
	static List<String> filterByXtraParams(Relation relation, RequestSpec reqspec, SQL sql) {
		// TODO parameters: remove reqspec.params in excess of #parametersToBind ?
		
		List<String> colNames = relation.getColumnNames();
		String relationName = relation.getName();
		
		final List<String> warnings = new ArrayList<String>();
		
		if(colNames!=null) {
			Set<String> columns = new HashSet<String>();
			columns.addAll(colNames);
			//XXX bind parameters: column type?
			
			// uni-valued filters
			addUniqueFilter(reqspec.filterEquals, columns, sql, "=", relationName, warnings);
			addUniqueFilter(reqspec.filterNotEquals, columns, sql, "<>", relationName, warnings); //XXXxx should be multi-valued? nah, there is already a 'not in'
			addUniqueFilter(reqspec.filterGreaterThan, columns, sql, ">", relationName, warnings);
			addUniqueFilter(reqspec.filterGreaterOrEqual, columns, sql, ">=", relationName, warnings);
			addUniqueFilter(reqspec.filterLessThan, columns, sql, "<", relationName, warnings);
			addUniqueFilter(reqspec.filterLessOrEqual, columns, sql, "<=", relationName, warnings);

			// multi-valued filters
			addMultiFilter(reqspec.filterLike, columns, sql, "like ?", relationName, warnings);
			addMultiFilter(reqspec.filterNotLike, columns, sql, "not like ?", relationName, warnings);

			// multi-valued with subexpression filters
			addMultiFilterSubexpression(reqspec.filterIn, columns, sql, "in", relationName, warnings);
			addMultiFilterSubexpression(reqspec.filterNotIn, columns, sql, "not in", relationName, warnings);
		}
		else {
			if(reqspec.filterEquals.size()>0) {
				log.warn("relation '"+relation.getName()+"' has no columns specified");
			}
		}
		
		return warnings;
	}
	
	static void addUniqueFilter(final Map<String, String> valueMap, Set<String> columns, SQL sql, String compareSymbol, String relationName, List<String> warnings) {
		for(String col: valueMap.keySet()) {
			if(!validateFilterColumnNames || columns.contains(col)) {
				sql.bindParameterValues.add(valueMap.get(col));
				sql.addFilter(SQL.sqlIdDecorator.get(col)+" "+compareSymbol+" ?");
				logFilter.info("addUniqueFilter: values="+valueMap.get(col));
			}
			else {
				log.warn("unknown filter column: "+col+" [relation="+relationName+"]");
				warnings.add("unknown filter column: "+col);
			}
		}
	}
	
	static void addMultiFilter(final Map<String, String[]> valueMap, Set<String> columns, SQL sql, String compareExpression, String relationName, List<String> warnings) {
		for(String col: valueMap.keySet()) {
			if(!validateFilterColumnNames || columns.contains(col)) {
				String[] values = valueMap.get(col);
				for(int i=0;i<values.length;i++) {
					sql.bindParameterValues.add(values[i]);
					sql.addFilter(SQL.sqlIdDecorator.get(col)+" "+compareExpression); //" like ?"
				}
				logFilter.info("addMultiFilter: values="+Arrays.asList(values));
			}
			else {
				log.warn("unknown filter column: "+col+" [relation="+relationName+"]");
				warnings.add("unknown filter column: "+col);
			}
		}
	}

	static void addMultiFilterSubexpression(final Map<String, String[]> valueMap, Set<String> columns, SQL sql, String compareExpression, String relationName, List<String> warnings) {
		for(String col: valueMap.keySet()) {
			if(!validateFilterColumnNames || columns.contains(col)) {
				StringBuilder sb = new StringBuilder();
				sb.append(SQL.sqlIdDecorator.get(col)+" "+compareExpression+" (");
				String[] values = valueMap.get(col);
				for(int i=0;i<values.length;i++) {
					String value = values[i];
					sb.append((i>0?", ":"")+"?");
					sql.bindParameterValues.add(value);
				}
				sb.append(")");
				sql.addFilter(sb.toString());
				logFilter.info("addMultiFilterSubexpression: values="+Arrays.asList(values));
			}
			else {
				log.warn("unknown filter column: "+col+" [relation="+relationName+"]");
				warnings.add("unknown filter column: "+col);
			}
		}
	}
	
	static void addOriginalParameters(RequestSpec reqspec, SQL sql) throws SQLException {
		int informedParams = reqspec.params.size();
		//XXX bind all or bind none?
		//int bindParamsLoop = informedParams; //bind all
		int bindParamsLoop = -1; // bind none
		if(sql.originalBindParameterCount!=null) {
			if(sql.originalBindParameterCount > informedParams) {
				//XXX option to bind params with null?
				throw new BadRequestException("Query '"+reqspec.object+"' needs "+sql.originalBindParameterCount+" parameters but "
					+((informedParams>0)?"only "+informedParams:"none")
					+((informedParams>1)?" were":" was")
					+" informed");
			}
			bindParamsLoop = sql.originalBindParameterCount;
		}
		for(int i=0;i<bindParamsLoop;i++) {
			sql.bindParameterValues.add(reqspec.params.get(i));
		}
	}
	
	//XXX: move to SQL class?
	static void bindParameters(PreparedStatement st, SQL sql) throws SQLException, IOException {
		for(int i=0;i<sql.bindParameterValues.size();i++) {
			Object value = sql.bindParameterValues.get(i);
			if(value instanceof String) {
				st.setString(i+1, (String) value);
			}
			else if(value instanceof InputStream) {
				st.setBinaryStream(i+1, (InputStream) value);
			}
			else if(value instanceof Reader) {
				st.setCharacterStream(i+1, (Reader) value);
			}
			else if(value instanceof Part) {
				Part p = (Part) value;
				//XXX guess if binary or character stream... based on p.getContentType() or column type??
				//st.setBinaryStream(i+1, p.getInputStream());
				st.setCharacterStream(i+1, new InputStreamReader(p.getInputStream()));
			}
			else {
				log.warn("bindParameters: unknown value type: " + (value!=null?value.getClass().getName():value) );
			}
		}
	}
	
	void dumpResultSet(ResultSet rs, RequestSpec reqspec, String schemaName, String queryName, 
			List<String> uniqueColumns, List<FK> importedFKs, List<Constraint> UKs,
			boolean mayApplyLimitOffset, HttpServletResponse resp) 
			throws SQLException, IOException {
		dumpResultSet(rs, reqspec, schemaName, queryName, uniqueColumns, importedFKs, UKs, mayApplyLimitOffset, resp, getLimit(reqspec.limit));
	}
	
	static void dumpResultSet(ResultSet rs, RequestSpec reqspec, String schemaName, String queryName, 
			List<String> uniqueColumns, List<FK> importedFKs, List<Constraint> UKs,
			boolean mayApplyLimitOffset, HttpServletResponse resp, Integer limit) 
			throws SQLException, IOException {
		if(mayApplyLimitOffset) {
			rs = new ResultSetLimitOffsetDecorator(rs, limit, reqspec.offset);
		}
		int count = 0;
		DumpSyntaxInt ds = reqspec.outputSyntax;
		if(ds.usesImportedFKs()) {
			ds.setImportedFKs(importedFKs);
		}
		if(ds.usesAllUKs()) {
			ds.setAllUKs(UKs);
		}
		
		if(log.isDebugEnabled()) {
			DataDumpUtils.logResultSetColumnsTypes(rs.getMetaData(), queryName, log);
		}
		
		ds.initDump(schemaName, queryName, uniqueColumns, rs.getMetaData());

		resp.setContentType(ds.getMimeType());
		//resp.addHeader(ResponseSpec.HEADER_CONTENT_TYPE, ds.getMimeType());
		//XXX download? http://stackoverflow.com/questions/398237/how-to-use-the-csv-mime-type
		//resp.addHeader("Content-disposition", "attachment;filename="+table.name+"."+ds.getDefaultFileExtension());
		String contentLocation = (String) reqspec.request.getAttribute(REQ_ATTR_CONTENTLOCATION);
		if(contentLocation!=null) {
			resp.addHeader(ResponseSpec.HEADER_CONTENT_LOCATION, contentLocation);
		}
		resp.addHeader(ResponseSpec.HEADER_RESULTSET_LIMIT, String.valueOf(limit));
		
		if(ds.acceptsOutputStream()) {
			ds.dumpHeader(resp.getOutputStream());
			boolean hasNext = ds.isFetcherSyntax()?true:rs.next();
			while(hasNext) {
				ds.dumpRow(rs, count, resp.getOutputStream());
				count++;
				hasNext = rs.next();
			}
		}
		else {
			ds.dumpHeader(resp.getWriter());
			boolean hasNext = ds.isFetcherSyntax()?true:rs.next();
			while(hasNext) {
				ds.dumpRow(rs, count, resp.getWriter());
				count++;
				hasNext = rs.next();
			}
		}
		
		if(count==0) {
			// rfc2616-sec10.html : 10.2.5 204 No Content
			//https://benramsey.com/blog/2008/05/http-status-204-no-content-and-205-reset-content/
			//resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			//resp.addIntHeader("X-ResultSet-Count", count);
		}
		if(ds.acceptsOutputStream()) {
			ds.dumpFooter(count, resp.getOutputStream());
		}
		else {
			ds.dumpFooter(count, resp.getWriter());
		}
	}
	
	void dumpBlob(ResultSet rs, RequestSpec reqspec, String queryName,
			boolean mayApplyLimitOffset, HttpServletResponse resp) 
			throws SQLException, IOException {
		if(mayApplyLimitOffset) {
			rs = new ResultSetLimitOffsetDecorator(rs, getLimit(reqspec.limit), reqspec.offset);
		}
		
		ResultSetMetaData rsmd = rs.getMetaData();
		/*if(rsmd.getColumnCount()!=1) {
			throw new BadRequestException("ResultSet has more than 1 column ["+queryName+"; #="+rsmd.getColumnCount()+"]");
		}*/
		List<String> cols = DataDumpUtils.getColumnNames(rsmd);
		int colIndex = cols.indexOf(reqspec.uniValueCol);
		if(colIndex==-1) {
			throw new BadRequestException("Data column '"+reqspec.uniValueCol+"' not found");
		}

		String mimeType = MIME_TEXT;
		//XXXdone: get mimetype from column type (BLOB=application/octet-stream, CLOB=text/plain, ...) ? http://www.rfc-editor.org/rfc/rfc2046.txt
		List<Class<?>> types = DataDumpUtils.getColumnTypes(rsmd);
		if(Blob.class.equals( types.get(colIndex) )) {
			mimeType = "application/octet-stream";
		}
		
		String filename = null;
		
		if(reqspec.uniValueMimetypeCol!=null && !cols.contains(reqspec.uniValueMimetypeCol)) {
			throw new BadRequestException("Type column '"+reqspec.uniValueMimetypeCol+"' not found");
		}
		if(reqspec.uniValueMimetype!=null) {
			mimeType = reqspec.uniValueMimetype;
		}
		//mimeType = reqspec.outputSyntax.getMimeType();
		
		if(reqspec.uniValueFilenameCol!=null && !cols.contains(reqspec.uniValueFilenameCol)) {
			throw new BadRequestException("Filename column '"+reqspec.uniValueFilenameCol+"' not found");
		}
		if(reqspec.uniValueFilename!=null) {
			filename = reqspec.uniValueFilename;
		}
		
		if(rs.next()) {
			if(reqspec.uniValueMimetypeCol!=null) {
				mimeType = rs.getString(reqspec.uniValueMimetypeCol);
			}
			//resp.addHeader(ResponseSpec.HEADER_CONTENT_TYPE, mimeType);
			resp.setContentType(mimeType);
			if(reqspec.uniValueFilenameCol!=null) {
				filename = rs.getString(reqspec.uniValueFilenameCol);
			}
			if(filename!=null) {
				resp.addHeader(ResponseSpec.HEADER_CONTENT_DISPOSITION, "attachment; filename=" + filename);
			}
			InputStream is = rs.getBinaryStream(reqspec.uniValueCol);
			if(is!=null) {
				IOUtil.pipeStreams(is, resp.getOutputStream());
				is.close();
			}
			else {
				// null return: null
				//throw new BadRequestException("Null stream [column="+reqspec.uniValueCol+"]");
			}
		}
		else {
			throw new BadRequestException("ResultSet has no rows ["+queryName+"]");
		}
		
		if(rs.next()) {
			log.warn("more than 1 row, response may already be committed [query="+queryName+"]");
			throw new BadRequestException("ResultSet has more than 1 row ["+queryName+"]");
		}
	}
	
	/*
	 * possible syntaxes for commands/special queries
	 * - desc ; desc <table> //oracle-like
	 * - show catalogs; schemas; tables; columns; //mysql-like - http://dev.mysql.com/doc/refman/5.0/en/show.html;
	 *   h2-like - http://www.h2database.com/html/grammar.html#show
	 * - metadata.gettables(<schema>); metadata.getcolumns(<table>); //"jdbc"
	 * - \d+ <table> //postgresql
	 * - !dbinfo ; !describe ; !tables ; !columns <table> ; !exportedkeys ; !importedkeys ; !indexes ; !metadata ; !primarykeys, !procedures ; // sqlline - http://sqlline.sourceforge.net/
	 * - $showtables [<xxx>] ; $showcolumns <xxx> ; $tables [<xxx>] ; $columns <xxx> ; $schemas
	 * x exportedkeys, x importedkeys, x metadata
	 * TODO: indexes, primarykeys
	 * getCatalogs()? $metadata getCatalogs
	 */
	static final SqlCommand[] cmds = new SqlCommand[]{ new ShowSchemas(), new ShowTables(), new ShowColumns(), new ShowImportedKeys(), new ShowExportedKeys(), new ShowMetadata() };
	
	boolean trySqlCommand(Query relation, RequestSpec reqspec, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		String sql = relation.getQuery();

		for(SqlCommand cmd: cmds) {
			if(cmd.matches(sql)) {
				Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
				ResultSet rs = cmd.run(conn);
				try {
					//XXX: mayApplyLimitOffset should be true or false?
					dumpResultSet(rs, reqspec, relation.getSchemaName(), relation.getName(), /*pk*/ null, /*fks*/ null, /*uks*/ null, /*mayApplyLimitOffset*/ true, resp);
				}
				catch(SQLException e) {
					DBUtil.doRollback(conn);
					log.warn("exception in 'trySqlCommand'/"+cmd.getClass().getSimpleName()+": "+e+" ; sql:\n"+sql);
					throw e;
				}
				finally {
					ConnectionUtil.closeConnection(conn);
				}
				return true;
			}
		}
		
		return false;
	}
	
	static DBObjectType statusObject(String name) {
		try {
			DBObjectType type = DBObjectType.valueOf(name);
			return type;
		}
		catch(IllegalArgumentException e) {
			return null;
		}
	}
	
	public static String getObjectType(DBIdentifiable dbid) {
		String ret = null;
		if(dbid instanceof Table) {
			ret = DBObjectType.TABLE.name();
		}
		else if(dbid instanceof Query) {
			ret = QueryOn.CONST_QUERY;
		}
		else if(dbid instanceof View) {
			ret = DBObjectType.VIEW.name();
		}
		else {
			ret = QueryOn.CONST_RELATION;
		}
		return ret;
	}
	
	public static String getUsername(Subject currentUser) {
		return String.valueOf(currentUser.getPrincipal());
	}
	
	int getLimit(Integer requestSpecLimit) {
		return getLimit(requestSpecLimit, defaultLimit, maxLimit);
	}
	
	static int getLimit(Integer requestSpecLimit, Integer defaultLimit, int maxLimit) {
		if(requestSpecLimit!=null) {
			return Math.min(requestSpecLimit, maxLimit);
		}
		return Math.min(defaultLimit, maxLimit);
	}
	
	/*@SuppressWarnings("rawtypes")
	static <T> List<String> getSimpleClassNames(List<Class<T>> classes) {
		List<String> l = new ArrayList<String>();
		for(Class c: classes) {
			l.add(c.getSimpleName());
		}
		return l;
	}*/
	
}
