package tbrugz.queryon;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

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

/*
 * TODOne: apply diff option - add authorization like <type>:APPLYDIFF:<model>:<schema>:<name>
 * TODO: apply ddl: how to create objects in schema other than the connection's (datasource) default??
 * TODO: add data diff!
 */
public class DiffServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	static final Log log = LogFactory.getLog(DiffServlet.class);
	
	final boolean instant = true; //setup 'instant' in init()

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		//throw new BadRequestException("Only POST allowed", HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			doProcess(req, resp);
		} catch(BadRequestException e) {
			log.warn("BadRequestException: "+e.getMessage());
			resp.setStatus(e.getCode());
			resp.getWriter().write(e.getMessage());
		} /*catch (ServletException e) {
			//e.printStackTrace();
			throw e;
		} */ catch (Exception e) {
			//e.printStackTrace();
			throw new ServletException(e);
		}
	}

	void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
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
		ShiroUtils.checkPermission(currentUser, obj.getType()+":SHOW", obj.getFullObjectName());
		
		String modelIdFrom = SchemaModelUtils.getModelId(req, "modelFrom");
		String modelIdTo = SchemaModelUtils.getModelId(req, "modelTo");
		if(modelIdFrom.equals(modelIdTo)) {
			log.warn("equal models being compared [id="+modelIdFrom+"], no diffs can be generated");
		}

		boolean doApply = getDoApply(req);
		if(doApply) {
			String permissionPattern = obj.getType()+":APPLYDIFF:"+modelIdFrom;
			log.info("aplying to '"+modelIdFrom+"'... permission: "+permissionPattern+" :: "+obj.getFullObjectName());
			ShiroUtils.checkPermission(currentUser, permissionPattern, obj.getFullObjectName());
			if(!instant) {
				throw new BadRequestException("cannot apply diff to non-instant QOS");
			}
			DBIdentifiableDiff.addComments = false;
		}
		else {
			DBIdentifiableDiff.addComments = true;
		}
		
		SchemaModel modelFrom = SchemaModelUtils.getModel(req.getSession().getServletContext(), modelIdFrom);
		if(modelFrom==null) {
			throw new BadRequestException("Unknown model (from): "+modelIdFrom);
		}
		SchemaModel modelTo = SchemaModelUtils.getModel(req.getSession().getServletContext(), modelIdTo);
		if(modelTo==null) {
			throw new BadRequestException("Unknown model (to): "+modelIdTo);
		}

		QueryOnSchema qos = getQOS();
		
		DBIdentifiable dbidFrom = null;
		DBIdentifiable dbidTo = null;
		
		//XXX: add NotFoundException (404) ?
		try {
			dbidFrom = qos.getObject(obj.getType(), obj.getSchemaName(), obj.getName(), modelFrom, prop, modelIdFrom);
		}
		catch(BadRequestException e) {
			log.info("not found(?): "+e);
		}
		try {
			dbidTo = qos.getObject(obj.getType(), obj.getSchemaName(), obj.getName(), modelTo, prop, modelIdTo);
		}
		catch(BadRequestException e) {
			log.info("not found(?): "+e);
		}
		
		final DBMSResources res = DBMSResources.instance();
		final DBMSFeatures feat = res.getSpecificFeatures(qos.getLastDialect());
		log.debug("dialect: "+qos.getLastDialect()+" feat: "+feat);
		ColumnDiff.updateFeatures(feat);
		//res.updateDbId(qos.getLastDialect());
		
		/*if(dbidFrom==null) {
			throw new BadRequestException("Object "+obj+" not found on model "+modelIdFrom);
		}
		if(dbidTo==null) {
			throw new BadRequestException("Object "+obj+" not found on model "+modelIdTo);
		}*/
		
		if(dbidFrom==null && dbidTo==null) {
			throw new BadRequestException("Object "+obj+" not found on both '"+modelIdFrom+"' and '"+modelIdTo+"' models");
		}
		
		List<Diff> diffs = null;
		
		if(dbidFrom==null) {
			Diff diff = null;
			if(dbidTo instanceof Table) {
				diff = new TableDiff(ChangeType.ADD, (Table)dbidTo);
			}
			else {
				diff = new DBIdentifiableDiff(ChangeType.ADD, null, dbidTo, null);
			}
			diffs = newSingleElemList(diff);
		}
		else if(dbidTo==null) {
			Diff diff = null;
			if(dbidFrom instanceof Table) {
				diff = new TableDiff(ChangeType.DROP, (Table) dbidFrom);
			}
			else {
				diff = new DBIdentifiableDiff(ChangeType.DROP, dbidFrom, null, null);
			}
			diffs = newSingleElemList(diff);
		}
		else if(dbidFrom instanceof Table && dbidTo instanceof Table) {
			Table origTable = (Table) dbidFrom;
			Table newTable = (Table) dbidTo;
			diffs = TableDiff.tableDiffs(origTable, newTable);
			if(diffs.size()==0) {
				log.info("no diffs found");
				//XXX: return 404? maybe not...
			}
			//SchemaDiff.logInfo(diffs);
		}
		else {
			Diff diff = new DBIdentifiableDiff(ChangeType.REPLACE, dbidFrom, dbidTo, null);
			diffs = newSingleElemList(diff);
		}

		if(doApply) {
			applyDiffs(diffs, prop, modelIdFrom, resp);
		}
		else {
			dumpDiffs(diffs, resp);
		}
		
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
		String apply = req.getParameter("doApply");
		return apply!=null && apply.equals("true");
	}

	void dumpDiffs(List<Diff> diffs, HttpServletResponse resp) throws IOException {
		for(Diff d: diffs) {
			resp.getWriter().write(d.getDiff());
			resp.getWriter().write((d.getObjectType().isExecutableType() && d.getChangeType().equals(ChangeType.ADD))? "\n" : ";\n");
		}
	}
	
	void applyDiffs(List<Diff> diffs, Properties prop, String modelId, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException {
		Connection conn = DBUtil.initDBConn(prop, modelId);
		String sql = null;
		try {
			for(Diff d: diffs) {
				List<String> sqls = d.getDiffList();
				for(String s: sqls) {
					Statement st = conn.createStatement();
					sql = s;
					//sql = d.getDiff();
					boolean retIsRs = st.execute(sql);
					int count = st.getUpdateCount();
					resp.getWriter().write(sql+" /* model="+modelId+" ; ret=[isRS="+retIsRs+",count="+count+"] */");
					resp.getWriter().write((d.getObjectType().isExecutableType() && d.getChangeType().equals(ChangeType.ADD))? "\n" : ";\n");
				}
			}
		}
		catch(SQLException e) {
			throw new BadRequestException("Error: ["+e+"] ; sql =\n"+sql);
		}
		finally {
			conn.close();
		}
	}
	
}
