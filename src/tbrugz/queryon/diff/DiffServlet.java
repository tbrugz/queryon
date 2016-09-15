package tbrugz.queryon.diff;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.AbstractHttpServlet;
import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.NamedTypedDBObject;
import tbrugz.queryon.QOnPrivilegeType;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.QueryOnSchema;
import tbrugz.queryon.QueryOnSchemaInstant;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.exception.NotFoundException;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.queryon.util.ShiroUtils;
import tbrugz.sqldiff.RenameDetector;
import tbrugz.sqldiff.SQLDiff;
import tbrugz.sqldiff.model.ChangeType;
import tbrugz.sqldiff.model.ColumnDiff;
import tbrugz.sqldiff.model.DBIdentifiableDiff;
import tbrugz.sqldiff.model.Diff;
import tbrugz.sqldiff.model.TableDiff;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.Utils;

/*
 * TODOne: apply diff option - add authorization like <type>:APPLYDIFF:<model>:<schema>:<name>
 * TODO: apply ddl: how to create objects in schema other than the connection's (datasource) default??
 * TODO: add data diff!
 */
public class DiffServlet extends AbstractHttpServlet {

	private static final long serialVersionUID = 1L;
	static final Log log = LogFactory.getLog(DiffServlet.class);
	
	final boolean addComments = true;
	final boolean instant = true; //setup 'instant' in init()

	static final String PARAM_MODEL_SOURCE = "modelSource";
	static final String PARAM_MODEL_TARGET = "modelTarget";
	static final String PARAM_DO_APPLY = "doApply";
	static final String PARAM_APPLY_MESSAGE = "applyMessage";
	
	public static final String MIME_SQL = "text/plain"; //"application/sql"; - browsers may "download"
	
	public static final String PROP_PRE_APPLY_HOOKS = "queryon.diff.apply.pre-hooks";
	public static final String PROP_POST_APPLY_HOOKS = "queryon.diff.apply.post-hooks";

	public static final String HOOKS_APPLY_MODELS_SUFFIX = ".applymodels";
	
	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		List<String> partz = QueryOnSchema.parseQS(req);
		if(partz.size()<2) {
			throw new BadRequestException("Malformed URL");
		}
		log.info("partz: "+partz);
		
		NamedTypedDBObject obj = NamedTypedDBObject.getObject(partz);
		
		Properties prop = (Properties) req.getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		// TODOne: diff authorization
		// XXX add <type>:DIFF authorization instead of <type>:SHOW ?
		Subject currentUser = ShiroUtils.getSubject(prop, req);
		ShiroUtils.checkPermission(currentUser, obj.getType()+":"+QOnPrivilegeType.SHOW, obj.getFullObjectName());
		
		String modelIdSource = SchemaModelUtils.getModelId(req, PARAM_MODEL_SOURCE, false);
		String modelIdTarget = SchemaModelUtils.getModelId(req, PARAM_MODEL_TARGET, false);
		if(modelIdSource.equals(modelIdTarget)) {
			log.warn("equal models being compared [id="+modelIdSource+"], no diffs can be generated");
		}

		boolean previousDBIdDiffAddComments = DBIdentifiableDiff.addComments;
		
		boolean doApply = getDoApply(req);
		List<ApplyHook> preHooks = new ArrayList<ApplyHook>();
		List<ApplyHook> postHooks = new ArrayList<ApplyHook>();
		if(doApply) {
			// XXX do NOT allow HTTP GET method...
			String permissionPattern = obj.getType()+":"+QOnPrivilegeType.APPLYDIFF+":"+modelIdSource;
			log.info("aplying to '"+modelIdSource+"'... permission: "+permissionPattern+" :: "+obj.getFullObjectName());
			ShiroUtils.checkPermission(currentUser, permissionPattern, obj.getFullObjectName());
			if(!instant) {
				throw new BadRequestException("cannot apply diff to non-instant QOS");
			}
			DBIdentifiableDiff.addComments = false;
			
			preHooks = processHooks(prop, PROP_PRE_APPLY_HOOKS);
			postHooks = processHooks(prop, PROP_POST_APPLY_HOOKS);
		}
		else {
			DBIdentifiableDiff.addComments = true;
		}
		
		SchemaModel modelSource = SchemaModelUtils.getModel(req.getServletContext(), modelIdSource);
		if(modelSource==null) {
			throw new BadRequestException("Unknown model (source): "+modelIdSource);
		}
		SchemaModel modelTarget = SchemaModelUtils.getModel(req.getServletContext(), modelIdTarget);
		if(modelTarget==null) {
			throw new BadRequestException("Unknown model (target): "+modelIdTarget);
		}

		QueryOnSchema qos = getQOS();
		
		DBIdentifiable dbidSource = null;
		DBIdentifiable dbidTarget = null;
		
		String diffDialect = null;
		
		try {
			dbidSource = qos.getObject(obj.getType(), obj.getSchemaName(), obj.getName(), modelSource, prop, modelIdSource);
			diffDialect = qos.getLastDialect();
		}
		catch(NotFoundException e) {
			log.info("not found: "+e);
		}
		try {
			dbidTarget = qos.getObject(obj.getType(), obj.getSchemaName(), obj.getName(), modelTarget, prop, modelIdTarget);
		}
		catch(NotFoundException e) {
			log.info("not found: "+e);
		}
		
		/*if(dbidSource==null) {
			throw new NotFoundException("Object "+obj+" not found on model "+modelIdSource);
		}
		if(dbidTarget==null) {
			throw new NotFoundException("Object "+obj+" not found on model "+modelIdTarget);
		}*/
		
		if(dbidSource==null && dbidTarget==null) {
			throw new NotFoundException("Object "+obj+" not found on both '"+modelIdSource+"' and '"+modelIdTarget+"' models");
		}

		final DBMSResources res = DBMSResources.instance();
		final DBMSFeatures feat = res.getSpecificFeatures(diffDialect);
		log.debug("dialect: "+diffDialect+" feat: "+feat);
		ColumnDiff.updateFeatures(feat);
		
		List<Diff> diffs = null;
		
		if(dbidSource==null) {
			Diff diff = null;
			if(dbidTarget instanceof Table) {
				diff = new TableDiff(ChangeType.ADD, (Table)dbidTarget);
			}
			else {
				diff = new DBIdentifiableDiff(ChangeType.ADD, null, dbidTarget, null);
			}
			diffs = newSingleElemList(diff);
		}
		else if(dbidTarget==null) {
			Diff diff = null;
			if(dbidSource instanceof Table) {
				diff = new TableDiff(ChangeType.DROP, (Table) dbidSource);
			}
			else {
				diff = new DBIdentifiableDiff(ChangeType.DROP, dbidSource, null, null);
			}
			diffs = newSingleElemList(diff);
		}
		else if(dbidSource instanceof Table && dbidTarget instanceof Table) {
			Table origTable = (Table) dbidSource;
			Table newTable = (Table) dbidTarget;
			diffs = TableDiff.tableDiffs(origTable, newTable);
			if(diffs.size()==0) {
				log.info("no diffs found");
				//XXX: return 404? maybe not...
			}
			else {
				//XXXxx: option to do column & constraint rename detection?
				boolean doRenameDetection = Utils.getPropBool(prop, SQLDiff.PROP_DO_RENAMEDETECTION, SQLDiff.DEFAULT_DO_RENAME_DETECTION);
				if(doRenameDetection) {
					int renames = doTableRenames(diffs, prop);
					if(renames>0) {
						log.debug(renames+" renames in table "+dbidSource+" diff");
					}
				}
			}
			//SchemaDiff.logInfo(diffs);
		}
		else {
			Diff diff = new DBIdentifiableDiff(ChangeType.REPLACE, dbidSource, dbidTarget, null);
			diffs = newSingleElemList(diff);
		}
		
		if(doApply) {
			//String message = ":message: ? \" see '\" zzz";
			String message = req.getParameter(PARAM_APPLY_MESSAGE);
			// pre-hooks
			for(ApplyHook ah: preHooks) {
				ah.setProperties(prop);
				List<String> hooksApplyModels = Utils.getStringListFromProp(prop, ah.getPropPrefix()+HOOKS_APPLY_MODELS_SUFFIX, ",");
				if(hooksApplyModels.contains(modelIdSource)) {
					String ret = ah.run(new ApplyHook.ApplyMessage(message, String.valueOf(currentUser.getPrincipal()),
							String.valueOf(obj.getType()), obj.getSchemaName(), obj.getName(),
							modelIdTarget, modelIdSource));
					log.info("pre-hook: ret:\n"+ret);
				}
			}
			applyDiffs(diffs, prop, modelIdSource, resp);
			// post-hooks
			// XXX use queue/async in post-hooks?
			for(ApplyHook ah: postHooks) {
				ah.setProperties(prop); 
				List<String> hooksApplyModels = Utils.getStringListFromProp(prop, ah.getPropPrefix()+HOOKS_APPLY_MODELS_SUFFIX, ",");
				if(hooksApplyModels.contains(modelIdSource)) {
					String ret = ah.run(new ApplyHook.ApplyMessage(message, String.valueOf(currentUser.getPrincipal()),
							String.valueOf(obj.getType()), obj.getSchemaName(), obj.getName(),
							modelIdTarget, modelIdSource));
					log.info("post-hook: ret:\n"+ret);
				}
			}
		}
		else {
			dumpDiffs(diffs, resp);
		}
		
		DBIdentifiableDiff.addComments = previousDBIdDiffAddComments;
		resp.getWriter().flush();
	}
	
	List<Diff> newSingleElemList(Diff e) {
		List<Diff> l = new ArrayList<Diff>(); l.add(e);
		return l;
	}
	
	QueryOnSchema getQOS() {
		if(instant) {
			return new QueryOnSchemaInstant(); // XXXxx use factory? new QueryOnSchema() / QueryOnSchemaInstant() ...
		}
		throw new IllegalStateException("getQOS: only 'true' instant allowed");
		//return new QueryOnSchema();
	}
	
	boolean getDoApply(HttpServletRequest req) {
		String apply = req.getParameter(PARAM_DO_APPLY);
		return apply!=null && apply.equals("true");
	}

	void dumpDiffs(List<Diff> diffs, HttpServletResponse resp) throws IOException {
		resp.setContentType(MIME_SQL);
		for(Diff d: diffs) {
			resp.getWriter().write(d.getDiff());
			resp.getWriter().write((d.getObjectType().isExecutableType() && d.getChangeType().equals(ChangeType.ADD))? "\n" : ";\n");
		}
	}
	
	void applyDiffs(List<Diff> diffs, Properties prop, String modelId, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException {
		Connection conn = null;
		String sql = null;
		try {
			conn = DBUtil.initDBConn(prop, modelId);
			resp.setContentType(MIME_SQL);
			for(Diff d: diffs) {
				List<String> sqls = d.getDiffList();
				for(String s: sqls) {
					Statement st = conn.createStatement();
					sql = s;
					//sql = d.getDiff();
					boolean retIsRs = st.execute(sql);
					int count = st.getUpdateCount();
					resp.getWriter().write(sql);
					if(addComments) {
						resp.getWriter().write(" /* model="+modelId+" ; ret=[isRS="+retIsRs+",count="+count+"] */");
					}
					resp.getWriter().write((d.getObjectType().isExecutableType() && d.getChangeType().equals(ChangeType.ADD))? "\n" : ";\n");
				}
			}
			conn.commit(); //XXX: commit after postHooks have run?
		}
		catch(SQLException e) {
			throw new BadRequestException("Error: ["+e+"] ; sql =\n"+sql);
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}
	
	int doTableRenames(List<Diff> diffs, Properties prop) {
		double minSimilarity = 0.5;
		int renames = 0;
		List<String> renameTypes = Utils.getStringListFromProp(prop, SQLDiff.PROP_RENAMEDETECT_TYPES, ",", RenameDetector.RENAME_TYPES);
		if(renameTypes==null || renameTypes.contains(DBObjectType.COLUMN.toString())) {
			//log.info("doTableRenames[COLUMN]: diffs="+diffs);
			List<ColumnDiff> lcd = new ArrayList<ColumnDiff>();
			for(int i=diffs.size()-1 ; i>=0 ; i--) {
				Diff d = diffs.get(i);
				if(d instanceof ColumnDiff) { lcd.add((ColumnDiff)d); diffs.remove(i); }
			}
			renames += RenameDetector.detectAndDoColumnRenames(lcd, minSimilarity);
			diffs.addAll(lcd);
		}
		if(renameTypes==null || renameTypes.contains(DBObjectType.CONSTRAINT.toString())) {
			//log.info("doTableRenames[CONSTRAINT]: diffs="+diffs);
			List<DBIdentifiableDiff> ldd = new ArrayList<DBIdentifiableDiff>();
			for(int i=diffs.size()-1 ; i>=0 ; i--) {
				Diff d = diffs.get(i);
				if(d instanceof DBIdentifiableDiff) { ldd.add((DBIdentifiableDiff)d); diffs.remove(i); }
			}
			renames += RenameDetector.detectAndDoConstraintRenames(ldd, minSimilarity);
			diffs.addAll(ldd);
		}
		return renames;
	}
	
	List<ApplyHook> processHooks(Properties prop, String key) {
		List<ApplyHook> hooks = new ArrayList<ApplyHook>();
		List<String> hookclasses = Utils.getStringListFromProp(prop, key, ",");
		if(hookclasses!=null) {
			for(String s: hookclasses) {
				ApplyHook ah = null;
				if(s.contains(":")) {
					String[] clazzpartz = s.split(":");
					ah = (ApplyHook) Utils.getClassInstance(clazzpartz[1].trim(), "tbrugz.queryon.diff.hook");
					if(ah==null) {
						throw new InternalServerException("hook class not found: "+clazzpartz[1].trim());
					}
					ah.setId(clazzpartz[0].trim());
				}
				else {
					ah = (ApplyHook) Utils.getClassInstance(s.trim(), "tbrugz.queryon.diff.hook");
					if(ah==null) {
						throw new InternalServerException("hook class not found: "+s.trim());
					}
				}
				hooks.add(ah);
			}
		}
		return hooks;
	}
}
