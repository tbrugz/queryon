package tbrugz.queryon;

import java.beans.IntrospectionException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
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

import tbrugz.queryon.action.QOnManage;
import tbrugz.queryon.exception.ForbiddenException;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.exception.NotFoundException;
import tbrugz.queryon.processor.UpdatePluginUtils;
import tbrugz.queryon.resultset.ResultSetFilterDecorator;
import tbrugz.queryon.resultset.ResultSetGrantsFilterDecorator;
import tbrugz.queryon.resultset.ResultSetLimitOffsetDecorator;
import tbrugz.queryon.resultset.ResultSetMetadata2RsAdapter;
import tbrugz.queryon.resultset.ResultSetPermissionFilterDecorator;
import tbrugz.queryon.resultset.UnionResultSet;
import tbrugz.queryon.sqlcmd.ShowColumns;
import tbrugz.queryon.sqlcmd.ShowExportedKeys;
import tbrugz.queryon.sqlcmd.ShowImportedKeys;
import tbrugz.queryon.sqlcmd.ShowMetadata;
import tbrugz.queryon.sqlcmd.ShowSchemas;
import tbrugz.queryon.sqlcmd.ShowTables;
import tbrugz.queryon.syntaxes.WebSyntax;
import tbrugz.queryon.util.DBObjectUtils;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.queryon.util.MiscUtils;
import tbrugz.queryon.util.QOnModelUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.queryon.util.ShiroUtils;
import tbrugz.queryon.util.WebUtils;
import tbrugz.sqldump.resultset.ResultSetListAdapter;
import tbrugz.sqldump.resultset.pivot.PivotResultSet;
import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.datadump.DumpSyntaxBuilder;
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
import tbrugz.sqldump.dbmodel.ParametrizedDBObject;
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
import tbrugz.sqldump.util.MathUtil;
import tbrugz.sqldump.util.ParametrizedProperties;
import tbrugz.sqldump.util.SQLUtils;
import tbrugz.sqldump.util.StringDecorator;
import tbrugz.sqldump.util.StringUtils;
import tbrugz.sqldump.util.Utils;

/**
 * @see Web API Design - http://info.apigee.com/Portals/62317/docs/web%20api.pdf
 */
/*
 * TODO r2rml: option to understand URLs like: Department/name=accounting;city=Cambridge
 * ?TODO: prevent sql injection
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
		SQL_ANY, // any SQL (select, insert, ...)
		SELECT_ANY, // any query (select ...)
		VALIDATE_ANY,
		EXPLAIN_ANY,
		MANAGE,
		// not actions but special (global) permissions:
		INSERT_ANY,
		UPDATE_ANY,
		DELETE_ANY
	}
	
	// 'status objects' (SO)
	/*public static final DBObjectType[] STATUS_OBJECTS = {
		DBObjectType.TABLE, DBObjectType.VIEW, DBObjectType.RELATION, DBObjectType.EXECUTABLE, DBObjectType.FK
	};*/

	public static final String[] DEFAULT_CLASSLOADING_PACKAGES = { "tbrugz.queryon", "tbrugz.queryon.processor", "tbrugz.sqldump", "tbrugz.sqldump.datadump", "tbrugz.sqldump.processors", "tbrugz", "" };
	
	public static final String ACTION_QUERY_ANY = "QueryAny";
	public static final String ACTION_VALIDATE_ANY = "ValidateAny";
	public static final String ACTION_EXPLAIN_ANY = "ExplainAny";
	public static final String ACTION_SQL_ANY = "SqlAny";
	
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
	
	static final String INITP_PROPERTIES_RESOURCE = "properties-resource";
	static final String INITP_PROPERTIES_FILE = "properties-file";
	static final String SYSPROP_PROPERTIES_PATH = "queryon.properties-path";
	static final String ENV_PROPERTIES_PATH = "QON_PROPERTIES_PATH";

	//static final String INITP_MODEL_ID = "model-id";
	static final String DEFAULT_PROPERTIES_RESOURCE = "/queryon.properties";
	static final String DEFAULT_PROPERTIES_VALUES_RESOURCE = "/queryon-defaults.properties";
	static final String DEFAULT_SYNTAXES_RESOURCE = "/dumpsyntaxes-qon.properties";
	
	//static final String DEFAULT_MODELID = "default";
	
	public static final String CONN_PROPS_PREFIX = "queryon";
	
	static final String PROP_MODELS = "queryon.models";
	static final String PROP_MODELS_DEFAULT = "queryon.models.default";
	
	public static final String PROP_DEFAULT_LIMIT = "queryon.limit.default";
	public static final String PROP_MAX_LIMIT = "queryon.limit.max";
	static final String PROP_BASE_URL = "queryon.baseurl";
	static final String PROP_CONTEXT_PATH = "queryon.context-path";
	static final String PROP_HEADERS_ADDCONTENTLOCATION = "queryon.headers.addcontentlocation";
	static final String PROP_SYNTAX_DEFAULT = "queryon.syntax.default";
	static final String PROP_XTRASYNTAXES = "queryon.xtrasyntaxes";
	static final String PROP_UPDATE_PLUGINS = "queryon.update-plugins";
	static final String PROP_PROCESSORS_ON_STARTUP = "queryon.processors-on-startup";
	static final String PROP_SQLDIALECT = "queryon.sqldialect"; //TODO: sqldialect per model...
	static final String PROP_VALIDATE_GETMETADATA = "queryon.validate.x-getmetadata";
	static final String PROP_VALIDATE_ORDERCOLNAME = "queryon.validate.x-ordercolumnname";
	static final String PROP_VALIDATE_FILTERCOLNAME = "queryon.validate.x-filtercolumnname";
	public static final String PROP_SCHEMAS_TO_IGNORE = "queryon.schemas-to-ignore";
	static final String PROP_SQL_USEIDDECORATOR = "queryon.sql.use-id-decorator";
	static final String PROP_AUTH_SHIRO_ALLOW_DISABLED = "queryon.auth.shiro.allow-disabled";
	
	//static final String DEFAULT_XTRA_SYNTAXES = "tbrugz.queryon.syntaxes.HTMLAttrSyntax";
	static final String DEFAULT_XTRA_SYNTAXES = null;
	
	static final String PROP_X_REQUEST_UTF8 = "queryon.x-request-utf8";
	
	static final String SUFFIX_GRABCLASS = ".grabclass";
	//static final String SUFFIX_SQLDIALECT = ".sqldialect";
	static final String PROP_GRABCLASS = "queryon.grabclass";
	
	static final String DEFAULT_OUTPUT_SYNTAX = "html";
	
	public static final String ATTR_PROP = "prop";
	public static final String ATTR_MODEL_MAP = "modelmap";
	public static final String ATTR_DEFAULT_MODEL = "defaultmodel";
	public static final String ATTR_SCHEMAS_MAP = "schemasmap";
	public static final String ATTR_INIT_ERROR = "initerror";
	public static final String ATTR_DUMP_SYNTAX_UTILS = "dsutils";
	public static final String ATTR_UPDATE_PLUGINS = "update-plugins";
	
	public static final String METHOD_GET = "GET";
	public static final String METHOD_HEAD = "HEAD";
	public static final String METHOD_PATCH = "PATCH";
	public static final String METHOD_POST = "POST";
	public static final String METHOD_PUT = "PUT";
	public static final String METHOD_DELETE = "DELETE";
	
	public static final String UTF8 = "UTF-8";
	
	protected final Properties prop = new ParametrizedProperties();
	protected DumpSyntaxUtils dsutils;
	//SchemaModel model;
	//final Map<String, SchemaModel> models = new HashMap<String, SchemaModel>();
	
	String propertiesResource = null;
	String propertiesFile = null;
	
	final Map<String, List<UpdatePlugin>> updatePlugins = new HashMap<String, List<UpdatePlugin>>();
	//String modelId;
	
	boolean doFilterStatusByPermission = true; //XXX: add prop for doFilterStatusByPermission ?
	boolean doFilterStatusByQueryGrants = true; //XXX: add prop for doFilterStatusByQueryGrants ?
	static boolean validateFilterColumnNames = true;
	//boolean xSetRequestUtf8 = false;
	protected boolean validateUpdateColumnPermissions = true; //XXX: add prop for validateUpdateColumnPermissions
	protected Integer defaultLimit;
	protected int maxLimit;
	protected boolean debugMode = false; //XXX add prop for debugMode
	private Boolean shiroEnabled = null;
	
	public static final String doNotCheckGrantsPermission = ActionType.SELECT_ANY.name();
	
	protected ServletContext servletContext = null;
	protected String servletUrlContext = "q";
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		doInitProperties(config);
		//modelId = config.getInitParameter(INITP_MODEL_ID);
		doInit(config.getServletContext());
	}
	
	static {
		//BaseResultSetCollectionAdapter.setCollectionValuesJoiner("|");
		ParametrizedProperties.setUseSystemProperties(true);
	}
	
	protected void doInitProperties(ServletConfig config) {
		//log4jInit();
		propertiesResource = config.getInitParameter(INITP_PROPERTIES_RESOURCE);
		propertiesFile = null;

		if(propertiesResource==null) {
			propertiesFile = config.getInitParameter(INITP_PROPERTIES_FILE);
			if(propertiesFile==null) {
				propertiesFile = System.getProperty(SYSPROP_PROPERTIES_PATH);
				if(propertiesFile==null) {
					propertiesFile = System.getenv(ENV_PROPERTIES_PATH);
					if(propertiesFile==null) {
						propertiesResource = DEFAULT_PROPERTIES_RESOURCE;
						log.info("queryon resource config: "+propertiesResource+" [using DEFAULT_PROPERTIES_RESOURCE]");
					}
					else {
						log.info("queryon file config: "+propertiesFile+" [using env '"+ENV_PROPERTIES_PATH+"']");
					}
				}
				else {
					log.info("queryon file config: "+propertiesFile+" [using sysprop '"+SYSPROP_PROPERTIES_PATH+"']");
				}
			}
			else {
				log.info("queryon file config: "+propertiesFile+" [using "+INITP_PROPERTIES_FILE+"]");
			}
		}
		else {
			log.info("queryon resource config: "+propertiesResource+" [using "+INITP_PROPERTIES_RESOURCE+"]");
		}
	}
	
	void log4jInit() {
		String log4jres = "/log4j.properties";
		InputStream is = QueryOn.class.getResourceAsStream(log4jres);
		if(is!=null) {
			try {
				Class<?> c = Class.forName("org.apache.log4j.PropertyConfigurator");
				Method m = c.getMethod("configure", InputStream.class);
				m.invoke(null, is);
				log.info("log4j configured with '"+log4jres+"'");
			}
			catch(ClassNotFoundException e) {
				log.warn("Log4j not found: "+e);
			}
			catch(Exception e) {
				log.warn("Error initing log4j: "+e);
			}
			//PropertyConfigurator.configure(log4jProp);
		}
		else {
			log.info("log4j not configured ['"+log4jres+"' not found]");
		}
	}
	
	protected void doInit(ServletContext context/*, String propertiesResource, String propertiesFile*/) throws ServletException {
		String contextPath = null;
		try {
			prop.clear();
			context.removeAttribute(ATTR_INIT_ERROR);
			
			//XXX: protocol: add from ServletRequest?
			String protocol = "http://"; //XXX https protocol??
			//XXX: path: add host port (request - ServletRequest - object needed?)? servlet mapping url-pattern?
			String path = protocol + InetAddress.getLocalHost().getHostName().toLowerCase();
			contextPath = getServletContext().getContextPath();
			String rdfBase = path +
					((!path.endsWith("/") && (!contextPath.startsWith("/"))?"/":"")) +
					contextPath;
			// scheme://domain:port/path?query_string#fragment_id - http://en.wikipedia.org/wiki/Uniform_resource_locator
			//String path = "http://"+InetAddress.getLocalHost().getHostName()+"/"+getServletContext().getContextPath();
			prop.setProperty(PROP_CONTEXT_PATH, contextPath);
			prop.setProperty(RDFAbstractSyntax.PROP_RDF_BASE, rdfBase);
			log.info("path= ["+path+"] "+PROP_CONTEXT_PATH+"= ["+contextPath+"] ; "+RDFAbstractSyntax.PROP_RDF_BASE+"= ["+rdfBase+"]");
			
			prop.load(QueryOn.class.getResourceAsStream(DEFAULT_PROPERTIES_VALUES_RESOURCE));
			
			if(propertiesResource!=null) {
				log.info("loading properties: "+propertiesResource);
				prop.load(QueryOn.class.getResourceAsStream(propertiesResource));
			}
			else if(propertiesFile!=null) {
				log.info("loading properties file: "+propertiesFile);
				prop.load(new FileInputStream(propertiesFile));
			}
			else {
				log.warn("no properties config loaded");
			}

			DumpSyntaxRegistry.setSyntaxesResource(DEFAULT_SYNTAXES_RESOURCE);
			DumpSyntaxRegistry.addSyntaxes(prop.getProperty(PROP_XTRASYNTAXES, DEFAULT_XTRA_SYNTAXES));
			log.info("syntaxes: "+StringUtils.getClassSimpleNameListT( DumpSyntaxRegistry.getSyntaxes()) );
			
			Map<String, SchemaModel> models = new LinkedHashMap<String, SchemaModel>();
			List<String> modelIds = getDeclaredModels();
			if(modelIds!=null) {
				log.info("modelIds="+modelIds);
				List<String> modelsGrabbed = new ArrayList<String>();
				for(String id: modelIds) {
					if(id==null || id.trim().isEmpty() || id.equals("null")) {
						String msg = "invalid model id: '"+id+"'";
						log.warn(msg);
						throw new ServletException(msg);
					}
					else {
						String modelWarningsKey = id+"."+ATTR_INIT_ERROR;
						if( grabModel(models, id) ) {
							modelsGrabbed.add(id);
							context.removeAttribute(modelWarningsKey);
						}
						else {
							context.setAttribute(modelWarningsKey, "Error grabbing model '"+id+"'");
						}
					}
				}
				//log.info("modelsGrabbed="+modelsGrabbed);
				String defaultModel = prop.getProperty(PROP_MODELS_DEFAULT);
				if(defaultModel==null) {
					if(modelIds.size()>1) {
						String msg = "multi-model instance must define a default model [prop '"+PROP_MODELS_DEFAULT+"']";
						log.warn(msg);
						throw new ServletException(msg);
					}
					defaultModel = modelIds.get(0);
				}
				if(!modelsGrabbed.contains(defaultModel)) {
					String msg = "unknown default model '"+defaultModel+"'";
					log.warn(msg);
					defaultModel = null;
					throw new ServletException(msg);
				}
				/*if(defaultModel==null && modelsGrabbed.size()>2) {
					defaultModel = modelsGrabbed.get(0);
					log.warn("null default model [prop '"+PROP_MODELS_DEFAULT+"'], defaultModel set to '"+defaultModel+"'");
				}*/
				context.setAttribute(ATTR_DEFAULT_MODEL, defaultModel);
				log.info("defaultmodel="+defaultModel+" [grabbed: "+modelsGrabbed+"]");
			}
			else {
				String defaultModel = null; // XXX: use DEFAULT_MODELID? 
				grabModel(models, defaultModel);
				context.setAttribute(ATTR_DEFAULT_MODEL, defaultModel);
				log.info("defaultmodel="+defaultModel+" [single-model]");
			}
			//log.debug("charset: "+Charset.defaultCharset());
			context.setAttribute(ATTR_MODEL_MAP, models);
			servletContext = context;
			//model = SchemaModelUtils.getDefaultModel(context);
			dsutils = new DumpSyntaxUtils(prop);
			
			initFromProperties();
			
			context.setAttribute(ATTR_PROP, prop);
			context.setAttribute(ATTR_DUMP_SYNTAX_UTILS, dsutils);
			
			Map<String, List<String>> schemasByModel = new HashMap<String, List<String>>();
			context.setAttribute(ATTR_SCHEMAS_MAP, schemasByModel);
			
			setupUpdatePlugins(context);
			
			runOnStartupProcessors(context);
			
			initModelsMetadata(models);
		} catch (Exception e) {
			String message = e.toString()+" [prop resource: "+propertiesResource+"][contextPath: "+contextPath+"][servlet: "+this.getClass().getSimpleName()+"]";
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
	
	protected List<String> getDeclaredModels() {
		return Utils.getStringListFromProp(prop, PROP_MODELS, ",");
	}
	
	protected void initFromProperties() {
		//log.debug("quote:: "+DBMSResources.instance().getIdentifierQuoteString());
		validateFilterColumnNames = Utils.getPropBool(prop, PROP_VALIDATE_FILTERCOLNAME, validateFilterColumnNames);
		
		SQL.validateOrderColumnNames = Utils.getPropBool(prop, PROP_VALIDATE_ORDERCOLNAME, SQL.validateOrderColumnNames);
		
		//SQL.sqlIdDecorator = new StringDecorator.StringQuoterDecorator(DBMSResources.instance().getIdentifierQuoteString());
		boolean useIdDecorator = Utils.getPropBool(prop, PROP_SQL_USEIDDECORATOR, true);
		if(!useIdDecorator) {
			log.warn("sqlIdDecorator: will not use decorator [prop "+PROP_SQL_USEIDDECORATOR+"]");
			SQL.sqlIdDecorator = new StringDecorator.StringQuoterDecorator("");
		}
		
		// init date formats
		SQL.initDateFormats(dsutils);
		
		//xSetRequestUtf8 = Utils.getPropBool(prop, PROP_X_REQUEST_UTF8, xSetRequestUtf8);
		
		defaultLimit = Utils.getPropInt(prop, QueryOn.PROP_DEFAULT_LIMIT);
		maxLimit = Utils.getPropInt(prop, QueryOn.PROP_MAX_LIMIT, RequestSpec.DEFAULT_LIMIT);
		
		SQLUtils.setProperties(prop);
	}
	
	boolean grabModel(Map<String, SchemaModel> models, String id) {
		try {
			models.put(id, modelGrabber(prop, id));
			return true;
		}
		catch(Exception e) {
			log.warn("Error initting model '"+id+"': "+e);
			log.debug("Error initting model '"+id+"': "+e.getMessage(), e);
		}
		return false;
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

	void setupUpdatePlugins(ServletContext context) {
		updatePlugins.clear();
		List<String> defaultUpdatePluginsStrList = Utils.getStringListFromProp(prop, PROP_UPDATE_PLUGINS, ",");
		
		Map<String, SchemaModel> models = SchemaModelUtils.getModels(context);
		for(Map.Entry<String,SchemaModel> entry: models.entrySet()) {
			String modelId = entry.getKey();
			
			List<String> updatePluginsStrList = Utils.getStringListFromProp(prop, "queryon."+modelId+".update-plugins", ",");
			if(updatePluginsStrList==null) {
				updatePluginsStrList = defaultUpdatePluginsStrList;
			}
			
			SchemaModel sm = entry.getValue();
			List<UpdatePlugin> plugins = setupUpdatePlugins(context, sm, modelId, updatePluginsStrList);
			updatePlugins.put(modelId, plugins);
		}
		
		context.setAttribute(ATTR_UPDATE_PLUGINS, updatePlugins);
	}
	
	List<UpdatePlugin> setupUpdatePlugins(ServletContext context, SchemaModel model, String modelId, List<String> updatePluginList) {
		if(updatePluginList==null) { return null; }

		List<UpdatePlugin> plugins = new ArrayList<UpdatePlugin>();
		List<String> updatePluginsStr = new ArrayList<String>();
		List<UpdatePlugin> activePlugins = new ArrayList<UpdatePlugin>();
		
		for(String upPluginStr: updatePluginList) {
			UpdatePlugin up = (UpdatePlugin) Utils.getClassInstance(upPluginStr, DEFAULT_CLASSLOADING_PACKAGES);
			up.setSchemaModel(model);
			up.setModelId(modelId);
			up.setProperties(prop);
			plugins.add(up);
			updatePluginsStr.add(up.getClass().getSimpleName());
		}
		
		log.info("initting update-plugins"+(modelId!=null?" [model="+modelId+"]":"")+": "+updatePluginsStr);

		String warnKey = UpdatePluginUtils.ATTR_INIT_WARNINGS_PREFIX+"."+modelId;
		
		try {
			Connection conn = DBUtil.initDBConn(prop, modelId, model);
			UpdatePluginUtils.clearWarnings(context, warnKey);
			
			for(UpdatePlugin up: plugins) {
				try {
					up.setConnection(conn);
					up.onInit(context);
					activePlugins.add(up);
				}
				catch(RuntimeException e) {
					String message = "Exception starting update-plugin "+up.getClass().getSimpleName()+" [model="+modelId+"]";
					log.warn(message+": "+e);
					log.debug(message+": "+e, e);
					UpdatePluginUtils.putWarning(servletContext, warnKey, null, "[init]", message);
				}
			}
			
			ConnectionUtil.closeConnection(conn);
		}
		catch(Exception e) {
			String message = "Exception starting update-plugin [model="+modelId+"]: "+e;
			log.warn(message);
			log.info(message, e);
			UpdatePluginUtils.putWarning(servletContext, warnKey, null, "[init]", message);
		}

		log.info("active update-plugins"+(modelId!=null?" [model="+modelId+"]":"")+": "+StringUtils.getClassSimpleNameListFromObjectList(activePlugins));
		
		return activePlugins;
	}
	
	void runOnStartupProcessors(ServletContext context) {
		//XXXdone option to reload properties & re-execute processors? see: manage/reload
		List<String> procsOnStartupDefault = Utils.getStringListFromProp(prop, PROP_PROCESSORS_ON_STARTUP, ",");
		Map<String, SchemaModel> models = SchemaModelUtils.getModels(context);
		for(Map.Entry<String,SchemaModel> entry: models.entrySet()) {
			String modelId = entry.getKey();
			
			List<String> procsOnStartupList = Utils.getStringListFromProp(prop, "queryon."+modelId+".processors-on-startup", ",");
			if(procsOnStartupList==null) {
				procsOnStartupList = procsOnStartupDefault;
			}
			
			if(procsOnStartupList!=null) {
				for(String proc: procsOnStartupList) {
					runProcessor(proc, context, modelId);
				}
			}
		}
	}
	
	void runProcessor(String processorClass, ServletContext context, String modelId) {
		try {
			log.info("Running startup processor '"+processorClass+"' ["+modelId+"]");
			ProcessorServlet.doProcess(processorClass, context, modelId);
		}
		catch(Exception e) {
			log.warn("Exception executing processor on startup [proc="+processorClass+"; model="+modelId+"]: "+e);
			log.debug("Exception executing processor on startup [proc="+processorClass+"; model="+modelId+"]: "+e.getMessage(), e);
			//XXX: fail on error?
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
		try {
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
		}
		return sm;
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}

	protected void doFacade(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			doService(req, resp);
		}
		catch(InternalServerException e) {
			handleException(req, resp, e);
		}
		catch(ForbiddenException e) {
			handleException(req, resp, e);
			// XXX if currentUser not authenticated, throw 401/Unauthorized ?
			//int status = e.isAuthenticated() ? e.getCode() : HttpServletResponse.SC_UNAUTHORIZED;
			//WebUtils.writeException(resp, e, status, debugMode);
		}
		catch(BadRequestException e) {
			handleException(req, resp, e);
		}
		catch(RuntimeException e) {
			//e.printStackTrace();
			throw new ServletException(e);
		}
		catch(ServletException e) {
			//e.printStackTrace();
			throw e;
		}
	}
	
	protected void handleException(HttpServletRequest req, HttpServletResponse resp, BadRequestException e) throws IOException {
		WebUtils.writeException(req, resp, e, debugMode);
	}
	
	protected RequestSpec getRequestSpec(HttpServletRequest req) throws ServletException, IOException {
		return new RequestSpec(dsutils, req, prop);
	}
	
	protected void doService(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		//log.info(">> pathInfo: "+req.getPathInfo()+" ; method: "+req.getMethod());
		
		try {

			/*if(xSetRequestUtf8) {
				try {
					String origCharset = req.getCharacterEncoding();
					log.debug("setting request encoding to UTF-8 [was: "+origCharset+"]");
					req.setCharacterEncoding(UTF8);
					//resp.setCharacterEncoding(UTF8);
				} catch (UnsupportedEncodingException e) {
					log.warn("Exception on setCharacterEncoding: "+e.getMessage(), e);
				}
			}*/
			
			RequestSpec reqspec = getRequestSpec(req);
			log.info(">> pathInfo: "+req.getPathInfo()+" ; method: "+req.getMethod()+
					( (reqspec.httpMethod!=null && !reqspec.httpMethod.equals(req.getMethod())?" ; final-method: "+reqspec.httpMethod:"") )
					);
			//XXX app-specific xtra parameters, like auth properties? app should extend QueryOn & implement addXtraParameters
			
			SchemaModel model = SchemaModelUtils.getModel(req.getServletContext(), reqspec.getModelId());
			if(model==null) {
				throw new InternalServerException("null model [modelId="+reqspec.getModelId()+"]");
			}
			
			final String otype;
			final ActionType atype; //XXX: add ActionType to RequestSpec
			final DBIdentifiable dbobj;
			//StatusObject sobject = StatusObject.valueOf(reqspec.object)
			//XXX should status object names have special syntax? like meta:table, meta:fk
			
			boolean isStatusObject = isStatusObject(reqspec.object);
			//DBObjectType statusType = null;
			if(isStatusObject) {
				//if(statusType!=null && Arrays.asList(STATUS_OBJECTS).contains(statusType)) { //test if STATUS_OBJECTS contains statusType ?
				atype = ActionType.STATUS;
				//statusType = statusObject(reqspec.object);
				otype = reqspec.object.toUpperCase();
				dbobj = null;
			}
			else {
				ActionType atypeTmp = getPrivilegedAction(reqspec.object, reqspec.httpMethod);
				if(atypeTmp!=null) {
					atype = atypeTmp;
					otype = atype.name();
					dbobj = null;
				}
				else {
					dbobj = SchemaModelUtils.getDBIdentifiableBySchemaAndName(model, reqspec);
					if(dbobj==null) {
						throw new NotFoundException("object not found: "+reqspec.object);
					}
					atype = getActionType(reqspec, dbobj);
					if(atype.equals(ActionType.EXECUTE)) {
						otype = DBObjectType.EXECUTABLE.name();
					}
					else {
						otype = QueryOn.getObjectType(dbobj);
					}
				}
			}
		
			//log.info("atype: "+atype+" ; otype: "+otype);
			Subject currentUser = ShiroUtils.getSubject(prop, req);
			Connection conn = null;
			
			//log.info("is shiro enabled? [shiroEnabled="+isShiroEnabled()+";atype="+atype+"]");
			//ShiroUtils.checkPermission(currentUser, otype+":"+atype, reqspec.object);
			boolean permitted = isPermitted(currentUser, otype, atype, reqspec.object);
			if(!permitted) {
				if( (atype==ActionType.UPDATE || atype == ActionType.INSERT) && validateUpdateColumnPermissions) {
					// should validate columns on doInsert/doUpdate
				}
				else {
					ShiroUtils.throwPermissionException(currentUser, otype+":"+atype, reqspec.object);
				}
			}
			switch (atype) {
			case SELECT: {
				Relation rel = (Relation) dbobj;
				if(rel==null) {
					log.warn("strange... rel is null");
					rel = SchemaModelUtils.getRelation(model, reqspec, true); //XXX: option to search views based on property?
				}
				if(currentUser!=null && ! ShiroUtils.isPermitted(currentUser, doNotCheckGrantsPermission)) {
					checkGrantsAndRolesMatches(currentUser, PrivilegeType.SELECT, rel);
				}
				doSelect(model, rel, reqspec, currentUser, resp, false);
				}
				break;
			case SELECT_ANY:
				try {
					conn = DBUtil.initDBConn(prop, reqspec.getModelId());
					Query relation = getQuery(req, reqspec, currentUser, conn);
					resp.addHeader(ResponseSpec.HEADER_CONTENT_DISPOSITION, "attachment; filename=queryon_"
						+relation.getName() //XXX add parameter values? filters? -- ,maybe filters is too much
						+"."+reqspec.outputSyntax.getDefaultFileExtension());
					
					boolean sqlCommandExecuted = trySqlCommand(relation, conn, reqspec, resp);
					if(!sqlCommandExecuted) {
						DBObjectUtils.updateQueryParameters(relation, conn);
						doSelect(model, relation, reqspec, currentUser, conn, resp, true);
					}
				}
				catch(RuntimeException e) {
					throw new BadRequestException(e.getMessage(), e);
				}
				catch(SQLException e) {
					throw new BadRequestException(e.getMessage(), e);
				}
				finally {
					ConnectionUtil.closeConnection(conn);
				}
				break;
			case VALIDATE_ANY:
				try {
					conn = DBUtil.initDBConn(prop, reqspec.getModelId());
					Query relation = getQuery(req, reqspec, currentUser, conn);
					doValidate(relation, reqspec, currentUser, conn, resp);
				}
				catch(RuntimeException e) {
					throw new BadRequestException(e.getMessage(), e);
				}
				catch(SQLException e) {
					throw new BadRequestException(e.getMessage(), e);
				}
				finally {
					ConnectionUtil.closeConnection(conn);
				}
				break;
			case EXPLAIN_ANY:
				try {
					conn = DBUtil.initDBConn(prop, reqspec.getModelId());
					Query relation = getQuery(req, reqspec, currentUser, conn);
					DBObjectUtils.updateQueryParameters(relation, conn);
					doExplain(relation, reqspec, currentUser, conn, resp);
				}
				catch(RuntimeException e) {
					throw new BadRequestException(e.getMessage(), e);
				}
				catch(SQLException e) {
					throw new BadRequestException(e.getMessage(), e);
				}
				finally {
					ConnectionUtil.closeConnection(conn);
				}
				break;
			case SQL_ANY:
				try {
					conn = DBUtil.initDBConn(prop, reqspec.getModelId());
					Query relation = getQuery(req, reqspec, currentUser, conn);
					
					boolean sqlCommandExecuted = trySqlCommand(relation, conn, reqspec, resp);
					if(!sqlCommandExecuted) {
						DBObjectUtils.updateQueryParameters(relation, conn);
						doSql(model, relation, reqspec, currentUser, conn, resp);
					}
				}
				catch(RuntimeException e) {
					throw new BadRequestException(e.getMessage(), e);
				}
				catch(SQLException e) {
					throw new BadRequestException(e.getMessage(), e);
				}
				finally {
					ConnectionUtil.closeConnection(conn);
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
				doInsert((Relation) dbobj, reqspec, currentUser, permitted, resp);
				}
				break;
			case UPDATE: {
				doUpdate((Relation) dbobj, reqspec, currentUser, permitted, resp);
				}
				break;
			case DELETE: {
				doDelete((Relation) dbobj, reqspec, currentUser, resp);
				}
				break;
			case STATUS:
				doStatus(model, otype, reqspec, currentUser, resp);
				break;
			case MANAGE:
				doManage(model, reqspec, req, resp);
				break;
			default:
				throw new BadRequestException("Unknown action type: "+atype); 
			}
			postService(model, reqspec, req, resp);
		}
		catch(InternalServerException e) {
			//log.warn(e, e);
			log.warn(e.getClass().getSimpleName()+" ["+e.getCode()+"]: "+e.getMessage());
			//log.debug(e.getClass().getSimpleName()+" ["+e.getCode()+"]: "+e.getMessage(), e);
			throw e;
		}
		catch(BadRequestException e) {
			//XXX: do not log exception!
			log.warn(e.getClass().getSimpleName()+" ["+e.getCode()+"]: "+e.getMessage());
			//log.debug(e.getClass().getSimpleName()+" ["+e.getCode()+"]: "+e.getMessage(), e);
			throw e;
		}
		catch(SQLException e) {
			//log.warn(e, e);
			log.warn(e.getClass().getSimpleName()+" [SQLException]: "+e.getMessage());
			//log.debug(e.getClass().getSimpleName()+" [SQLException]: "+e.getMessage(), e);
			throw new InternalServerException(e.getClass().getSimpleName()+" [SQLException]: "+e.getMessage(), e);
		}
		catch(IOException e) {
			throw new ServletException(e);
		}
		catch(ClassNotFoundException e) {
			throw new ServletException(e);
		}
		catch(NamingException e) {
			log.warn(e.getClass().getSimpleName()+" [NamingException]: "+e.getMessage(), e);
			throw new InternalServerException(e.getClass().getSimpleName()+" [NamingException]: "+e.getMessage(), e);
			//throw new ServletException(e);
		}
		catch(IntrospectionException e) {
			throw new ServletException(e);
		}
		catch(Throwable e) {
			throw new ServletException(e);
		}
	}
	
	ActionType getPrivilegedAction(String object, String httpMethod) {
		if(ACTION_QUERY_ANY.equals(object)) {
			if(! METHOD_POST.equals(httpMethod)) {
				// XXX use 405: SC_METHOD_NOT_ALLOWED?
				throw new BadRequestException(object+": method must be POST");
			}
			return ActionType.SELECT_ANY;
		}
		if(ACTION_VALIDATE_ANY.equals(object)) {
			if(! METHOD_POST.equals(httpMethod)) {
				throw new BadRequestException(object+": method must be POST");
			}
			return ActionType.VALIDATE_ANY;
		}
		if(ACTION_EXPLAIN_ANY.equals(object)) {
			if(! METHOD_POST.equals(httpMethod)) {
				throw new BadRequestException(object+": method must be POST");
			}
			return ActionType.EXPLAIN_ANY;
		}
		if(ACTION_SQL_ANY.equals(object)) {
			if(! METHOD_POST.equals(httpMethod)) {
				throw new BadRequestException(object+": method must be POST");
			}
			return ActionType.SQL_ANY;
		}
		if(ActionType.MANAGE.name().toLowerCase().equals(object)) {
			return ActionType.MANAGE;
		}
		return null;
	}
	
	protected ActionType getActionType(RequestSpec reqspec, DBIdentifiable dbobj) {
		if(dbobj instanceof Relation) {
			return getRelationActionType(reqspec.httpMethod);
		}
		if(dbobj instanceof ExecutableObject) {
			//XXX only if POST method? GET if there are no side-effects? DETERMINISTIC?
			if(!reqspec.httpMethod.equals(METHOD_POST) && !reqspec.httpMethod.equals(METHOD_GET)) {
				throw new BadRequestException("unknown http method: "+reqspec.httpMethod+" [Executable object]");
			}
			return ActionType.EXECUTE;
		}
		throw new BadRequestException("unknown object type: "+dbobj.getClass().getName()+" [obj="+dbobj.getName()+"]");
	}
	
	ActionType getRelationActionType(String httpMethod) {
		if(httpMethod.equals(METHOD_GET)) {
			return ActionType.SELECT;
		}
		if(httpMethod.equals(METHOD_HEAD)) {
			return ActionType.SELECT;
		}
		if(httpMethod.equals(METHOD_POST)) {
			return ActionType.INSERT; //upsert?
		}
		if(httpMethod.equals(METHOD_PATCH)) {
			//XXXdone: PUT should be idempotent ... maybe should be used for INSERT? use PATCH instead of PUT?
			return ActionType.UPDATE;
		}
		if(httpMethod.equals(METHOD_PUT)) {
			// for backward compatibility
			return ActionType.UPDATE;
		}
		else if(httpMethod.equals(METHOD_DELETE)) {
			return ActionType.DELETE;
		}
		else {
			throw new BadRequestException("unknown http method: "+httpMethod+" [Relation object]"); //+" [obj="+dbobj.getName()+"]");
		}
	}
	
	public static void checkGrantsAndRolesMatches(Subject subject, PrivilegeType privilege, Relation rel) {
		boolean check = grantsAndRolesMatches(subject, privilege, rel.getGrants());
		if(!check) {
			String schema = rel.getSchemaName();
			throw new ForbiddenException("no "+privilege+" permission on "+(schema!=null?schema+".":"")+rel.getName(), subject.isAuthenticated());
		}
	}

	public static void checkGrantsAndRolesMatches(Subject subject, PrivilegeType privilege, ExecutableObject eo) {
		boolean check = grantsAndRolesMatches(subject, privilege, eo.getGrants());
		if(!check) {
			throw new ForbiddenException("no "+privilege+" permission on "+eo.getQualifiedName(), subject.isAuthenticated());
		}
	}
	
	public static boolean grantsAndRolesMatches(Subject subject, PrivilegeType privilege, List<Grant> grants) {
		List<Grant> filteredGrants = QOnModelUtils.filterGrantsByPrivilegeType(grants, privilege);
		if(filteredGrants==null || filteredGrants.size()==0) {
			return true;
		}
		Set<String> roles = ShiroUtils.getSubjectRoles(subject);
		//log.info("grantsAndRolesMatches:: privilege: "+privilege+" / filteredGrants: "+filteredGrants);
		//log.info("grantsAndRolesMatches:: roles: "+roles);
		Set<String> mustHaveRoles = new HashSet<String>();
		for(Grant grant: filteredGrants) {
			if(roles.contains(grant.getGrantee())) {
				return true;
			}
			mustHaveRoles.add(grant.getGrantee());
		}
		log.debug("grantsAndRolesMatches: user must have any of "+mustHaveRoles+" roles but only has "+roles+" roles [privilege="+privilege+"]");
		
		return false;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doFacade(req, resp);
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doFacade(req, resp);
	}

	//@Override
	protected void doPatch(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doFacade(req, resp);
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doFacade(req, resp);
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doFacade(req, resp);
	}
	
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String method = req.getMethod();
		if (method.equals(METHOD_PATCH)) {
			doPatch(req, resp);
		}
		else {
			super.service(req, resp);
		}
	}

	/*
	protected Query getQuery(HttpServletRequest req, RequestSpec reqspec) throws SQLException {
		return getQuery(req, reqspec, false, null);
	}

	protected Query getQuery(HttpServletRequest req, RequestSpec reqspec, boolean validate, Connection conn) throws SQLException {
		//return getQuery(req, reqspec, true, conn);
		// ...
	}
	
	protected Query getQuery(HttpServletRequest req, RequestSpec reqspec, Subject currentUser, Connection conn) throws SQLException {
		return getQuery(req, reqspec, currentUser, conn, true);
	}
	*/
	
	// XXX should use RequestSpec for parameters?
	protected Query getQuery(HttpServletRequest req, RequestSpec reqspec, Subject currentUser, Connection conn) throws SQLException {
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
		//XXXxx: validate first & return number of parameters?
		//relation.setParameterCount( reqspec.params.size() ); //maybe not good... anyway (would need connection to validate SQL)
		//String finalSql = SQL.getFinalSqlNoUsername(sql);
		/*if(validateParameters) {
			//SQL sqlz = null;
			try {
				//sqlz = SQL.createSQL(relation, reqspec, getUsername(currentUser), validateParameters);
				//DBObjectUtils.validateQueryParameters(relation, sqlz.getFinalSql(), conn, true);
				//DBObjectUtils.validateQueryParameters(relation, relation.getQuery(), conn, true);
				DBObjectUtils.validateQueryParameters(relation, sqlz.getFinalSql(), conn, true);
			}
			catch(SQLException e) {
				setSqlInfo(req, sqlz);
				log.warn("Error validating query: "+e);
				log.info("Error validating query: sqlz.getFinalSql()=="+sqlz.getFinalSql());
				throw e;
			}
		}*/
		
		return relation;
	}
	
	public static SQL getSelectQuery(Relation relation, RequestSpec reqspec, Constraint pk, LimitOffsetStrategy loStrategy,
			String username, Integer defaultLimit, int maxLimit, HttpServletResponse resp, boolean strictMode) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		
		SQL sql = SQL.createSQL(relation, reqspec, username);
		
		// sets named parameters
		reqspec.setNamedParameters(sql);
		
		// add parameters for Query
		sql.addOriginalParameters(reqspec);
		
		filterByKey(relation, reqspec, pk, sql);

		// TODO parameters: remove reqspec.params in excess of #parametersToBind ?

		// xtra filters
		List<String> warnings = filterByXtraParams(relation, reqspec, sql);
		if(warnings!=null && warnings.size()>0 && resp!=null) {
			String warns = Utils.join(warnings, ", ");
			resp.addHeader(ResponseSpec.HEADER_WARNING_UNKNOWN_COLUMN, warns);
			if(strictMode) {
				throw new BadRequestException(warns);
			}
		}
		
		//XXX app-specific xtra filters, like auth filters? app should extend QueryOn & implement addXtraConstraints
		//appXtraConstraints(relation, sql, reqspec, req);
		
		//XXX: apply order or projection first? order last seems more natural...
		
		// projection (select columns) - also adds 'distinct' if requested
		sql.applyProjection(reqspec, relation);
		
		// group by
		sql.applyGroupByOrAggregate(reqspec);

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
			log.debug("pre-LimitOffsetStrategy ["+loStrategy+"] sql:\n"+sql.getSql());
		}
		//int limit = (sql.limitMax!=null && sql.limitMax < reqspec.limit) ? sql.limitMax : reqspec.limit;
		int finalMaxLimit = maxLimit;
		if(sql.limitMax!=null) {
			finalMaxLimit = Math.min(finalMaxLimit, sql.limitMax);
		}
		//log.info("finalMaxLimit="+finalMaxLimit+" ; sql.limitMax="+sql.limitMax+" ; defaultLimit="+defaultLimit);
		sql.addLimitOffset(loStrategy, getLimit(sql.limit, defaultLimit, finalMaxLimit), reqspec.offset);
		
		sql.applyCount(reqspec);
		
		//query finished!
		
		return sql;
		//log.info("sql:\n"+sql);
	}
	
	ResultSet pivotResultSet(final ResultSet rs, Relation relation, SQL sql, RequestSpec reqspec, HttpServletResponse resp) throws SQLException {
		boolean hasMeasures = reqspec.aggregate.size() > 0 || (reqspec.columns.size() > reqspec.oncols.size() + reqspec.onrows.size());
		int pivotFlags = reqspec.pivotflags!=null?reqspec.pivotflags:
			hasMeasures?RequestSpec.DEFAULT_PIVOTFLAGS_WITH_MEASURES:RequestSpec.DEFAULT_PIVOTFLAGS_WITHOUT_MEASURES;
		
		try {
			PivotResultSet prs = new PivotResultSet(rs, reqspec.onrows, reqspec.oncols, true, pivotFlags);
			//log.info("reqspec.onrows="+reqspec.onrows+" ;; reqspec.oncols="+reqspec.oncols);
			if(log.isDebugEnabled()) {
				String colTypes = Utils.join(DataDumpUtils.getResultSetColumnsTypes(rs.getMetaData()), ";\n\t- ");
				log.debug("PivotResultSet: cols ["+relation.getQualifiedName()+"]:\n\t- "+colTypes);
			}
			int originalColCount = rs.getMetaData().getColumnCount();
			log.info("PivotResultSet: rowCount: "+prs.getRowCount()+" ; colCount: "+prs.getMetaData().getColumnCount()+"; "+
					"originalRowCount: "+prs.getOriginalRowCount()+" ; originalColCount: "+originalColCount+" ; "+
					"flags: "+pivotFlags);//+" ; nonPivotKeysCount: "+prs.getNonPivotKeysCount());
			if(sql.applyedLimit!=null && prs.getOriginalRowCount()==sql.applyedLimit) {
				String message = "Pivot Query data limited to "+prs.getOriginalRowCount()+" rows";
				resp.addHeader(ResponseSpec.HEADER_WARNING, message);
				log.debug(message);
			}
			return prs;
		}
		catch(RuntimeException e) {
			throw new BadRequestException(e.getMessage(), e);
		}
	}
		
	protected void doSelect(SchemaModel model, Relation relation, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp, boolean validateQuery) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		try {
			doSelect(model, relation, reqspec, currentUser, conn, resp, validateQuery);
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}
	
	/*protected void doSelect(SchemaModel model, Relation relation, RequestSpec reqspec, Subject currentUser, Connection conn, HttpServletResponse resp, boolean validateQuery) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		LimitOffsetStrategy loStrategy = LimitOffsetStrategy.getDefaultStrategy(model.getSqlDialect());
		doSelect(model, relation, reqspec, loStrategy, currentUser, conn, resp, validateQuery);
	}*/
	
	protected void doSelect(SchemaModel model, Relation relation, RequestSpec reqspec/*, LimitOffsetStrategy loStrategy*/, Subject currentUser, Connection conn, HttpServletResponse resp, boolean validateQuery) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		if(relation.getName()==null) {
			throw new BadRequestException("select: relation name must not be null");
		}
		
		String finalSql = null;
		SQL sql = null;
		try {
			
		if(log.isDebugEnabled()) {
			ConnectionUtil.showDBInfo(conn.getMetaData());
		}
		
		Constraint pk = SchemaModelUtils.getPK(relation);
		LimitOffsetStrategy loStrategy = LimitOffsetStrategy.getDefaultStrategy(model.getSqlDialect());
		boolean fullKeyDefined = fullKeyDefined(reqspec, pk);
		
		preprocessParameters(reqspec, relation, pk);
		sql = getSelectQuery(relation, reqspec, pk, loStrategy, getUsername(currentUser), defaultLimit, maxLimit, resp, isStrictMode());
		finalSql = sql.getFinalSql();
		
		if(validateQuery) {
			if(relation instanceof Query) {
				try {
					DBObjectUtils.validateQuery((Query)relation, finalSql, conn, true);
				}
				catch(SQLException e) {
					log.warn("error validating query '"+relation+"': "+finalSql);
				}
			}
			else {
				log.warn("relation '"+relation+"' not a query: can't validate");
			}
		}
		log.debug("sql:\n"+finalSql);
		/*
		if(reqspec.isHeadMethod()) {
			log.info("HEAD method: response body is empty"); 
			return;
		}
		*/
		
		PreparedStatement st = conn.prepareStatement(finalSql);
		//st.setFetchSize(limit+1); //XXX ?
		sql.bindParameters(st);
		
		//boolean applyLimitOffsetInResultSet = loStrategy==LimitOffsetStrategy.RESULTSET_CONTROL;
		//boolean applyLimitOffsetInResultSet = !sql.allowEncapsulation;
		boolean applyLimitOffsetInResultSet = !sql.sqlLoEncapsulated;

		/*if(applyLimitOffsetInResultSet) {
			//XXX only if (offset == 0)? or should also use ResultSet.relative(offset)?
			st.setMaxRows(sql.limit);
		}*/
		ResultSet rs = st.executeQuery();
		
		if(reqspec.oncols.size()>0 || reqspec.onrows.size()>0) {
			rs = pivotResultSet(rs, relation, sql, reqspec, resp);
		}
		
		List<FK> fks = ModelUtils.getImportedKeys(relation, model.getForeignKeys());
		List<Constraint> uks = ModelUtils.getUKs(relation);
		
		//XXX incompatible with cache-control?
		if(Utils.getPropBool(prop, PROP_HEADERS_ADDCONTENTLOCATION, false)) {
			String contentLocation = reqspec.getCanonicalUrl(prop);
			//log.info("content-location header: "+contentLocation);
			reqspec.setAttribute(RequestSpec.ATTR_CONTENTLOCATION, contentLocation);
		}
		
		if(reqspec.uniValueCol!=null) {
			dumpBlob(rs, reqspec, relation.getName(), applyLimitOffsetInResultSet, resp);
		}
		else {
			Integer lim = MathUtil.minIgnoreNull(sql.limit, sql.limitMax);

			Properties sqlProps = SQL.processSqlParameterProperties(finalSql, reqspec.outputSyntax);
			if(sqlProps!=null && sqlProps.size()>0) {
				//Properties modifiedProps = new ParametrizedProperties();
				//modifiedProps.putAll(prop);
				//modifiedProps.putAll(sqlProps);
				//modifiedProps.putAll(reqspec.syntaxSpecificProperties);
				Properties modifiedProps = MiscUtils.mergeProperties(prop, sqlProps, reqspec.syntaxSpecificProperties);
				reqspec.outputSyntax.procProperties(modifiedProps);
				//log.info("["+reqspec.outputSyntax.getSyntaxId()+"] sql props: "+sqlProps);
			}
			
			dumpResultSet(rs, reqspec, relation.getSchemaName(), relation.getName(), pk!=null?pk.getUniqueColumns():null,
					fks, uks, fullKeyDefined, applyLimitOffsetInResultSet, resp, getLimit(lim));
		}
		
		}
		catch(SQLException e) {
			DBUtil.doRollback(conn);
			log.warn("exception in 'doSelect': "+e+" ; sql:\n"+finalSql);
			setSqlInfo(reqspec.request, sql);
			//XXX: create new SQLException including the query string? throw BadRequestException? InternalServerException?
			//throw new InternalServerException("Exception in 'doSelect': "+e, e);
			//e.printStackTrace();
			throw e;
		}
	}
	
	protected void preprocessParameters(RequestSpec reqspec, Relation relation, Constraint pk) {}

	void doSql(SchemaModel model, Query relation, RequestSpec reqspec, Subject currentUser, Connection conn, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		if(relation.getName()==null) {
			throw new BadRequestException("sql: relation name must not be null");
		}
		
		SQL sql = null;
		String finalSql = null;
		try {
			if(log.isDebugEnabled()) {
				ConnectionUtil.showDBInfo(conn.getMetaData());
			}
			
			sql = SQL.createSQL(relation, reqspec, getUsername(currentUser), false);
			try {
				DBObjectUtils.validateQueryParameters(relation, sql.getFinalSql(), conn, true);
			}
			catch(SQLException e) {
				log.warn("error validating query '"+relation+"': "+finalSql);
			}
			
			sql.addOriginalParameters(reqspec);
			
			finalSql = sql.getFinalSql();
			log.debug("sql:\n"+finalSql);
			PreparedStatement st = conn.prepareStatement(finalSql);
			sql.bindParameters(st);
			
			boolean applyLimitOffsetInResultSet = true; //!sql.sqlLoEncapsulated;
			
			ResultSet rs = null;
			boolean hasResultSet = st.execute();
			WebUtils.addSqlWarningsAsHeaders(st.getWarnings(), resp);
			
			if(hasResultSet) {
				rs = st.getResultSet();
			}
			
			if(hasResultSet) {
				if(reqspec.uniValueCol!=null) {
					dumpBlob(rs, reqspec, relation.getName(), applyLimitOffsetInResultSet, resp);
				}
				else {
					Integer lim = MathUtil.minIgnoreNull(sql.limit, sql.limitMax);
					dumpResultSet(rs, reqspec, relation.getSchemaName(), relation.getName(), null,
							null, null, false, applyLimitOffsetInResultSet, resp, getLimit(lim));
				}
			}
			else {
				int updateCount = st.getUpdateCount();
				//XXX validate commit?
				if(reqspec.maxUpdates!=null && updateCount > reqspec.maxUpdates) {
					throw new BadRequestException("Update count ["+updateCount+"] greater than update-max ["+reqspec.maxUpdates+"]");
				}
				
				DBUtil.doCommit(conn);
				writeUpdateCount(reqspec, resp, updateCount, "updated");
			}
		}
		catch(SQLException e) {
			DBUtil.doRollback(conn);
			log.warn("exception in 'doSql': "+e+" ; sql:\n"+finalSql);
			setSqlInfo(reqspec.request, sql);
			throw e;
		}
	}
	
	/*
	 * XXX: option to select different validate strategies (drivers may validate queries differently)
	 * - current impl
	 * - no stmt.getMetaData()
	 * - run query with limit of 0 or 1? set parameters with what? null? random?
	 */
	void doValidate(Query relation, RequestSpec reqspec, Subject currentUser, Connection conn, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		SQL sql = null;
		try {
			sql = SQL.createSQL(relation, reqspec, getUsername(currentUser), false);
			DBObjectUtils.validateQuery(relation, sql.getFinalSql(), conn, true);
			//DBObjectUtils.validateQueryParameters(relation, sql.getFinalSql(), conn, true);
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
			resp.setHeader(ResponseSpec.HEADER_VALIDATE_PARAMTYPES, Utils.join(paramsTypes, ","));
			resp.setHeader(ResponseSpec.HEADER_VALIDATE_NAMED_PARAMETER_NAMES, Utils.join(sql.namedParameters, ","));
			if(sql.bindNullOnMissingParameters!=null) {
				resp.setHeader(ResponseSpec.HEADER_VALIDATE_OPTIONAL_PARAMS, MiscUtils.joinBooleanArray(sql.bindNullOnMissingParameters, ","));
			}
			boolean doGetMetadata = Utils.getPropBool(prop, PROP_VALIDATE_GETMETADATA, true);
			if(doGetMetadata) {
				ResultSetMetaData rsmd = stmt.getMetaData(); // needed to *really* validate query (at least on oracle)
				if(rsmd==null) {
					//String message = "can't get resultset metadata of: "+sql.getFinalSql().trim();
					String message = "getMetaData() returned null: empty/invalid query? not a 'select'? sql:\n"+sql.getFinalSql().trim();
					log.warn(message);
					//throw new SQLException(message);
					//throw new BadRequestException(message);
					resp.setContentType(MIME_TEXT);
					//resp.getWriter().write("Statement successfully validated");
				}
				else {
					// dumping ResultSetMetaData as a ResultSet ;)
					ResultSet rs = new ResultSetMetadata2RsAdapter(rsmd);
					dumpResultSet(rs, reqspec, relation.getSchemaName(), relation.getName(),
							null, //pk!=null?pk.getUniqueColumns():null,
							null, null, //fks, uks,
							false, //applyLimitOffsetInResultSet,
							resp);
				}
			}
			else {
				resp.setContentType(MIME_TEXT);
				//resp.getWriter().write("Statement successfully validated");
			}
		}
		catch(SQLException e) {
			log.info("doValidate: error validating: "+e);
			setSqlInfo(reqspec.request, sql);
			//log.debug("doValidate: error validating: "+e.getMessage(), e);
			DBUtil.doRollback(conn);
			throw e;
		}
	}

	void doExplain(Query relation, RequestSpec reqspec, Subject currentUser, Connection conn, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		SQL sql = null;
		DBMSFeatures feat = null;
		try {
			final DBMSResources res = DBMSResources.instance();
			feat = res.getSpecificFeatures(conn.getMetaData());
			
			if(!feat.supportsExplainPlan()) {
				throw new BadRequestException("Explain plan not available for database: "+feat.getClass().getSimpleName());
			}
			
			sql = SQL.createSQL(relation, reqspec, getUsername(currentUser));
			try {
				DBObjectUtils.validateQueryParameters(relation, sql.getFinalSql(), conn, true);
			}
			catch(SQLException e) {
				log.warn("error validating query '"+relation+"': "+sql.getFinalSql());
			}
			
			// sets named parameters
			reqspec.setNamedParameters(sql);
			
			sql.addOriginalParameters(reqspec);
			/*for(int i=0;i<reqspec.params.size();i++) {
				sql.bindParameterValues.add(reqspec.params.get(i));
			}*/
			//log.info("doExplain: params [#"+sql.bindParameterValues.size()+"]: "+sql.bindParameterValues);
			ResultSet rs = feat.explainPlan(sql.getFinalSql(), sql.getParameterValues(), conn);
			
			dumpResultSet(rs, reqspec, relation.getSchemaName(), relation.getName(),
					null, //pk!=null?pk.getUniqueColumns():null,
					null, null, //fks, uks,
					false, //applyLimitOffsetInResultSet,
					resp);
		}
		catch(SQLException e) {
			log.info("doExplain: error explaining: "+e);
			String explainSql = feat.sqlExplainPlanQuery(sql.getFinalSql());
			if(explainSql!=null) {
				//log.info("doExplain: explainSql: "+explainSql+" - indexOf == "+explainSql.indexOf(sql.getFinalSql()));
				setSqlInfo(reqspec.request, explainSql, sql.getFinalSql());
			}
			//log.debug("doExplain: error explaining: "+e, e);
			DBUtil.doRollback(conn);
			throw e;
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
	protected void doExecute(ExecutableObject eo, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		//log.info("eo: "+eo+" ; currentUser: "+currentUser.getPrincipal() + " ; remote: "+reqspec.getRemoteInfo());
		Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		
		//XXXdone: test for Subject's permissions
		try {
		
		int pc = eo.getParams()!=null ? eo.getParams().size() : 0;
		int inParamCount = SchemaModelUtils.getNumberOfInParameters(eo.getParams());
		int outParamCount = 0;
		Object retObject = null;
		String sql = SQL.createExecuteSqlFromBody(eo, getUsername(currentUser) );
		CallableStatement stmt = null;
		int paramOffset = 1 + (eo.getType()==DBObjectType.FUNCTION?1:0);
		boolean hasResultSet = false;
		
		if((eo.getType()==DBObjectType.EXECUTABLE || eo.getType()==DBObjectType.SCRIPT)
				&& sql!=null && !sql.equals("")) {
			
			//log.debug("executing BODY: "+sql);
			stmt = conn.prepareCall(sql);
			try {
				ParameterMetaData pmd = stmt.getParameterMetaData();
				if(pc != pmd.getParameterCount()) {
					log.warn("#eo.getParams() ["+pc+"] != pmd.getParameterCount() ["+pmd.getParameterCount()+"]");
				}
			}
			catch(SQLException e) {
				log.warn("doExecute: error on getParameterMetaData() [pc="+pc+"]: "+e);
			}
			if(reqspec.params.size() < inParamCount) {
				throw new BadRequestException("Number of request parameters ["+reqspec.params.size()+"] less than number of executable's parameters [#params="+pc+" ; inParamCount="+inParamCount+"]");
			}
			int paramIndex = 0;
			for(int i=0;i<pc;i++) {
				ExecutableParameter ep = eo.getParams().get(i); 
				if(SchemaModelUtils.isInParameter(ep)) {
					Object o = reqspec.params.get(paramIndex++);
					setStatementParameter(stmt, i+1, o);
				}
				else {
					stmt.registerOutParameter(i+1, DBUtil.getSQLTypeForColumnType(ep.getDataType()));
				}
			}
		}
		else {
			sql = SQL.createExecuteSQLstr(eo);
			stmt = conn.prepareCall(sql);
			//int inParamCount = 0;
			if(reqspec.params.size() < inParamCount) {
				throw new BadRequestException("Number of request parameters ["+reqspec.params.size()+"] less than number of executable's parameters [size()="+eo.getParams().size()+" ; inParamCount="+inParamCount+"]");
			}
			for(int i=0;i<eo.getParams().size();i++) {
				ExecutableParameter ep = eo.getParams().get(i);
				if(SchemaModelUtils.isInParameter(ep)) {
					/*if(reqspec.params.size() <= i) {
						throw new BadRequestException("Number of request parameters ["+reqspec.params.size()+"] less than index of executable's parameter [i="+i+" ; inParamCount="+(inParamCount)+" ; size="+eo.getParams().size()+"]");
					}*/
					//XXX: oracle: when using IN OUT parameters, driver may require to use specific type (stmt.setDouble()) // stmt.setDouble(i+paramOffset, ...);
					setStatementParameter(stmt, i+paramOffset, reqspec.params.get(i));
					//log.info("["+i+"/"+(inParamCount+paramOffset)+"] setObject: "+reqspec.params.get(inParamCount));
					//inParamCount++;
				}
				if(SchemaModelUtils.isOutParameter(ep)) {
					stmt.registerOutParameter(i+paramOffset, DBUtil.getSQLTypeForColumnType(ep.getDataType()));
					//log.info("["+i+"/"+(outParamCount+paramOffset)+"] registerOutParameter ; type="+DBUtil.getSQLTypeForColumnType(ep.getDataType()));
					outParamCount++;
				}
				//log.info("["+i+"] param: "+ep);
			}
			if(eo.getType()==DBObjectType.FUNCTION) { // is function !?! // eo.getReturnParam()!=null
				int type = Types.VARCHAR;
				if(eo.getReturnParam()!=null) {
					type = DBUtil.getSQLTypeForColumnType(eo.getReturnParam().getDataType());
				}
				stmt.registerOutParameter(1, type);
				//log.info("[return] registerOutParameter ; type="+DBUtil.getSQLTypeForColumnType(eo.getReturnParam().getDataType()));
				outParamCount++;
			}
		}

		log.debug("sql exec: "+sql+" [executable="+eo+" ; return="+eo.getReturnParam()+" ; inParamCount="+inParamCount+" ; outParamCount="+outParamCount+"]");
		hasResultSet = stmt.execute();

		int updatecount = stmt.getUpdateCount();
		//log.debug("hasResultSet: "+hasResultSet+" ; updateCount: "+updatecount);
		if(updatecount!=-1) { 
			resp.addIntHeader(ResponseSpec.HEADER_UPDATECOUNT, updatecount);
		}

		try {
			ResultSet generatedKeys = stmt.getGeneratedKeys();
			if(generatedKeys!=null && generatedKeys.next()) {
				List<String> colVals = getGeneratedKeys(generatedKeys);
				//log.debug("getGeneratedKeys: "+colVals);
				setGeneratedKeys(reqspec, resp, colVals);
			}
			else {
				//log.debug("getGeneratedKeys: no keys");
			}
		}
		catch(SQLException e) {
			log.warn("doExecute: getGeneratedKeys: "+e.getMessage());
		}
		
		boolean gotReturn = false;
		if(eo.getType()==DBObjectType.FUNCTION) { // is function !?! // eo.getReturnParam()!=null
			retObject = stmt.getObject(1);
			pushReturnObject(reqspec, retObject);
			gotReturn = true;
		}
		if(hasResultSet && !gotReturn) {
			retObject = stmt.getResultSet();
			pushReturnObject(reqspec, retObject);
			gotReturn = true;
		}
		final boolean allowMultipleReturnObjects = allowMultipleReturnObjects();
		for(int i=0;i<pc;i++) {
			ExecutableParameter ep = eo.getParams().get(i);
			if(SchemaModelUtils.isOutParameter(ep)) {
				if(gotReturn && outParamCount>1 && !allowMultipleReturnObjects) {
					log.warn("there are "+outParamCount+" out parameter. Only the first will be returned [allowMultipleReturnObjects=="+allowMultipleReturnObjects+"]");
					resp.addHeader(ResponseSpec.HEADER_WARNING, "Execute-TooManyReturnParams ReturnCount="+outParamCount);
					break; //got first result
					//log.info("ret["+i+";"+(i+paramOffset)+"]: "+stmt.getObject(i+paramOffset));
				}
				retObject = stmt.getObject(i+paramOffset);
				pushReturnObject(reqspec, retObject);
				gotReturn = true;
			}
		}
		resp.addIntHeader(ResponseSpec.HEADER_EXECUTE_RETURNCOUNT, outParamCount);
		
		if(retObject!=null) {
			if(retObject instanceof ResultSet) {
				writeExecuteResultSetOutput(reqspec, eo, resp, (ResultSet)retObject);
			}
			else {
				setContentType(resp, MIME_TEXT);
				if(retObject instanceof Clob) {
					Clob cret = (Clob) retObject;
					//retObject = cret.getSubString(0L, (int) cret.length());
					retObject = IOUtil.readFromReader(cret.getCharacterStream());
				}
				writeExecuteOutput(reqspec, eo, resp, retObject.toString());
			}
		}
		else {
			setContentType(resp, MIME_TEXT);
			if(outParamCount==0) {
				//XXX reqspec.getExecuteWithNoReturnSucessStatus(); //?
				writeExecuteOutput(reqspec, eo, resp, "execution successful - no return");
			}
			else {
				writeExecuteOutput(reqspec, eo, resp, "execution successful - null return");
			}
		}

		DBUtil.doCommit(conn);
		}
		catch(SQLException e) {
			DBUtil.doRollback(conn);
			String message = "Error executing procedure/fuction "+eo.getQualifiedName()+": ";
			//log.warn(message+e);
			log.debug(message+e.getMessage(), e);
			throw new SQLException(message, e);
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}
	
	protected boolean allowMultipleReturnObjects() {
		return false;
	}
	
	@SuppressWarnings("unchecked")
	protected List<Object> getReturnList(RequestSpec reqspec) {
		List<Object> retlist = null;
		Object rl = reqspec.getAttribute(RequestSpec.ATTR_RETURN_LIST);
		if(rl==null) {
			retlist = new ArrayList<Object>();
			reqspec.setAttribute(RequestSpec.ATTR_RETURN_LIST, retlist);
		}
		else {
			retlist = (List<Object>) rl;
		}
		return retlist;
	}
	
	protected void pushReturnObject(RequestSpec reqspec, Object o) {
		getReturnList(reqspec).add(o);
	}
	
	void setStatementParameter(PreparedStatement stmt, int pos, Object o) throws SQLException, IOException {
		if(o instanceof Part) {
			Part p = (Part) o;
			//log.debug("setStatementParameter[Part]: "+pos+" / size=="+p.getSize());
			// XXX clob?
			//String content = IOUtil.readFromReader(new InputStreamReader(p.getInputStream()));
			//stmt.setString(pos, content);
			// blob
			stmt.setBinaryStream(pos, p.getInputStream());
		}
		else {
			stmt.setObject(pos, o);
		}
	}

	static final List<String> statusUniqueColumns = Arrays.asList(new String[]{"schemaName", "name"});
	static final List<String> relationCommonCols =  Arrays.asList(new String[]{"relationType", "columnNames", "columnTypes", "columnRemarks", "constraints", "remarks", "grants"});
	static final List<String> executableCols =  Arrays.asList(new String[]{"type", "packageName", "qualifiedName", "parameterCount", "parameterTypes", "params", "returnParam", "remarks", "grants"});

	static final List<String> relationAndExecutableCols =  Arrays.asList(new String[]{"parameterCount", "parameterTypes", "remarks", "grants", "dbObjectType" /*"class", "type", "relationType"*/});
	
	static final List<String> tableExclusiveColumns = Arrays.asList(new String[]{"PKConstraint"});
	static final List<String> viewExclusiveColumns = Arrays.asList(new String[]{"parameterCount", "parameterTypes"});
	static final List<String> queryExclusiveColumns = Arrays.asList(new String[]{"parameterCount", "parameterTypes", "namedParameterNames"});

	static final List<String> tableAllColumns;
	static final List<String> viewAllColumns;
	static final List<String> queryAllColumns;
	static final List<String> relationAllColumns;
	static final List<String> executableAllColumns;
	
	static {
		tableAllColumns = new ArrayList<String>(); tableAllColumns.addAll(relationCommonCols); tableAllColumns.addAll(tableExclusiveColumns);
		viewAllColumns = new ArrayList<String>(); viewAllColumns.addAll(relationCommonCols); viewAllColumns.addAll(viewExclusiveColumns);
		queryAllColumns = new ArrayList<String>(); queryAllColumns.addAll(relationCommonCols); queryAllColumns.addAll(queryExclusiveColumns);
		relationAllColumns = new ArrayList<String>(); relationAllColumns.addAll(relationCommonCols); relationAllColumns.addAll(viewExclusiveColumns);
		executableAllColumns = new ArrayList<String>(); executableAllColumns.addAll(executableCols);
	}
	
	@SuppressWarnings("resource")
	protected void doStatus(SchemaModel model, String statusTypeStr, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws IntrospectionException, SQLException, IOException, ServletException, ClassNotFoundException, NamingException {
		ResultSet rs = null;
		List<FK> importedFKs = null;
		List<Constraint> uks = null;
		DBObjectType statusType = DBObjectType.valueOf(statusTypeStr);
		final String objectName = statusType.desc();
		PrivilegeType privilege = PrivilegeType.SELECT;
		//XXX: filter by schemaName, name? ResultSetFilterDecorator(rs, colpositions, colvalues)?
		//log.info("doStatus: "+statusType);
		switch (statusType) {
		//XXX Relations: importedFKs = ...?
		case TABLE: {
			List<Table> list = new ArrayList<Table>(); list.addAll(model.getTables());
			rs = new ResultSetListAdapter<Table>(objectName, statusUniqueColumns, tableAllColumns, list, Table.class);
			break;
		}
		case VIEW: {
			List<View> list = new ArrayList<View>(); list.addAll(model.getViews());
			rs = new ResultSetListAdapter<View>(objectName, statusUniqueColumns, viewAllColumns, list, View.class);
			break;
		}
		case RELATION: {
			/*List<Relation> list = new ArrayList<Relation>(); list.addAll(model.getViews()); list.addAll(model.getTables());
			rs = new ResultSetListAdapter<Relation>(objectName, statusUniqueColumns, relationAllColumns, list, Relation.class);*/
			//XXX: sort relations? maybe a client (js) task
			List<View> lViews = Utils.newList(model.getViews());
			ResultSet rsQ = new ResultSetListAdapter<View>(objectName, statusUniqueColumns, queryAllColumns, lViews, Query.class);
			List<Table> lTable = Utils.newList(model.getTables());
			ResultSet rsT = new ResultSetListAdapter<Table>(objectName, statusUniqueColumns, queryAllColumns, lTable, Table.class);
			
			List<ResultSet> lrs = Utils.newList(rsQ, rsT);
			rs = new UnionResultSet(lrs);
			break;
		}
		case QUERY: {
			List<View> list = Utils.newList(model.getViews());
			rs = new ResultSetListAdapter<View>(objectName, statusUniqueColumns, queryAllColumns, list, Query.class);
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
		case ANY: {
			List<View> lViews = Utils.newList(model.getViews());
			ResultSet rsQ = new ResultSetListAdapter<View>(objectName, statusUniqueColumns, relationAndExecutableCols, lViews, Query.class);
			List<Table> lTable = Utils.newList(model.getTables());
			ResultSet rsT = new ResultSetListAdapter<Table>(objectName, statusUniqueColumns, relationAndExecutableCols, lTable, Table.class);
			List<ExecutableObject> lExec = Utils.newList(model.getExecutables());
			ResultSet rsE = new ResultSetListAdapter<ExecutableObject>(objectName, statusUniqueColumns, relationAndExecutableCols, lExec, ExecutableObject.class);
			//XXX: add FKs?
			
			List<ResultSet> lrs = Utils.newList(rsQ, rsT, rsE);
			rs = new UnionResultSet(lrs);
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
	
	protected ResultSet filterStatus(ResultSet rs, RequestSpec reqspec, Subject currentUser, PrivilegeType privilege) throws SQLException {
		if(reqspec.params!=null && reqspec.params.size()>0) {
			rs = new ResultSetFilterDecorator(rs, Arrays.asList(new Integer[]{1,2}), MiscUtils.toStringList(reqspec.params) );
		}
		//log.info("doFilterStatusByPermission: "+doFilterStatusByPermission+" / doFilterStatusByQueryGrants: "+doFilterStatusByQueryGrants+" / "
		//		+ShiroUtils.isPermitted(currentUser, doNotCheckGrantsPermission)+":"+doNotCheckGrantsPermission);
		if(doFilterStatusByPermission && currentUser!=null) {
			// filter by [(relation)Type]:SELECT|EXECUTE:[schemaName]:[name]
			rs = new ResultSetPermissionFilterDecorator(rs, currentUser, "[3]:"+privilege+":[1]:[2]");
		}
		if(doFilterStatusByQueryGrants && currentUser!=null && (! ShiroUtils.isPermitted(currentUser, doNotCheckGrantsPermission)) ) { // XXX: doNotCheckGrantsPermission: SELECT_ANY and/or EXECUTE_ANY ?
			rs = new ResultSetGrantsFilterDecorator(rs, ShiroUtils.getSubjectRoles(currentUser), privilege, "name", "grants");
		}
		return rs;
	}
	
	// TODO: get lock field from relation's metadata
	boolean tryOptimisticLock(Relation relation, RequestSpec reqspec, SQL sql) {
		String lockField = prop.getProperty("queryon.optimisticlock@"+relation.getQualifiedName()+".field");
		if(lockField==null) {
			return false;
		}
		int idx = relation.getColumnNames().indexOf(lockField);
		if(idx<0) {
			throw new BadRequestException("Lock field '"+lockField+"' not found in relation '"+relation.getQualifiedName()+"'");
		}
		
		String type = relation.getColumnTypes().get(idx);
		sql.addFilter("("+lockField+" = ? or "+lockField+" is null)");
		sql.addParameter(reqspec.optimisticLock, type);
		log.info("tryOptimisticLock: "+reqspec.optimisticLock+" ; "+type);
		
		return true;
	}
	
	void checkOptimisticLock(Relation relation, RequestSpec reqspec, Constraint pk, Connection conn) throws SQLException, IOException {
		SQL sqlLock = SQL.createSQL(relation, reqspec, null);
		preprocessParameters(reqspec, relation, pk);
		filterByKey(relation, reqspec, pk, sqlLock);
		sqlLock.addCount();
		PreparedStatement stmt = conn.prepareStatement(sqlLock.getFinalSql());
		sqlLock.bindParameters(stmt);
		log.debug("sql lock: "+sqlLock.getFinalSql());
		ResultSet rs = stmt.executeQuery();
		/*if(rs.next()) {
			log.debug("no rows updated but optimisticLock active... query without lock has rows, so probably row has been changed");
			throw new BadRequestException("Row has been changed [update-count="+count+"]", HttpServletResponse.SC_CONFLICT);
		}*/
		rs.next();
		int sqlLockCount = rs.getInt(1);
		if(sqlLockCount>=1) {
			log.debug("no rows updated but optimisticLock active... query without lock has rows [#"+sqlLockCount+"], so probably row has been changed");
			throw new BadRequestException("Row has been changed", HttpServletResponse.SC_CONFLICT);
		}
	}
	
	protected void doDelete(Relation relation, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException, ServletException {
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
		preprocessParameters(reqspec, relation, pk);
		filterByKey(relation, reqspec, pk, sql);

		// xtra filters
		List<String> warnings = filterByXtraParams(relation, reqspec, sql);
		//delete warnings should not be ignored...
		if(warnings!=null && warnings.size()>0) {
			String warns = Utils.join(warnings, ", ");
			throw new BadRequestException("Filter error: "+warns);
		}
		
		//XXX optimistic lock on delete?
		//boolean willTryLock = tryOptimisticLock(relation, reqspec, sql);
		
		PreparedStatement st = conn.prepareStatement(sql.getFinalSql());
		sql.bindParameters(st);
		
		log.debug("sql delete: "+sql);
		
		int count = st.executeUpdate();
		
		if(fullKeyDefined(reqspec, pk)) {
			if(count==0) {
				//if(willTryLock) {
				//	checkOptimisticLock(relation, reqspec, pk, conn);
				//}
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
		List<UpdatePlugin> plugins = updatePlugins.get(reqspec.modelId);
			if(plugins!=null) {
			for(UpdatePlugin up: plugins) {
				if(up.accepts(relation)) {
					//up.setProperties(prop);
					up.setConnection(conn);
					up.onDelete(relation, reqspec);
				}
			}
		}
		
		//XXXxxx ??: (heterogeneous) array to ResultSet adapter? (?!?)
		DBUtil.doCommit(conn);
		Integer status = reqspec.getDeleteSucessStatus();
		if(status!=null) {
			resp.setStatus(status);
		}
		resp.addIntHeader(ResponseSpec.HEADER_UPDATECOUNT, count);
		if(status==null || status!=HttpServletResponse.SC_NO_CONTENT) {
			writeUpdateCount(reqspec, resp, count, "deleted");
		}
		
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

	protected void doUpdate(Relation relation, RequestSpec reqspec, Subject currentUser, boolean isPermitted, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		SQL sql = null;
		try {

		sql = SQL.createUpdateSQL(relation);
		
		Set<String> columns = new HashSet<String>();
		columns.addAll(relation.getColumnNames());

		/*
		Set<String> roles = ShiroUtils.getSubjectRoles(currentUser);
		List<Grant> updateGrants = QOnModelUtils.filterGrantsByPrivilegeType(relation.getGrants(), PrivilegeType.UPDATE);
		boolean hasRelationUpdatePermission = QOnModelUtils.hasPermissionWithoutColumn(updateGrants, roles)
				|| ShiroUtils.isPermitted(currentUser, ActionType.UPDATE_ANY.name());
		*/
		boolean hasRelationInsertPermission = isPermitted || ShiroUtils.isPermitted(currentUser, ActionType.UPDATE_ANY.name());
		
		Constraint pk = SchemaModelUtils.getPK(relation);
		preprocessParameters(reqspec, relation, pk);
		
		StringBuilder sb = new StringBuilder();
		int colsCount = 0;
		Set<String> updateCols = new HashSet<String>();
		String otype = QueryOn.getObjectType((DBIdentifiable) relation);
		
		// standart parameters
		{
		Iterator<String> cols = reqspec.updateValues.keySet().iterator();
		for(; cols.hasNext();) {
			String col = cols.next();
			if(! MiscUtils.containsIgnoreCase(columns, col)) {
				log.warn("[update] unknown column: "+col);
				throw new BadRequestException("[update] unknown column: "+col);
			}
			if(updateCols.contains(col)) {
				log.warn("doUpdate: column '"+col+"' already processed");
				continue;
			}
			updateCols.add(col);
			//TODOne: check UPDATE permission on each row, based on grants
			//if(validateUpdateColumnPermissions && !hasRelationUpdatePermission && !QOnModelUtils.hasPermissionOnColumn(updateGrants, roles, col)) {
			if(validateUpdateColumnPermissions && !hasRelationInsertPermission && !ShiroUtils.isPermitted(currentUser, otype+":"+ActionType.UPDATE.name(), reqspec.object, col)) {
				throw new ForbiddenException("no update permission on column: "+reqspec.object+"."+col, currentUser.isAuthenticated());
			}
			//XXX date ''? timestamp '' ? http://blog.tanelpoder.com/2012/12/29/a-tip-for-lazy-oracle-users-type-less-with-ansi-date-and-timestamp-sql-syntax/
			sb.append((colsCount!=0?", ":"")+col+" = ?");
			String ctype = DBUtil.getColumnTypeFromColName(relation, col);
			sql.addParameter(reqspec.updateValues.get(col), ctype);
			colsCount++;
		}
		//log.debug("bindpars [#"+sql.bindParameterValues.size()+"]: "+sql.bindParameterValues);
		}
		// blobs
		{
			Iterator<String> pcols = reqspec.updatePartValues.keySet().iterator();
			while(pcols.hasNext()) {
				String col = pcols.next();
				if(! MiscUtils.containsIgnoreCase(columns, col)) {
					log.warn("[update] unknown column: "+col);
					throw new BadRequestException("[update] unknown column: "+col);
				}
				if(updateCols.contains(col)) {
					log.warn("doUpdate: blob column '"+col+"' already processed");
					continue;
				}
				updateCols.add(col);
				//TODOne: check UPDATE permission on each row, based on grants
				//if(validateUpdateColumnPermissions && !hasRelationUpdatePermission && !QOnModelUtils.hasPermissionOnColumn(updateGrants, roles, col)) {
				if(validateUpdateColumnPermissions && !hasRelationInsertPermission && !ShiroUtils.isPermitted(currentUser, otype+":"+ActionType.UPDATE.name(), relation.getName(), col)) {
					throw new ForbiddenException("no update permission on column: "+relation.getName()+"."+col, currentUser.isAuthenticated());
				}
				//XXX date ''? timestamp '' ? http://blog.tanelpoder.com/2012/12/29/a-tip-for-lazy-oracle-users-type-less-with-ansi-date-and-timestamp-sql-syntax/
				sb.append((colsCount!=0?", ":"")+col+" = ?");
				
				int colindex = relation.getColumnNames().indexOf(col);
				String ctype = relation.getColumnTypes().get(colindex);
				
				addPartParameter(reqspec, sql, ctype, col, colindex);
				colsCount++;
			}
		}

		//log.debug("updateValues: "+reqspec.updateValues);

		if("".equals(sb.toString())) {
			throw new BadRequestException("[update] No valid columns");
		}
		sql.applyUpdate(sb.toString());

		filterByKey(relation, reqspec, pk, sql);

		// xtra filters
		List<String> warnings = filterByXtraParams(relation, reqspec, sql);
		//update warnings should not be ignored...
		if(warnings!=null && warnings.size()>0) {
			String warns = Utils.join(warnings, ", ");
			throw new BadRequestException("Filter error: "+warns);
		}

		//optimistic lock
		boolean willTryLock = tryOptimisticLock(relation, reqspec, sql);

		//log.info("pre-sql update: "+sql);
		
		String finalSql = sql.getFinalSql();
		PreparedStatement st = conn.prepareStatement(finalSql);
		sql.bindParameters(st);

		//log.debug("sql update: "+sql+"\nfinalSql: "+finalSql+(willTryLock?" [willTryOptimisticLock]":""));
		log.debug("sql:\n"+finalSql+(willTryLock?" [willTryOptimisticLock]":""));
		
		int count = st.executeUpdate();
		
		if(count==0 && willTryLock) {
			checkOptimisticLock(relation, reqspec, pk, conn);
		}
		
		//XXXdone: boundaries for # of updated rows?
		if(reqspec.maxUpdates!=null && count > reqspec.maxUpdates) {
			throw new BadRequestException("Update count ["+count+"] greater than update-max ["+reqspec.maxUpdates+"]");
		}
		if(reqspec.minUpdates!=null && count < reqspec.minUpdates) {
			throw new BadRequestException("Update count ["+count+"] less than update-min ["+reqspec.minUpdates+"]");
		}
		
		//XXX add ResultSet generatedKeys = st.getGeneratedKeys(); //?
		
		//XXX plugin should be called before execute()/bindParameters()?
		List<UpdatePlugin> plugins = updatePlugins.get(reqspec.modelId);
			if(plugins!=null) {
			for(UpdatePlugin up: plugins) {
				if(up.accepts(relation)) {
					//up.setProperties(prop);
					up.setConnection(conn);
					up.onUpdate(relation, reqspec);
				}
			}
		}
		
		//XXX: (heterogeneous) array / map to ResultSet adapter?
		DBUtil.doCommit(conn);
		
		Integer status = reqspec.getUpdateSucessStatus();
		if(status!=null) {
			resp.setStatus(status);
		}
		resp.addIntHeader(ResponseSpec.HEADER_UPDATECOUNT, count);
		if(status==null || status!=HttpServletResponse.SC_NO_CONTENT) {
			writeUpdateCount(reqspec, resp, count, "updated");
		}

		}
		catch(BadRequestException e) {
			DBUtil.doRollback(conn);
			throw e;
		}
		catch(SQLException e) {
			DBUtil.doRollback(conn);
			log.warn("Update error, ex="+e.getMessage()+" ; sql=\n"+sql,e); //e.printStackTrace();
			//throw new InternalServerException("SQL Error: "+e, e);
			throw e;
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}

	protected void doInsert(Relation relation, RequestSpec reqspec, Subject currentUser, boolean isPermitted, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		try {

		Constraint pk = SchemaModelUtils.getPK(relation);
		preprocessParameters(reqspec, relation, pk);
			
		SQL sql = SQL.createInsertSQL(relation);

		Set<String> columns = new HashSet<String>();
		columns.addAll(relation.getColumnNames());

		String[] pkcols = null;
		if(pk!=null) {
			//use url params to set PK cols values
			for(int i=0;i<pk.getUniqueColumns().size() && i<reqspec.params.size();i++) {
				String pkcol = pk.getUniqueColumns().get(i);
				if(! MiscUtils.containsIgnoreCase(columns, pkcol)) {
					String message = "unknown PK column: "+pkcol;
					log.warn(message);
					if(isStrictMode()) {
						throw new BadRequestException(message);
					}
					continue;
				}
				String pkval = String.valueOf( reqspec.params.get(i) );
				if(pkcol!=null && pkval!=null) {
					reqspec.updateValues.put(pkcol, pkval);
				}
			}
			pkcols = pk.getUniqueColumns().toArray(new String[]{});
		}
		
		/*
		Set<String> roles = ShiroUtils.getSubjectRoles(currentUser);
		List<Grant> insertGrants = QOnModelUtils.filterGrantsByPrivilegeType(relation.getGrants(), PrivilegeType.INSERT);
		boolean hasRelationInsertPermission = QOnModelUtils.hasPermissionWithoutColumn(insertGrants, roles)
				|| ShiroUtils.isPermitted(currentUser, ActionType.INSERT_ANY.name());
		*/
		boolean hasRelationInsertPermission = isPermitted || ShiroUtils.isPermitted(currentUser, ActionType.INSERT_ANY.name());
		
		StringBuilder sbCols = new StringBuilder();
		StringBuilder sbVals = new StringBuilder();
		int colsCount = 0;
		Set<String> updateCols = new HashSet<String>();
		{
			Iterator<Map.Entry<String, String>> cols = reqspec.updateValues.entrySet().iterator();
			String otype = QueryOn.getObjectType((DBIdentifiable) relation);
			
		for(; cols.hasNext();) {
				Map.Entry<String, String> colmap = cols.next();
				String col = colmap.getKey();
				if(! MiscUtils.containsIgnoreCase(columns, col)) {
				log.warn("doInsert: unknown 'value' column: "+col); //+" ; cols: "+columns);
				throw new BadRequestException("[insert] unknown column: "+col);
			}
				if(updateCols.contains(col)) {
					log.warn("doInsert: column '"+col+"' already processed");
					continue;
				}
				updateCols.add(col);
			//if(validateUpdateColumnPermissions && !hasRelationInsertPermission && !QOnModelUtils.hasPermissionOnColumn(insertGrants, roles, col)) {
			if(validateUpdateColumnPermissions && !hasRelationInsertPermission && !ShiroUtils.isPermitted(currentUser, otype+":"+ActionType.INSERT.name(), reqspec.object, col)) {
				//log.warn("user: "+currentUser+" ; principal: "+currentUser.getPrincipal()+" ; roles: "+roles);
				throw new ForbiddenException("no insert permission on column: "+reqspec.object+"."+col, currentUser.isAuthenticated());
			}
			//XXX timestamp '' ?
			sbCols.append((colsCount!=0?", ":"")+col);
			sbVals.append((colsCount!=0?", ":"")+"?");
			String ctype = DBUtil.getColumnTypeFromColName(relation, col);
				//log.debug("col: "+col+" ; ctype: "+ctype);
				sql.addParameter(colmap.getValue(), ctype);
			colsCount++;
		}
		}

		// blobs
		{
			Iterator<String> pcols = reqspec.updatePartValues.keySet().iterator();
			while(pcols.hasNext()) {
				String pcol = pcols.next();
				if(! MiscUtils.containsIgnoreCase(columns, pcol)) {
					log.warn("unknown 'value' column: "+pcol);
					throw new BadRequestException("[insert] unknown column: "+pcol);
				}
				if(updateCols.contains(pcol)) {
					log.warn("doInsert: blob column '"+pcol+"' already processed");
					continue;
				}
				updateCols.add(pcol);
				
				//if(validateUpdateColumnPermissions && !hasRelationInsertPermission && !QOnModelUtils.hasPermissionOnColumn(insertGrants, roles, pcol)) {
				if(validateUpdateColumnPermissions && !hasRelationInsertPermission && !ShiroUtils.isPermitted(currentUser, ActionType.INSERT.name(), relation.getName(), pcol)) {
					throw new ForbiddenException("no insert permission on column: "+relation.getName()+"."+pcol, currentUser.isAuthenticated());
				}
				int colindex = MiscUtils.indexOfIgnoreCase(relation.getColumnNames(), pcol);
				if(colindex>=0) {
					sbCols.append((colsCount!=0?", ":"")+pcol);
					sbVals.append((colsCount!=0?", ":"")+"?");
					
					String ctype = relation.getColumnTypes().get(colindex);
					addPartParameter(reqspec, sql, ctype, pcol, colindex);
					colsCount++;
				}
				else {
					String msg = "column "+pcol+" not found on relation "+relation;
					log.warn(msg);
					throw new BadRequestException(msg);
				}
			}
		}
		
		if("".equals(sbCols.toString())) {
			throw new BadRequestException("[insert] No valid columns");
		}
		sql.applyInsert(sbCols.toString(), sbVals.toString());

		//log.debug("sql insert: " + sql + (pkcols!=null?" [pkcols="+Arrays.asList(pkcols)+"]":"") );

		PreparedStatement st = pkcols!=null? 
			conn.prepareStatement(sql.getFinalSql(), pkcols):
			conn.prepareStatement(sql.getFinalSql());
		sql.bindParameters(st);
		
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
			setGeneratedKeys(reqspec, resp, colVals);
		}
		
		List<UpdatePlugin> plugins = updatePlugins.get(reqspec.modelId);
			if(plugins!=null) {
			for(UpdatePlugin up: plugins) {
				if(up.accepts(relation)) {
					//up.setProperties(prop);
					up.setConnection(conn);
					up.onInsert(relation, reqspec);
				}
			}
		}
		
		//XXX: (heterogeneous) array / map to ResultSet adapter?
		DBUtil.doCommit(conn);
		setResponseStatus(resp, HttpServletResponse.SC_CREATED);
		writeUpdateCount(reqspec, resp, count, "inserted");
		
		}
		catch(BadRequestException e) {
			DBUtil.doRollback(conn);
			throw e;
		}
		catch(SQLException e) {
			DBUtil.doRollback(conn);
			//e.printStackTrace();
			//throw new InternalServerException("SQL Error: "+e, e);
			throw e;
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}
	
	protected void setResponseStatus(HttpServletResponse resp, int status) {
		resp.setStatus(status);
	}
	
	protected void setContentType(HttpServletResponse resp, String type) {
		resp.setContentType(type);
	}
	
	protected void setGeneratedKeys(RequestSpec reqspec, HttpServletResponse resp, List<String> generatedKey) {
		resp.setHeader(ResponseSpec.HEADER_RELATION_UK_VALUES, Utils.join(generatedKey, ", "));
	}
	
	protected void writeUpdateCount(RequestSpec reqspec, HttpServletResponse resp, int count, String action) throws IOException {
		resp.setContentType(MIME_TEXT);
		resp.getWriter().write(count+" "+(count>1?"rows":"row")+" "+action);
	}

	protected void writeExecuteOutput(RequestSpec reqspec, ExecutableObject eo, HttpServletResponse resp, String value) throws IOException {
		resp.getWriter().write(value);
	}

	protected void writeExecuteResultSetOutput(RequestSpec reqspec, ExecutableObject eo, HttpServletResponse resp, ResultSet value) throws IOException, SQLException {
		dumpResultSet(value, reqspec, null, reqspec.object, null, null, null, true, resp);
	}
	
	void addPartParameter(RequestSpec reqspec, SQL sql, String ctype, String col, int colindex) throws IOException {
		boolean isBinary = DBUtil.BLOB_COL_TYPES_LIST.contains(ctype.toUpperCase());
		if(isBinary) {
			sql.addParameter(reqspec.updatePartValues.get(col).getInputStream());
		}
		else {
			//log.info("addPartParameter: xSetRequestUtf8="+xSetRequestUtf8);
			/*if(xSetRequestUtf8) {
				sql.bindParameterValues.add(new InputStreamReader( reqspec.updatePartValues.get(col).getInputStream(), UTF8 ));
				//log.info("1.addPartParameter: xSetRequestUtf8="+xSetRequestUtf8);
			}
			else {*/
				sql.addParameter(new InputStreamReader( reqspec.updatePartValues.get(col).getInputStream() ));
				//log.info("2.addPartParameter: xSetRequestUtf8="+xSetRequestUtf8);
			//}
		}
		log.debug("col["+colindex+"] "+col+": "+ctype+" [isBinary="+isBinary+"]");
	}
	
	void doManage(SchemaModel model, RequestSpec reqspec, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, ClassNotFoundException, SQLException, NamingException {
		//TODO: only reloads model for now...
		// - reload-config, reload-model, rerun-processors
		
		if(reqspec.params.size()<1) {
			throw new BadRequestException("parameters needed");
		}
		
		resp.setContentType(MIME_TEXT);
		//log.info("doManage: params: "+reqspec.params);
		
		String action = String.valueOf( reqspec.params.get(0) );
		if(QOnManage.ACTION_RELOAD.equals(action)) {
			doInit(req.getServletContext());
			resp.getWriter().write("queryon config reloaded");
			return;
		}
		
		// XXX reload-auth-info - reload shiro config -- how to know if user has permission? how to reload?
		
		/*
		 * validate? diff? - generate diff script between (memory) model & database
		 * for each table, grab table's metadata from database & compare
		 */
		if(QOnManage.ACTION_DIFF.equals(action)) {
			Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
			QOnManage qm = new QOnManage();
			try {
				qm.diffModel(model, conn, resp);
			}
			catch(Exception e) {
				throw new InternalServerException("Error diffing model", e);
			}
			finally {
				ConnectionUtil.closeConnection(conn);
			}
			return;
		}
		
		throw new BadRequestException("unknown action: "+action);
	}
	
	protected void postService(SchemaModel model, RequestSpec reqspec, HttpServletRequest req, HttpServletResponse resp) {
	}
	
	protected static boolean fullKeyDefined(RequestSpec reqspec, Constraint pk) {
		if(pk==null) {
			return false;
		}
		//log.info("#cols: pk="+pk.getUniqueColumns().size()+" ; keyValues="+reqspec.keyValues.size()+", params="+reqspec.params.size());
		int paramsCount = reqspec.keyValues.size() + reqspec.params.size();
		return pk.getUniqueColumns().size() <= paramsCount;
	}
	
	List<String> getGeneratedKeys(ResultSet generatedKeys) throws SQLException {
		int colCount = generatedKeys.getMetaData().getColumnCount();
		List<String> colVals = new ArrayList<String>();
		for(int i=0;i<colCount;i++) {
			colVals.add(generatedKeys.getString(i+1));
		}
		return colVals;
	}
	
	protected static void filterByKey(Relation relation, RequestSpec reqspec, Constraint pk, SQL sql) {
		String filter = "";
		// TODOxxx: what if parameters already defined in query?
		if(reqspec.keyValues.size()>0 && reqspec.params.size()>0) {
			log.warn("filterByKey: both keyValues "+reqspec.keyValues+" and params "+reqspec.params+" defined - should be only one");
		}
		int paramsCount = reqspec.keyValues.size() + reqspec.params.size();
		//log.info("filterByKey: "+reqspec.keyValues+" / "+reqspec.params);
		if(paramsCount>0) {
			if(SchemaModelUtils.hasParameters(relation)) {
				ParametrizedDBObject pobject = (ParametrizedDBObject) relation;
				// query parameters already added in 'addOriginalParameters()'
				if(pobject.getParameterCount() > paramsCount) {
					log.warn("#"+paramsCount+" parameters defined "+reqspec.params+"/"+reqspec.keyValues+
							" but query '"+relation.getName()+"' expects more ["+pobject.getParameterCount()+"] parameters");
				}
				else if(pobject.getParameterCount() < paramsCount) {
					log.warn("#"+paramsCount+" parameters defined "+reqspec.params+"/"+reqspec.keyValues+
							" but query '"+relation.getName()+"' expects less ["+pobject.getParameterCount()+"] parameters");
				}
			}
			else if(pk==null) {
				log.warn("filter params defined "+reqspec.params+" but table '"+relation.getName()+"' has no PK or UNIQUE constraint");
			}
			else {
				List<Object> allParams = new ArrayList<Object>();
				allParams.addAll(getValuesOrderedByPkCols(reqspec.keyValues, pk.getUniqueColumns()));
				allParams.addAll(reqspec.params);
				
				if(allParams.size()>pk.getUniqueColumns().size()) {
					log.warn("parameter count [#"+allParams.size()+"] bigger than pk size [#"+pk.getUniqueColumns().size()+"]");
					log.debug("parameters: "+allParams);
					//XXX throw BadRequest ?
				}
				for(int i=0;i<pk.getUniqueColumns().size();i++) {
					if(allParams.size()<=i) { break; }
					//String s = reqspec.params.get(i);
					String column = pk.getUniqueColumns().get(i);
					filter += (i!=0?" and ":"")+SQL.sqlIdDecorator.get(column)+" = ?";
					sql.addParameter(allParams.get(i), DBUtil.getColumnTypeFromColName(relation, column));
					logFilter.debug("filterByKey: value["+i+"]="+allParams.get(i));
				}
			}
		}
		sql.addFilter(filter);
	}
	
	static List<Object> getValuesOrderedByPkCols(Map<String, String> keyValues, List<String> pkCols) {
		if(pkCols==null || pkCols.size()==0) { return null; }
		List<Object> ret = new ArrayList<Object>();
		if(pkCols.size()==1) {
			Collection<String> vals = keyValues.values();
			if(vals.size()>0) {
				String v = vals.iterator().next();
				//log.debug("size==1 ; v: "+v);
				ret.add(v);
			}
		}
		else {
			for(String col: pkCols) {
				if(keyValues.containsKey(col)) {
					String v = keyValues.get(col);
					//log.debug("c: "+col+" ; v: "+v);
					ret.add(v);
				}
			}
		}
		//log.debug("#pkCols = "+pkCols.size()+" ; #keyValues = "+keyValues.size()+" ; #values = "+ret.size());
		return ret;
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
		List<String> colTypes = relation.getColumnTypes();
		String relationName = relation.getName();
		
		final List<String> warnings = new ArrayList<String>();
		
		if(colNames!=null) {
			Set<String> columns = new HashSet<String>();
			columns.addAll(colNames);

			//XXXdone bind parameters with column type: only 'addUniqueFilter' & 'addMultiFilterSubexpression'
			
			// boolean filters
			addBooleanFilter(reqspec.filterNull, columns, sql, "is null", relationName, warnings);
			addBooleanFilter(reqspec.filterNotNull, columns, sql, "is not null", relationName, warnings);
			
			// uni-valued filters
			addUniqueFilter(reqspec.filterEquals, colNames, colTypes, sql, "=", relationName, warnings);
			addUniqueFilter(reqspec.filterNotEquals, colNames, colTypes, sql, "<>", relationName, warnings); //XXXxx should be multi-valued? nah, there is already a 'not in'
			addUniqueFilter(reqspec.filterGreaterThan, colNames, colTypes, sql, ">", relationName, warnings);
			addUniqueFilter(reqspec.filterGreaterOrEqual, colNames, colTypes, sql, ">=", relationName, warnings);
			addUniqueFilter(reqspec.filterLessThan, colNames, colTypes, sql, "<", relationName, warnings);
			addUniqueFilter(reqspec.filterLessOrEqual, colNames, colTypes, sql, "<=", relationName, warnings);

			// multi-valued filters
			addMultiFilter(reqspec.filterLike, columns, sql, "like ?", relationName, warnings);
			addMultiFilter(reqspec.filterNotLike, columns, sql, "not like ?", relationName, warnings);

			// multi-valued with subexpression filters
			addMultiFilterSubexpression(reqspec.filterIn, colNames, colTypes, sql, "in", relationName, warnings);
			addMultiFilterSubexpression(reqspec.filterNotIn, colNames, colTypes, sql, "not in", relationName, warnings);
		}
		else {
			Map<String, Map<String, ? extends Object>> mapFilters = reqspec.getMapFilters();
			for(Map.Entry<String, Map<String, ? extends Object>> e: mapFilters.entrySet()) {
				if(e.getValue().size()>0) {
					String message = "can't set '"+e.getKey()+"' filter: relation '"+relation.getName()+"' has no columns specified";
					log.warn(message);
					warnings.add(message);
				}
			}

			Map<String, Set<String>> setFilters = reqspec.getSetFilters();
			for(Map.Entry<String, Set<String>> e: setFilters.entrySet()) {
				if(e.getValue().size()>0) {
					String message = "can't set '"+e.getKey()+"' filter: relation '"+relation.getName()+"' has no columns specified";
					log.warn(message);
					warnings.add(message);
				}
			}
		}
		
		return warnings;
	}
	
	static void addUniqueFilter(final Map<String, String> valueMap, List<String> colNames, List<String> colTypes, SQL sql, String compareSymbol, String relationName, List<String> warnings) {
		for(String col: valueMap.keySet()) {
			if(!validateFilterColumnNames || colNames.contains(col)) {
				int idx = colNames.indexOf(col);
				sql.addParameter( valueMap.get(col), idx>=0?colTypes.get(idx):null );
				sql.addFilter(SQL.sqlIdDecorator.get(col)+" "+compareSymbol+" ?");
				logFilter.debug("addUniqueFilter: values="+valueMap.get(col));
			}
			else {
				log.warn("unknown filter column: "+col+" [relation="+relationName+"]");
				warnings.add("unknown filter column: "+col);
			}
		}
	}
	
	/*
	 * add colTypes? maybe not: just strings are used in 'like' & 'not like'
	 */
	static void addMultiFilter(final Map<String, String[]> valueMap, Set<String> columns, SQL sql, String compareExpression, String relationName, List<String> warnings) {
		for(String col: valueMap.keySet()) {
			if(!validateFilterColumnNames || columns.contains(col)) {
				String[] values = valueMap.get(col);
				for(int i=0;i<values.length;i++) {
					sql.addParameter(values[i]);
					sql.addFilter(SQL.sqlIdDecorator.get(col)+" "+compareExpression); //" like ?"
				}
				logFilter.debug("addMultiFilter: values="+Arrays.asList(values));
			}
			else {
				log.warn("unknown filter column: "+col+" [relation="+relationName+"]");
				warnings.add("unknown filter column: "+col);
			}
		}
	}

	static void addMultiFilterSubexpression(final Map<String, String[]> valueMap, List<String> colNames, List<String> colTypes, SQL sql, String compareExpression, String relationName, List<String> warnings) {
		for(String col: valueMap.keySet()) {
			if(!validateFilterColumnNames || colNames.contains(col)) {
				int idx = colNames.indexOf(col);
				String ctype = idx>=0?colTypes.get(idx):null;
				StringBuilder sb = new StringBuilder();
				sb.append(SQL.sqlIdDecorator.get(col)+" "+compareExpression+" (");
				String[] values = valueMap.get(col);
				for(int i=0;i<values.length;i++) {
					String value = values[i];
					sb.append((i>0?", ":"")+"?");
					sql.addParameter(value, ctype);
				}
				sb.append(")");
				sql.addFilter(sb.toString());
				logFilter.debug("addMultiFilterSubexpression: values="+Arrays.asList(values));
			}
			else {
				log.warn("unknown filter column: "+col+" [relation="+relationName+"]");
				warnings.add("unknown filter column: "+col);
			}
		}
	}
	
	static void addBooleanFilter(final Set<String> valueSet, Set<String> columns, SQL sql, String compareSymbol, String relationName, List<String> warnings) {
		for(String col: valueSet) {
			if(!validateFilterColumnNames || columns.contains(col)) {
				sql.addFilter(SQL.sqlIdDecorator.get(col)+" "+compareSymbol);
				logFilter.debug("addBooleanFilter: value="+col+" ; symbol="+compareSymbol);
			}
			else {
				log.warn("unknown filter column: "+col+" [relation="+relationName+"]");
				warnings.add("unknown filter column: "+col);
			}
		}
	}
	
	/*
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
	*/

	protected void dumpResultSet(ResultSet rs, RequestSpec reqspec, String schemaName, String queryName, 
			List<String> uniqueColumns, List<FK> importedFKs, List<Constraint> UKs,
			boolean mayApplyLimitOffset, HttpServletResponse resp) 
			throws SQLException, IOException {
		dumpResultSet(rs, reqspec, schemaName, queryName, uniqueColumns, importedFKs, UKs, false, mayApplyLimitOffset, resp, getLimit(reqspec.limit));
	}
	
	//@SuppressWarnings("deprecation")
	void dumpResultSet(ResultSet rs, RequestSpec reqspec, String schemaName, String queryName, 
			List<String> uniqueColumns, List<FK> importedFKs, List<Constraint> UKs, boolean fullKeyDefined,
			boolean mayApplyLimitOffset, HttpServletResponse resp, int limit) 
			throws SQLException, IOException {
		if(mayApplyLimitOffset) {
			//log.info("mayApplyLimitOffset: "+limit+"/"+reqspec.offset);
			rs = new ResultSetLimitOffsetDecorator(rs, limit, reqspec.offset);
		}
		DumpSyntaxInt ds = reqspec.outputSyntax;
		if(ds instanceof DumpSyntaxBuilder) {
			ds = ((DumpSyntaxBuilder) ds).build(schemaName, queryName, uniqueColumns, rs.getMetaData());
		}
		else {
			log.warn("syntax '"+ds.getSyntaxId()+"' isn't a DumpSyntaxBuilder");
			ds.initDump(schemaName, queryName, uniqueColumns, rs.getMetaData());
		}

		if(ds.usesImportedFKs()) {
			ds.setImportedFKs(importedFKs);
		}
		if(ds.usesAllUKs()) {
			ds.setAllUKs(UKs);
		}
		if(fullKeyDefined) {
			ds.setUniqueRow(true);
		}

		log.debug("dump columns ["+queryName+"]:\n\t"+Utils.join(DataDumpUtils.getResultSetColumnsTypes(rs.getMetaData()), ";\n\t"));
		
		if(ds instanceof WebSyntax) {
			WebSyntax ws = (WebSyntax) ds;
			ws.setLimit(limit);
			ws.setOffset(reqspec.offset);
			ws.setBaseHref(getBaseHref(reqspec));
		}
		
		resp.setContentType(ds.getMimeType());
		//resp.addHeader(ResponseSpec.HEADER_CONTENT_TYPE, ds.getMimeType());
		//XXX download? http://stackoverflow.com/questions/398237/how-to-use-the-csv-mime-type
		//resp.addHeader("Content-disposition", "attachment;filename="+table.name+"."+ds.getDefaultFileExtension());
		String contentLocation = (String) reqspec.getAttribute(RequestSpec.ATTR_CONTENTLOCATION);
		if(contentLocation!=null) {
			//log.debug(ResponseSpec.HEADER_CONTENT_LOCATION+": "+contentLocation);
			resp.addHeader(ResponseSpec.HEADER_CONTENT_LOCATION, contentLocation);
		}
		//log.debug("dumpResultSet:: "+reqspec.limit+", "+defaultLimit+", "+maxLimit);
		resp.addHeader(ResponseSpec.HEADER_RESULTSET_LIMIT, String.valueOf(limit));
		
		if(reqspec.isHeadMethod()) {
			//XXX: HEAD & Content-Length: https://stackoverflow.com/a/18925736/616413
			log.debug("HEAD method: response body is empty"); 
			return;
		}
		
		int count = 0;
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
				//log.info("rs count:: "+ rs.getMetaData().getColumnCount());
				ds.dumpRow(rs, count, resp.getWriter());
				count++;
				hasNext = rs.next();
			}
		}
		
		boolean hasMoreRows = false; //XXX: test if there are more rows...
		
		if(count==0) {
			// rfc2616-sec10.html : 10.2.5 204 No Content
			//https://benramsey.com/blog/2008/05/http-status-204-no-content-and-205-reset-content/
			//resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			//resp.addIntHeader("X-ResultSet-Count", count);
		}
		if(ds.acceptsOutputStream()) {
			ds.dumpFooter(count, hasMoreRows, resp.getOutputStream());
		}
		else {
			ds.dumpFooter(count, hasMoreRows, resp.getWriter());
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
		Class<?> colType = types.get(colIndex);
		if(Blob.class.equals( colType )) {
			mimeType = ResponseSpec.MIME_TYPE_OCTET_SREAM; //"application/octet-stream";
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
			//log.debug("rs: "+rs.getClass());
			try {
				if(!rs.isLast()) { // works better with h2, but not with oracle (so: try/catch)
					//if(rs.next()) { // not working ok with h2, oracle
					throw new BadRequestException("ResultSet has more than 1 row ["+queryName+"]");
				}
			}
			catch(SQLException e) { // oracle?
				log.warn("Error at 'isLast()/next()': "+e);
			}
			
			if(reqspec.uniValueMimetypeCol!=null) {
				mimeType = rs.getString(reqspec.uniValueMimetypeCol);
			}
			//resp.addHeader(ResponseSpec.HEADER_CONTENT_TYPE, mimeType);
			resp.setContentType(mimeType);
			if(reqspec.uniValueFilenameCol!=null) {
				filename = rs.getString(reqspec.uniValueFilenameCol);
			}
			if(filename!=null) {
				resp.setHeader(ResponseSpec.HEADER_CONTENT_DISPOSITION, "attachment; filename=" + filename);
			}
			if(reqspec.isHeadMethod()) {
				log.debug("HEAD method: response body is empty"); 
				return;
			}
			
			try {
				// integer/double/date: just read the object/string... derby does not like getCharacterStream() on those
				if(Integer.class.equals( colType ) || Double.class.equals( colType ) || Date.class.equals( colType )) {
					Writer w = resp.getWriter();
					String s = rs.getString(reqspec.uniValueCol);
					if(s!=null) {
						w.write(s);
					}
				}
				else if(String.class.equals( colType )) {
					// "scalar" types / non-binary types
					Reader r = rs.getCharacterStream(reqspec.uniValueCol);
					if(r!=null) {
						IOUtil.pipeCharacterStreams(r, resp.getWriter());
						r.close();
					}
				}
				else {
					// types: Blob, Array, ResultSet, Object
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
			}
			catch(SQLException e) {
				resp.setContentType(MIME_TEXT);
				resp.setHeader(ResponseSpec.HEADER_CONTENT_DISPOSITION, ResponseSpec.HEADERVALUE_CONTENT_DISPOSITION_INLINE);
				throw e;
			}
		}
		else {
			throw new BadRequestException("ResultSet has no rows ["+queryName+"]");
		}
		
		/*if(rs.next()) {
			log.warn("more than 1 row, response may already be committed [query="+queryName+"]");
			throw new BadRequestException("ResultSet has more than 1 row ["+queryName+"]");
		}*/
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
	
	boolean trySqlCommand(Query relation, Connection conn, RequestSpec reqspec, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		String sql = relation.getQuery();

		for(SqlCommand cmd: cmds) {
			if(cmd.matches(sql)) {
				ResultSet rs = null;
				try {
					rs = cmd.run(conn);
					//XXX: mayApplyLimitOffset should be true or false?
					dumpResultSet(rs, reqspec, relation.getSchemaName(), relation.getName(), /*pk*/ null, /*fks*/ null, /*uks*/ null, /*mayApplyLimitOffset*/ true, resp);
				}
				catch(SQLException e) {
					DBUtil.doRollback(conn);
					log.warn("exception in 'trySqlCommand'/"+cmd.getClass().getSimpleName()+": "+e+" ; sql:\n"+sql);
					throw e;
				}
				finally {
					if(rs!=null) { rs.close(); }
				}
				return true;
			}
		}
		
		return false;
	}
	
	protected boolean isStatusObject(String name) {
		if(name==null) {
			return false;
		}
		name = name.toUpperCase();
		
		try {
			DBObjectType.valueOf(name);
			return true;
		}
		catch(IllegalArgumentException e) {
			return false;
		}
	}
	
	/*static DBObjectType statusObject(String name) {
		if(name==null) {
			return null;
		}
		name = name.toUpperCase();
		
		try {
			DBObjectType type = DBObjectType.valueOf(name);
			return type;
		}
		catch(IllegalArgumentException e) {
			return null;
		}
	}*/
	
	public static String getObjectType(DBIdentifiable dbid) {
		//String ret = null;
		if(dbid instanceof Table) {
			return DBObjectType.TABLE.name();
		}
		else if(dbid instanceof Query) {
			return DBObjectType.QUERY.name();
		}
		else if(dbid instanceof View) {
			return DBObjectType.VIEW.name();
		}
		else if(dbid instanceof Relation){
			return DBObjectType.RELATION.name();
		}
		else if(dbid instanceof ExecutableObject) {
			return DBObjectType.EXECUTABLE.name();
		}
		throw new IllegalArgumentException("unknown object for getObjectType(): "+dbid);
		//return ret;
	}
	
	public static String getUsername(Subject currentUser) {
		if(currentUser==null) { return null; }
		return String.valueOf(currentUser.getPrincipal());
	}
	
	int getLimit(Integer requestSpecLimit) {
		return getLimit(requestSpecLimit, defaultLimit, maxLimit);
	}
	
	static int getLimit(Integer requestSpecLimit, Integer defaultLimit, int maxLimit) {
		//log.info("requestSpecLimit="+requestSpecLimit+" ; defaultLimit="+defaultLimit+" ; maxLimit="+maxLimit);
		if(requestSpecLimit!=null) {
			return Math.min(requestSpecLimit, maxLimit);
		}
		if(defaultLimit!=null) {
			return Math.min(defaultLimit, maxLimit);
		}
		return maxLimit;
	}
	
	static void setSqlInfo(HttpServletRequest req, SQL sql) {
		if(sql==null) { return; }
		setSqlInfo(req, sql.getFinalSql(), sql.initialSql);
		//setSqlIndexOfInitial(req, sql.indexOfInitialSql());
		//setSqlLineOfInitial(req, sql.lineOfInitialSql());
	}

	static void setSqlInfo(HttpServletRequest req, String finalSql, String initialSql) {
		setSqlIndexOfInitial(req, SQL.indexOfInitialSql(finalSql, initialSql));
		setSqlLineOfInitial(req, SQL.lineOfInitialSql(finalSql, initialSql));
	}
	
	static void setSqlIndexOfInitial(HttpServletRequest req, int index) {
		req.setAttribute(RequestSpec.ATTR_SQL_INDEX_OF_INITIAL, index);
	}

	static void setSqlLineOfInitial(HttpServletRequest req, int line) {
		req.setAttribute(RequestSpec.ATTR_SQL_LINE_OF_INITIAL, line);
	}
	
	/*@SuppressWarnings("rawtypes")
	static <T> List<String> getSimpleClassNames(List<Class<T>> classes) {
		List<String> l = new ArrayList<String>();
		for(Class c: classes) {
			l.add(c.getSimpleName());
		}
		return l;
	}*/
	
	protected boolean isStrictMode() {
		return false;
	}
	
	protected String getBaseHref(RequestSpec reqspec) {
		return reqspec.getRequestFullContext() + "/" + servletUrlContext + "/"; // + (schemaName!=null?schemaName+".":"") + queryName;
	}

	protected String getBaseHref(HttpServletRequest req) {
		return WebUtils.getRequestFullContext(req) + "/" + servletUrlContext + "/";
	}
	
	public void checkPermission(Subject currentUser, String otype, ActionType atype, String objectName) {
		if(! isPermitted(currentUser, otype, atype, objectName)) {
			ShiroUtils.throwPermissionException(currentUser, otype+":"+atype, objectName);
		}
		/*if(isShiroEnabled() || currentUser!=null || !atype.equals(ActionType.SELECT)) {
			ShiroUtils.checkPermission(currentUser, otype+":"+atype, objectName);
		}*/
	}

	public boolean isPermitted(Subject currentUser, String otype, ActionType atype, String objectName) {
		if(isShiroEnabled() || currentUser!=null ||
			( !atype.equals(ActionType.SELECT) && !atype.equals(ActionType.STATUS ) )
			) {
			return ShiroUtils.isPermitted(currentUser, otype+":"+atype, objectName);
		}
		return true;
	}
	
	// DO NOT call before/on init()
	protected boolean isShiroEnabled() {
		if(shiroEnabled!=null) {
			return shiroEnabled;
		}
		
		boolean shiroEnabledLocal = ShiroUtils.isShiroEnabled();
		boolean authAllowDisabledShiro = Utils.getPropBool(prop, PROP_AUTH_SHIRO_ALLOW_DISABLED, false);

		if( (!authAllowDisabledShiro) && (!shiroEnabledLocal) ) {
			String msg = "shiro not available and disabling not allowed [authAllowDisabledShiro="+authAllowDisabledShiro+";shiroEnabledLocal="+shiroEnabledLocal+"]";
			log.warn(msg);
			throw new InternalServerException(msg);
		}
		
		shiroEnabled = shiroEnabledLocal;
		return shiroEnabled;
	}
	
}
