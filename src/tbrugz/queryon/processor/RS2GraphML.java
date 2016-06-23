package tbrugz.queryon.processor;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

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
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.QueryOn.LimitOffsetStrategy;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.graph.ResultSet2GraphML;
import tbrugz.sqldump.util.ConnectionUtil;

public class RS2GraphML extends ResultSet2GraphML implements WebProcessor {

	static final Log log = LogFactory.getLog(RS2GraphML.class);
	
	//SchemaModel model = null;
	Relation relation = null;
	
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
		// TODO Auto-generated method stub
	}

	@Override
	public void process() {
		throw new IllegalArgumentException("must call process(RequestSpec reqspec, HttpServletResponse resp)");
	}
	
	@Override
	public void process(RequestSpec reqspec, HttpServletResponse resp) {
		try {
			processInternal(reqspec, resp);
		} catch (Exception e) {
			log.warn(e);
			throw new BadRequestException("Error processing RS2GraphML", e); 
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
			
			String qname = "rs2graphml";
			prop.setProperty("sqldump.graphmlqueries", qname);
			prop.setProperty("sqldump.graphmlquery."+qname+".sql", finalSql);
			
			super.process();
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}

}
