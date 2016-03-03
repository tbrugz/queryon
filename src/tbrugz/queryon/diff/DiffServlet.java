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
import tbrugz.queryon.DBUtil;
import tbrugz.queryon.NamedTypedDBObject;
import tbrugz.queryon.QOnPrivilegeType;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.QueryOnSchema;
import tbrugz.queryon.QueryOnSchemaInstant;
import tbrugz.queryon.SchemaModelUtils;
import tbrugz.queryon.ShiroUtils;
import tbrugz.queryon.exception.NotFoundException;
import tbrugz.sqldiff.model.ChangeType;
import tbrugz.sqldiff.model.ColumnDiff;
import tbrugz.sqldiff.model.DBIdentifiableDiff;
import tbrugz.sqldiff.model.Diff;
import tbrugz.sqldiff.model.TableDiff;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.util.ConnectionUtil;

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
	
	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		List<String> partz = QueryOnSchema.parseQS(req);
		if(partz.size()<2) {
			throw new BadRequestException("Malformed URL");
		}
		log.info("partz: "+partz);
		
		NamedTypedDBObject obj = NamedTypedDBObject.getObject(partz);
		
		Properties prop = (Properties) req.getSession().getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		// TODOne: diff authorization
		// XXX add <type>:DIFF authorization instead of <type>:SHOW ?
		Subject currentUser = ShiroUtils.getSubject(prop);
		ShiroUtils.checkPermission(currentUser, obj.getType()+":"+QOnPrivilegeType.SHOW, obj.getFullObjectName());
		
		String modelIdSource = SchemaModelUtils.getModelId(req, PARAM_MODEL_SOURCE);
		String modelIdTarget = SchemaModelUtils.getModelId(req, PARAM_MODEL_TARGET);
		if(modelIdSource.equals(modelIdTarget)) {
			log.warn("equal models being compared [id="+modelIdSource+"], no diffs can be generated");
		}

		boolean previousDBIdDiffAddComments = DBIdentifiableDiff.addComments;
		
		boolean doApply = getDoApply(req);
		if(doApply) {
			// XXX do NOT allow HTTP GET method...
			String permissionPattern = obj.getType()+":"+QOnPrivilegeType.APPLYDIFF+":"+modelIdSource;
			log.info("aplying to '"+modelIdSource+"'... permission: "+permissionPattern+" :: "+obj.getFullObjectName());
			ShiroUtils.checkPermission(currentUser, permissionPattern, obj.getFullObjectName());
			if(!instant) {
				throw new BadRequestException("cannot apply diff to non-instant QOS");
			}
			DBIdentifiableDiff.addComments = false;
		}
		else {
			DBIdentifiableDiff.addComments = true;
		}
		
		SchemaModel modelSource = SchemaModelUtils.getModel(req.getSession().getServletContext(), modelIdSource);
		if(modelSource==null) {
			throw new BadRequestException("Unknown model (source): "+modelIdSource);
		}
		SchemaModel modelTarget = SchemaModelUtils.getModel(req.getSession().getServletContext(), modelIdTarget);
		if(modelTarget==null) {
			throw new BadRequestException("Unknown model (target): "+modelIdTarget);
		}

		QueryOnSchema qos = getQOS();
		
		DBIdentifiable dbidSource = null;
		DBIdentifiable dbidTarget = null;
		
		try {
			dbidSource = qos.getObject(obj.getType(), obj.getSchemaName(), obj.getName(), modelSource, prop, modelIdSource);
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
		
		final DBMSResources res = DBMSResources.instance();
		final DBMSFeatures feat = res.getSpecificFeatures(qos.getLastDialect());
		log.debug("dialect: "+qos.getLastDialect()+" feat: "+feat);
		ColumnDiff.updateFeatures(feat);
		//res.updateDbId(qos.getLastDialect());
		
		/*if(dbidSource==null) {
			throw new NotFoundException("Object "+obj+" not found on model "+modelIdSource);
		}
		if(dbidTarget==null) {
			throw new NotFoundException("Object "+obj+" not found on model "+modelIdTarget);
		}*/
		
		if(dbidSource==null && dbidTarget==null) {
			throw new NotFoundException("Object "+obj+" not found on both '"+modelIdSource+"' and '"+modelIdTarget+"' models");
		}
		
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
			//SchemaDiff.logInfo(diffs);
		}
		else {
			Diff diff = new DBIdentifiableDiff(ChangeType.REPLACE, dbidSource, dbidTarget, null);
			diffs = newSingleElemList(diff);
		}

		if(doApply) {
			applyDiffs(diffs, prop, modelIdSource, resp);
		}
		else {
			dumpDiffs(diffs, resp);
		}
		
		DBIdentifiableDiff.addComments = previousDBIdDiffAddComments;
	}
	
	List<Diff> newSingleElemList(Diff e) {
		List<Diff> l = new ArrayList<Diff>(); l.add(e);
		return l;
	}
	
	QueryOnSchema getQOS() {
		if(instant) {
			return new QueryOnSchemaInstant(); // XXXxx use factory? new QueryOnSchema() / QueryOnSchemaInstant() ...
		}
		return new QueryOnSchema();
	}
	
	boolean getDoApply(HttpServletRequest req) {
		String apply = req.getParameter(PARAM_DO_APPLY);
		return apply!=null && apply.equals("true");
	}

	void dumpDiffs(List<Diff> diffs, HttpServletResponse resp) throws IOException {
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
		}
		catch(SQLException e) {
			throw new BadRequestException("Error: ["+e+"] ; sql =\n"+sql);
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}
	
}
