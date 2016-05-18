package tbrugz.queryon.diff;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.AbstractHttpServlet;
import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.QueryOnSchema;
import tbrugz.queryon.QueryOn.ActionType;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.queryon.util.ShiroUtils;
import tbrugz.sqldiff.SQLDiff;
import tbrugz.sqldump.JDBCSchemaGrabber;
import tbrugz.sqldump.SQLDump;
import tbrugz.sqldump.dbmd.AbstractDBMSFeatures;
import tbrugz.sqldump.util.CategorizedOut;
import tbrugz.sqldump.util.ParametrizedProperties;
import tbrugz.sqldump.util.Utils;

/*
 * debugDumpModel action:
 * <servlet-context>/<schemas>/<types>/<syntax:xml|json|sql>?model=<modelId>&action=debugDumpModel
 * - syntaxes: xml, json
 * 
 * XXX: create AbstractDDLDumpServlet, DdlManyServlet
 */
public class DiffManyServlet extends AbstractHttpServlet {

	private static final long serialVersionUID = 1L;
	static final Log log = LogFactory.getLog(DiffManyServlet.class);
	
	public static final String PARAM_ACTION = "action";
	
	public static final String ACTION_DUMP_DEBUG = "debugDumpModel";
	
	public static final String MIME_XML = "application/xml";
	public static final String MIME_JSON = "application/json";
	public static final String MIME_SQL = "text/plain"; // "application/sql"?; - browsers may "download"
	public static final String MIME_PATCH = "text/plain"; // http://stackoverflow.com/questions/5160944/proper-mime-type-for-patch-files
	
	public static final String SYNTAX_XML = "xml";
	public static final String SYNTAX_JSON = "json";
	public static final String SYNTAX_SQL = "sql";
	public static final String SYNTAX_PATCH = "patch";

	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException, JAXBException, XMLStreamException, InterruptedException, ExecutionException {
		
		List<String> partz = QueryOnSchema.parseQS(req);
		if(partz.size()<3) {
			throw new BadRequestException("Malformed URL: "+partz+" [3 parts needed]");
		}
		log.info("partz: "+partz);
		
		String schemas = partz.get(0); // comma separated schema names
		//List<String> schemasList = Utils.getStringList(schemasStr, ",");
		String types = partz.get(1);   // comma separated types to diff
		String syntax = partz.get(2);  // valid:: json, xml, patch, sql
		
		Properties prop = (Properties) req.getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		Subject currentUser = ShiroUtils.getSubject(prop);
		ShiroUtils.checkPermission(currentUser, ActionType.SELECT_ANY.name(), ActionType.SELECT_ANY.name());
		
		// debug mode
		String action = req.getParameter(PARAM_ACTION);
		if(action!=null) {
			if(ACTION_DUMP_DEBUG.equals(action)) {
				String modelId = SchemaModelUtils.getModelId(req, SchemaModelUtils.PARAM_MODEL, false);
				doDumpModelDebug(modelId, prop, schemas, types, syntax, resp);
				return;
			}
			else {
				throw new BadRequestException("unknown action: "+action);
			}
		}
		
		String modelIdSource = SchemaModelUtils.getModelId(req, DiffServlet.PARAM_MODEL_SOURCE, false);
		String modelIdTarget = SchemaModelUtils.getModelId(req, DiffServlet.PARAM_MODEL_TARGET, false);
		if(modelIdSource.equals(modelIdTarget)) {
			log.warn("equal models being compared [id="+modelIdSource+"], no diffs can be generated");
		}
		
		try {
			//XXX params: schemas, objectTypes - define properties to sqldump
			
			ParametrizedProperties pp = new ParametrizedProperties();
			pp.putAll(prop);
			pp.put("sqldiff.target", modelIdTarget);
			pp.put("sqldiff.source", modelIdSource);
			pp.put("sqldiff."+modelIdTarget+".connpropprefix", DBUtil.getDBConnPrefix(prop, modelIdTarget));
			pp.put("sqldiff."+modelIdSource+".connpropprefix", DBUtil.getDBConnPrefix(prop, modelIdSource));
			//pp.put(DBUtil.getDBConnPrefix(prop, modelIdSource)+".diffgrabclass","JDBCSchemaGrabber");
			//pp.put(DBUtil.getDBConnPrefix(prop, modelIdTarget)+".diffgrabclass","JDBCSchemaGrabber");
			pp.put(DBUtil.getDBConnPrefix(prop, modelIdTarget)+".grabclass","JDBCSchemaGrabber");
			pp.put(DBUtil.getDBConnPrefix(prop, modelIdSource)+".grabclass","JDBCSchemaGrabber");
			pp.put("sqldiff.dodatadiff","false");
			String types2diff = types;
					/*.replaceAll("\\bFUNCTION\\b", "EXECUTABLE")
					.replaceAll("\\bPROCEDURE\\b", "EXECUTABLE")
					.replaceAll("\\bPACKAGE_BODY\\b", "EXECUTABLE")
					.replaceAll("\\bPACKAGE\\b", "EXECUTABLE")
					;*/
			pp.put("sqldiff.typestodiff", types2diff);
			
			pp.put("sqldiff.dodatadiff","false");
			
			setupDumpProperties(pp, schemas, types);
			
			dump(pp, syntax, resp);
		}
		finally {
			resp.getWriter().flush();
		}
	}
	
	void setupDumpProperties(Properties pp, String schemas, String types) {
		pp.put("sqldump.schemagrab.proceduresandfunctions", "false");
		pp.put("sqldump.schemagrab.db-specific-features", "true");
		pp.put(AbstractDBMSFeatures.PROP_GRAB_CONSTRAINTS_XTRA, "false"); //XXX: add xtra-constraints?
		
		//sqldump properties...
		pp.put("sqldump.schemagrab.schemas", schemas); //XXX param: schemas, validate?

		List<String> typesList = Utils.getStringList(types, ",");
		setPropForTypes(pp, typesList);
	}
	
	void dump(Properties pp, String syntax, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException, JAXBException, XMLStreamException, InterruptedException, ExecutionException {
		SQLDiff sqldiff = new SQLDiff();
		sqldiff.setProperties(pp);
		
		//XXX: return SchemaDiff.logInfo()?
		
		//XXX: option to ignore, or not, trailing whitespaces?
		
		sqldiff.procProterties();
		// using 'syntax' param
		if(SYNTAX_XML.equals(syntax)) {
			sqldiff.setXmlWriter(resp.getWriter());
			resp.setContentType(MIME_XML);
		}
		else if(SYNTAX_JSON.equals(syntax)) {
			sqldiff.setJsonWriter(resp.getWriter());
			resp.setContentType(MIME_JSON);
		}
		else if(SYNTAX_PATCH.equals(syntax)) {
			sqldiff.setPatchWriter(resp.getWriter());
			resp.setContentType(MIME_PATCH);
		}
		else if(SYNTAX_SQL.equals(syntax)) {
			// "sql": every DDL in plain text...
			CategorizedOut cout = new CategorizedOut(resp.getWriter(), null);
			sqldiff.setCategorizedOut(cout);
			resp.setContentType(MIME_SQL);
		}
		else {
			throw new BadRequestException("unknown syntax: "+syntax);
		}
		
		int lastDiffCount = sqldiff.doIt();
		log.info("diff count: "+lastDiffCount);
	}
	
	void setPropForTypes(Properties prop, List<String> typesToGrab) {
		Set<String> trueProps = new HashSet<String>();
		
		String[] types = { "TABLE", "FK", "VIEW", "INDEX", "TRIGGER",
				"SEQUENCE", "SYNONYM", "GRANT", "MATERIALIZED_VIEW", "FUNCTION",
				"PACKAGE", "PACKAGE_BODY", "PROCEDURE" };
				
		String[] props = { "sqldump.schemagrab.tables", "sqldump.schemagrab.fks" /* exportedfks?*/, AbstractDBMSFeatures.PROP_GRAB_VIEWS, AbstractDBMSFeatures.PROP_GRAB_INDEXES, AbstractDBMSFeatures.PROP_GRAB_TRIGGERS,
				AbstractDBMSFeatures.PROP_GRAB_SEQUENCES, AbstractDBMSFeatures.PROP_GRAB_SYNONYMS, JDBCSchemaGrabber.PROP_SCHEMAGRAB_GRANTS, AbstractDBMSFeatures.PROP_GRAB_MATERIALIZED_VIEWS, AbstractDBMSFeatures.PROP_GRAB_EXECUTABLES,
				AbstractDBMSFeatures.PROP_GRAB_EXECUTABLES, AbstractDBMSFeatures.PROP_GRAB_EXECUTABLES, AbstractDBMSFeatures.PROP_GRAB_EXECUTABLES };
		/*
		String[] props = { "sqldump.schemagrab.tables", "sqldump.schemagrab.fks", "sqldump.dbspecificfeatures.grabviews", "sqldump.dbspecificfeatures.grabindexes", "sqldump.dbspecificfeatures.grabtriggers",
				"sqldump.dbspecificfeatures.grabsequences", "sqldump.dbspecificfeatures.grabsynonyms", "sqldump.schemagrab.grants", null, "sqldump.dbspecificfeatures.grabexecutables",
				"sqldump.dbspecificfeatures.grabexecutables", "sqldump.dbspecificfeatures.grabexecutables", "sqldump.dbspecificfeatures.grabexecutables" };
		*/
		
		for(int i=0;i<types.length; i++) {
			String t = types[i];
			if(typesToGrab.contains(t)) {
				trueProps.add(props[i]);
			}
		}
		
		for(int i=0;i<props.length; i++) {
			String t = props[i];
			if(t==null) { continue; }
			if(trueProps.contains(t)) {
				prop.setProperty(t, "true");
			}
			else {
				prop.setProperty(t, "false");
			}
		}
	}
	
	void doDumpModelDebug(String modelId, Properties prop, String schemas, String types, String syntax, HttpServletResponse resp) {
		ParametrizedProperties pp = new ParametrizedProperties();
		pp.putAll(prop);
		pp.put("sqldump.connpropprefix", DBUtil.getDBConnPrefix(prop, modelId));
		pp.put("sqldump.grabclass", "JDBCSchemaGrabber");
		
		//SchemaModelDumper md = null;
		if(SYNTAX_XML.equals(syntax)) {
			//md = new JAXBSchemaXMLSerializer();
			pp.put("sqldump.processingclasses", "JAXBSchemaXMLSerializer");
			resp.setContentType(MIME_XML);
		}
		else if(SYNTAX_JSON.equals(syntax)) {
			//md = new JSONSchemaSerializer();
			pp.put("sqldump.processingclasses", "JSONSchemaSerializer");
			resp.setContentType(MIME_JSON);
		}
		else if(SYNTAX_SQL.equals(syntax)) {
			//md = new SchemaModelScriptDumper();
			pp.put("sqldump.processingclasses", "SchemaModelScriptDumper");
			resp.setContentType(MIME_SQL);
		}
		else {
			throw new BadRequestException("unknown syntax: "+syntax);
		}
		//pp.put("sqldump.processingclasses", md.getClass().getSimpleName());
		//resp.setContentType(md.getMimeType());
		//log.info("class: "+md.getClass().getSimpleName()+" content-type: "+md.getMimeType());
		
		setupDumpProperties(pp, schemas, types);
		
		SQLDump sqldump = new SQLDump();
		try {
			sqldump.doMain(null, pp, null, resp.getWriter());
		} catch (Exception e) {
			log.warn("Error invoking sqldump", e);
			throw new BadRequestException("Error invoking sqldump", e);
		}
	}

}
