package tbrugz.queryon.processor;

import java.io.Flushable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.SQL;
import tbrugz.queryon.WebProcessor;
import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.ProcessorServlet;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.QueryOn.ActionType;
import tbrugz.queryon.QueryOn.LimitOffsetStrategy;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.queryon.util.ShiroUtils;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.graph.ResultSet2GraphML;
import tbrugz.sqldump.util.ConnectionUtil;

public class RS2GraphML extends ResultSet2GraphML implements WebProcessor {

	static final Log log = LogFactory.getLog(RS2GraphML.class);
	
	//SchemaModel model = null;
	Relation relation = null;
	Subject currentUser = null;
	
	/*@Override
	public void setModel(SchemaModel model) {
		this.model = model;
	}*/

	@Override
	public void setDBIdentifiable(DBIdentifiable dbid) {
		if(dbid==null) {
			throw new IllegalArgumentException("setDBIdentifiable: null object");
		}
		if(dbid instanceof Relation) {
			this.relation = (Relation) dbid;
		}
		else {
			throw new IllegalArgumentException("DBid must be a relation: "+dbid.getClass().getSimpleName());
		}
	}
	
	/*@Override
	public void setRelation(Relation relation) {
		if(relation==null) {
			throw new IllegalArgumentException("setDBIdentifiable: null object");
		}
		this.relation = relation;
	}*/

	@Override
	public void setSubject(Subject currentUser) {
		this.currentUser = currentUser;
	}

	@Override
	public void process() {
		throw new IllegalArgumentException("must call process(RequestSpec reqspec, HttpServletResponse resp)");
	}
	
	@Override
	public void process(RequestSpec reqspec, HttpServletResponse resp) {
		// test for SELECT permission on object...
		String otype = QueryOn.getObjectType((DBIdentifiable) relation);
		ActionType atype = ActionType.SELECT;
		log.debug("user: "+currentUser+" ; otype="+otype+"; atype="+atype); 
		ShiroUtils.checkPermission(currentUser, otype+":"+atype, reqspec.object);
		if(! ShiroUtils.isPermitted(currentUser, QueryOn.doNotCheckGrantsPermission)) {
			QueryOn.checkGrantsAndRolesMatches(currentUser, PrivilegeType.SELECT, relation);
		}
		
		Flushable output = null;
		try {
			output = ProcessorServlet.setOutput(this, resp);
			processInternal(reqspec, resp);
		} catch (BadRequestException e) {
			log.warn(e);
			throw e;
		} catch (Exception e) {
			log.warn(e);
			throw new BadRequestException("Error processing RS2GraphML", e); 
		} finally {
			if(output!=null) {
				try {
					output.flush();
				} catch (IOException e) {
					log.warn("Error in flush? - "+e.getMessage(), e);
				}
			}
		}
	}
	
	void processInternal(RequestSpec reqspec, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException, ServletException {
		Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		String finalSql = null;
		
		try {
			if(log.isDebugEnabled()) {
				ConnectionUtil.showDBInfo(conn.getMetaData());
			}
			
			Constraint pk = SchemaModelUtils.getPK(relation);
			LimitOffsetStrategy loStrategy = LimitOffsetStrategy.getDefaultStrategy(model.getSqlDialect());
			
			SQL sql = QueryOn.getSelectQuery(model, relation, reqspec, pk, loStrategy, resp);
			finalSql = sql.getFinalSql();
			List<Object> params = sql.getParameterValues();
			log.info("params: "+params);
			
			String qname = "rs2graphml";
			prop.setProperty("sqldump.graphmlqueries", qname);
			prop.setProperty("sqldump.graphmlquery."+qname+".sql", finalSql);
			for(int i=0;i<params.size();i++) {
				prop.setProperty("sqldump.graphmlquery."+qname+".param."+(i+1), String.valueOf(params.get(i)) );
			}
			
			super.process();
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}
	
	@Override
	public boolean isIdempotent() {
		return true;
	}

}
