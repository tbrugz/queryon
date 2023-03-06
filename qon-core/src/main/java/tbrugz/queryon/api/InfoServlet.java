package tbrugz.queryon.api;

import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.AbstractHttpServlet;
import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.PagesServlet;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.UpdatePlugin;
import tbrugz.queryon.auth.AuthActions;
import tbrugz.queryon.auth.UserInfo;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.processor.QOnExecs;
import tbrugz.queryon.processor.QOnQueries;
import tbrugz.queryon.processor.QOnTables;
import tbrugz.queryon.processor.UpdatePluginUtils;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.queryon.util.QOnContextUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.queryon.util.ShiroUtils;
import tbrugz.queryon.util.WebUtils;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.util.IOUtil;
import tbrugz.sqldump.util.SQLUtils;
import tbrugz.sqldump.util.StringUtils;
import tbrugz.sqldump.util.Utils;

public class InfoServlet extends AbstractHttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Log log = LogFactory.getLog(InfoServlet.class);
	
	public static final String INFO_AUTH = "auth";
	public static final String INFO_ENV = "env";
	public static final String INFO_SCHEMAS = "schemas";
	public static final String INFO_SETTINGS = "settings";
	public static final String INFO_STATUS = "status";

	/*
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}
	*/
	
	@Override
	protected void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		if(pathInfo==null || pathInfo.length()<1) { throw new BadRequestException("URL (path-info) must not be null or empty"); }
		
		pathInfo = pathInfo.substring(1);
		//log.info("pathInfo = "+pathInfo+" ; req.getPathInfo() = "+req.getPathInfo()+" ; req.getQueryString() = "+req.getQueryString());
		log.debug("pathInfo = "+pathInfo+" ; method = "+req.getMethod());
		WebUtils.checkHttpMethod(req, QueryOn.METHOD_GET);
		
		if(pathInfo.equals(INFO_AUTH)) {
			WebUtils.writeJsonResponse(getAuth(req), resp);
		}
		else if(pathInfo.equals(INFO_ENV)) {
			WebUtils.writeJsonResponse(getEnv(), resp);
		}
		else if(pathInfo.equals(INFO_SCHEMAS)) {
			WebUtils.writeJsonResponse(getSchemas(req), resp);
		}
		else if(pathInfo.equals(INFO_SETTINGS)) {
			WebUtils.writeJsonResponse(getSettings(), resp);
		}
		else if(pathInfo.equals(INFO_STATUS)) {
			WebUtils.writeJsonResponse(getStatus(req), resp);
		}
		else {
			throw new BadRequestException("Unknown info: "+pathInfo);
		}
	}

	@Override
	public String getDefaultUrlMapping() {
		return "/qinfo/*";
	}

	public Map<String, Object> getEnv() {
		Map<String, Object> ret = new TreeMap<String, Object>();
		
		Set<String> modelSet = SchemaModelUtils.getModelIds(getServletContext());
		ret.put("models", modelSet);
		
		String[] endpoints = { "QueryOn", "QueryOnSchema", "Auth",
				"Diff", "QonPages", "Swagger",
				"OData", "GraphQL", "Soap" };
		String[] classNames = { "queryon.QueryOn", "QueryOnSchemaInstant", "auth.AuthServlet",
				"diff.DiffServlet", "PagesServlet", "SwaggerServlet",
				"ODataServlet", "GraphQlQonServlet", "QonSoapServlet" };
		Map<String, ? extends ServletRegistration> servletRegs = getServletContext().getServletRegistrations();

		Map<String, String> serviceEndpoints = new TreeMap<String, String>(); // new LinkedHashMap<String, String>();
		for(int i=0;i<endpoints.length;i++) {
			for(Map.Entry<String, ? extends ServletRegistration> sre: servletRegs.entrySet()) {
				ServletRegistration sr = sre.getValue();
				if(sr.getClassName().endsWith(classNames[i])) {
					String url = sr.getMappings().iterator().next();
					serviceEndpoints.put(endpoints[i], url);
					//System.out.println("- "+endpoints[i]+":: "+sre.getKey()+": "+sr.getClassName()+" / "+sr.getName()+" / "+url);
				}
			}
		}
		
		ret.put("services", serviceEndpoints);
		
		Map<String, List<String>> updatePluginsMap = new HashMap<String, List<String>>();
		Map<String, List<UpdatePlugin>> updatePlugins = QOnContextUtils.getUpdatePlugins(getServletContext());
		if(updatePlugins!=null) {
			for(Map.Entry<String, List<UpdatePlugin>> e: updatePlugins.entrySet()) {
				List<String> cnames = StringUtils.getClassSimpleNameListFromObjectList(e.getValue());
				updatePluginsMap.put(e.getKey(), cnames);
			}
		}
		
		ret.put("update-plugins", updatePluginsMap);

		return ret;
	}
	
	static String normalize(String s) {
		if(s==null) return "";
		return s;
	}
	
	public Map<String, Object> getSchemas(HttpServletRequest request) {
		Map<String, Object> ret = new TreeMap<String, Object>();

		String modelId = SchemaModelUtils.getModelId(request);

		SchemaModel sm = SchemaModelUtils.getModel(getServletContext(), modelId);
		Set<String> names = new TreeSet<String>();
		if(sm!=null) {
			Set<Table> ts = sm.getTables();
			for(Table t: ts) { names.add(normalize(t.getSchemaName())); }
			Set<View> vs = sm.getViews();
			for(View v: vs) { names.add(normalize(v.getSchemaName())); }
			Set<ExecutableObject> eos = sm.getExecutables();
			for(ExecutableObject eo: eos) { names.add(normalize(eo.getSchemaName())); }
			//XXX: add FKs, indexes, sequences, synonyms, triggers ??
		}
		
		ret.put("modelschemas", names);

		Map<String, List<String>> schemasByModel = QOnContextUtils.getSchemasByModel(getServletContext());
		if(schemasByModel==null) {
			schemasByModel = new HashMap<String, List<String>>();
			QOnContextUtils.setSchemasByModel(getServletContext(), schemasByModel);
		}

		List<String> schemas = schemasByModel.get(modelId);

		if(schemas==null) {
			Properties prop = QOnContextUtils.getProperties(getServletContext());
			//out.write(DBUtil.getDBConnPrefix(prop, modelId));
			if(prop!=null) {
				try {
					//add to SchemaUtils...
					Connection conn = DBUtil.initDBConn(prop, modelId);
					schemas = SQLUtils.getSchemaNames(conn.getMetaData());
					conn.close();
					List<String> s2i = Utils.getStringListFromProp(prop, QueryOn.PROP_SCHEMAS_TO_IGNORE, ",");
					if(s2i!=null) {
						for(int i=schemas.size()-1;i>=0;i--) {
							String s = schemas.get(i);
							if(s2i.contains(s)) {
								schemas.remove(i);
							}
						}
					}
					schemasByModel.put(modelId, schemas);
				}
				catch(Exception e) {
					log.warn("Exception: "+e);
				}
			}
		}
		ret.put("schemas", schemas);

		List<DBObjectType> objtypes = new ArrayList<DBObjectType>();
		if(sm!=null) {
			DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(sm.getSqlDialect());
			//System.out.println("feat: "+feat+" dialect: "+sm.getSqlDialect());
			List<DBObjectType> ots = feat.getSupportedObjectTypes();
			if(ots!=null) { objtypes.addAll( ots ); }
			List<DBObjectType> eots = feat.getExecutableObjectTypes(); 
			if(eots!=null) { objtypes.addAll( eots ); }
		}
		ret.put("objecttypes", objtypes);
		
		return ret;
	}
	
	public Map<String, Object> getSettings() throws IOException {
		Map<String, Object> ret = new TreeMap<String, Object>();

		String[] exposedKeys = {
				"queryon.models",
				"queryon.models.default",
				
				"queryon.qon-queries.table",
				"queryon.qon-tables.table",
				"queryon.qon-execs.table",
				"queryon.qon-pages.table", //PagesServlet.PROP_PREFIX+PagesServlet.SUFFIX_TABLE

				"queryon.filter.allowed",
				"queryon.groupby.allow",
				
				"queryon.web.auth-required",
				"queryon.web.appname",
				"queryon.web.login.show",
				"queryon.web.login-message",

				//"sqldump.datadump.htmlx.dateformat",
				//"sqldump.datadump.json.dateformat",
		};
		
		Pattern[] exposedPatterns = {
			Pattern.compile("queryon\\.qon-(?:queries|tables|execs|pages)@[\\w]+\\.table"),
		};

		//XXX: test if 'queryon.update-plugins' contains qon-tables and/or qon-execs
		String[] defaultValues = {
				null, null,
				QOnQueries.DEFAULT_QUERIES_TABLE, QOnTables.DEFAULT_TABLES_TABLE,
				QOnExecs.DEFAULT_EXECS_TABLE, PagesServlet.DEFAULT_PAGES_TABLE,
				null, null,
				null, null, "true", null,
				//, null, null
				};
		
		if(exposedKeys.length!=defaultValues.length) {
			throw new InternalServerException("exposedKeys.length ["+exposedKeys.length+"] != defaultValues.length ["+defaultValues.length+"]");
		}
		
		int i = 0;
		Properties prop = QOnContextUtils.getProperties(getServletContext());
		if(prop!=null) {
			for(;i<exposedKeys.length;) {
				String k = exposedKeys[i];
				ret.put(k, prop.getProperty(k, defaultValues[i]));
				//if(i>0) { out.write(",\n"); }
				//out.write(sqd.get(k)+": "+gson.toJson(prop.getProperty(k, defaultValues[i])));
				i++;
			}
			for(int j=0;j<exposedPatterns.length;j++) {
				Pattern p = exposedPatterns[j];
				for(Object k: prop.keySet()) {
					String key = (String) k;
					if(p.matcher(key).matches()) {
						//if(i>0) { out.write(",\n"); }
						//out.write(sqd.get(key)+": "+gson.toJson(prop.getProperty(key)));
						ret.put(key, prop.getProperty(key));
						i++;
					}
				}
			}
		}
		
		//syntaxes
		DumpSyntaxUtils dsutils = QOnContextUtils.getDumpSyntaxUtils(getServletContext());
		if(dsutils!=null) {
			//if(i>0) { out.write(",\n"); }
			//out.write(sqd.get("syntax.fileextensions") + ": " + gson.toJson( dsutils.syntaxExtensions ) );
			ret.put("syntax.fileextensions", dsutils.syntaxExtensions);
			i++;
		}
		
		/*InputStream inputStream = application.getResourceAsStream("/META-INF/MANIFEST.MF");
		Manifest mf = new Manifest(inputStream);
		Attributes atts = mf.getMainAttributes();
		out.write("Implementation-Version: " + atts.getValue("Implementation-Version"));
		out.write("Implementation-Build: " + atts.getValue("Implementation-Build"));*/

		//Map<String, String> p2 = new TreeMap<String, String>();
		//p2.load(application.getResourceAsStream("/WEB-INF/classes/queryon-version.properties"));

		try {
			Properties pqon = new Properties();
			pqon.load(IOUtil.getResourceAsStream("/queryon-version.properties"));
			for(Map.Entry<Object, Object> entry: pqon.entrySet()) {
				//p2.put("queryon."+entry.getKey(), String.valueOf(entry.getValue()) );
				ret.put("queryon."+entry.getKey(), String.valueOf(entry.getValue()) );
			}
		}
		catch(RuntimeException e) {} 
		
		try {
			Properties psqld = new Properties();
			psqld.load(IOUtil.getResourceAsStream("/sqldump-version.properties"));
			for(Map.Entry<Object, Object> entry: psqld.entrySet()) {
				//p2.put("sqldump."+entry.getKey(), String.valueOf(entry.getValue()) );
				ret.put("sqldump."+entry.getKey(), String.valueOf(entry.getValue()) );
			}
		}
		catch(RuntimeException e) {} 
		
		return ret;
	}
	
	@SuppressWarnings("unchecked")
	public Map<String, Object> getStatus(HttpServletRequest request) {
		Map<String, Object> ret = new TreeMap<String, Object>();
		Map<String, Object> modelsInfo = new TreeMap<String, Object>();

		Properties prop = QOnContextUtils.getProperties(getServletContext());
		Subject currentUser = ShiroUtils.getSubject(prop, request);
		boolean permitted = ShiroUtils.isPermitted(currentUser, "MANAGE");
		ret.put("permitted", permitted);
		if(permitted) {
			Map<String, SchemaModel> models = SchemaModelUtils.getModels(getServletContext());
			if(models!=null && models.entrySet()!=null) {
				for(Map.Entry<String, SchemaModel> entry: models.entrySet()) {
					String modelId = entry.getKey();
					if(modelId==null) { modelId = "null"; }
					//XXX: filter properties if user not logged...
					modelsInfo.put(modelId, entry.getValue().getMetadata());
					//modelsInfo.put(modelId+".sqldialect", entry.getValue().getSqlDialect());
					
					//qon-tables-warnings
					Map<String, String> tWarnings = (Map<String, String>) getServletContext().getAttribute(QOnTables.ATTR_TABLES_WARNINGS_PREFIX+"."+modelId);
					if(tWarnings!=null && tWarnings.size()>0) {
						modelsInfo.put(modelId+".tables-warnings", tWarnings);
					}
					
					//qon-queries-warnings
					Map<String, String> qWarnings = (Map<String, String>) getServletContext().getAttribute(QOnQueries.ATTR_QUERIES_WARNINGS_PREFIX+"."+modelId);
					if(qWarnings!=null && qWarnings.size()>0) {
						modelsInfo.put(modelId+".queries-warnings", qWarnings);
					}

					//qon-execs-warnings
					Map<String, String> eWarnings = (Map<String, String>) getServletContext().getAttribute(QOnExecs.ATTR_EXECS_WARNINGS_PREFIX+"."+modelId);
					if(eWarnings!=null && eWarnings.size()>0) {
						modelsInfo.put(modelId+".execs-warnings", eWarnings);
					}

					//qon-init-warnings
					Map<String, String> iWarnings = (Map<String, String>) getServletContext().getAttribute(UpdatePluginUtils.ATTR_INIT_WARNINGS_PREFIX+"."+modelId);
					if(iWarnings!=null && iWarnings.size()>0) {
						modelsInfo.put(modelId+".init-warnings", iWarnings);
					}
				}
			}
			Map<String, String> initErrors = (Map<String, String>) getServletContext().getAttribute(QueryOn.ATTR_INIT_ERRORS);
			if(initErrors!=null && initErrors.size()>0) {
				modelsInfo.put("init-errors", initErrors);
			}
			/*
			Throwable initError = (Throwable) getServletContext().getAttribute(QueryOn.ATTR_INIT_ERROR);
			if(initError!=null) {
				modelsInfo.put("init-error", initError.toString());
			}
			*/
			ret.put("models-info", modelsInfo);
		}
		return ret;
	}
	
	public UserInfo getAuth(HttpServletRequest request) {
		Properties prop = QOnContextUtils.getProperties(getServletContext());
		AuthActions beanActions = new AuthActions(prop);
		UserInfo ui = beanActions.getCurrentUserXtra(request);
		return ui;
	}

}
