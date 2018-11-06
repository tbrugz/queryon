package tbrugz.queryon.processor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.SQL;
import tbrugz.queryon.UpdatePlugin;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.util.DBObjectUtils;
import tbrugz.queryon.util.DBUtil;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.Utils;

public class QOnQueries extends QOnQueriesProcessor implements UpdatePlugin {

	static final Log log = LogFactory.getLog(QOnQueries.class);
	
	/*static final String PROP_PREFIX = "queryon.qon-queries";
	
	static final String SUFFIX_ACTION = ".action";
	static final String SUFFIX_TABLE = ".table";
	static final String SUFFIX_QUERY_NAMES = ".querynames";
	static final String SUFFIX_LIMIT_INSERT_EXACT = ".limit.insert.exact";
	static final String SUFFIX_LIMIT_UPDATE_EXACT = ".limit.update.exact";
	static final String SUFFIX_METADATA_ALLOW_QUERY_EXEC = ".metadata.allow-query-exec";
	
	static final String DEFAULT_QUERIES_TABLE = "qon_queries";*/

	static final String PIPE_SPLIT = "\\|";
	
	//ServletContext servletContext;
	
	String getQonQueriesTable() {
		return prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_QUERIES_TABLE);
	}
	
	boolean isQonQueriesRelation(Relation relation) {
		String tablename = getQonQueriesTable();
		if( (! tablename.equalsIgnoreCase(relation.getName()))
			&& (! tablename.equalsIgnoreCase(relation.getSchemaName()+"."+relation.getName())) ) {
			return false;
		}
		return true;
	}
	
	void addGrants(Relation r, String owner, PrivilegeType pt, List<String> roles) {
		if(roles==null || roles.size()==0) { return; }
		List<Grant> grants = new ArrayList<Grant>();
		
		for(String s: roles) {
			grants.add(new Grant(owner, pt, s));
		}
		
		if(grants.size()>0) {
			r.setGrants(grants);
		}
	}
	
	Query newQuery(String schema, String name, String sql, String remarks, List<String> rolesSelect) {
		Query q = new Query();
		q.setSchemaName(schema);
		q.setName(name);
		q.setQuery(sql);
		q.setRemarks(remarks);
		//XXX t.setParameterCount(parameterCount);
	
		addGrants(q, name, PrivilegeType.SELECT, rolesSelect);
		return q;
	}
	
	Query createQonQuery(RequestSpec reqspec) {
		Map<String, String> v = reqspec.getUpdateValues();
	
		Query q = newQuery(v.get("SCHEMA_NAME"), v.get("NAME"), v.get("QUERY"), v.get("REMARKS"),
				Utils.getStringListIgnoreEmpty(v.get("ROLES_FILTER"), PIPE_SPLIT));
		
		int addcount = addQueryToModel(q);
		boolean added = addcount==1;

		if(added) {
			return q;
		}
		return null;
	}
	
	Query getQOnQueryFromModel(RequestSpec reqspec) {
		return (Query) DBIdentifiable.getDBIdentifiableByTypeAndName(model.getViews(), DBObjectType.VIEW, String.valueOf( reqspec.getParams().get(0) ));
	}
	
	@Override
	public void onInit(ServletContext context) {
		// TODO: do not depend on QOnQueriesProcessor / SQLQueries
		this.servletContext = context;
		process(context);
	}
	
	@Override
	public void onInsert(Relation relation, RequestSpec reqspec) {
		if(!isQonQueriesRelation(relation)) { return; }
	
		Query q = createQonQuery(reqspec);
		boolean added = q!=null;
		log.info("onInsert: added "+relation+"? "+added);
	}
	
	@Override
	public void onUpdate(Relation relation, RequestSpec reqspec) {
		if(!isQonQueriesRelation(relation)) { return; }
	
		Query q = getQOnQueryFromModel(reqspec);
	
		boolean removed = false;
		if(q==null) {
			log.warn("onUpdate: qon_query not found on model: "+reqspec.getParams()+" ; "+relation); //+" ; uv: "+uv);
		}
		else {
			removed = model.getViews().remove(q);
		}
	
		boolean added = false;
		Query qnew = null;
		try {
			qnew = createQonQuery(reqspec);
			added = qnew!=null;
		}
		catch(BadRequestException e) {
			if(q!=null) {
				// "model rollback"
				model.getViews().add(q);
			}
			throw e;
		}
		log.info("onUpdate: removed "+q+"? "+removed+" ; added "+qnew+"? "+added+" [qonRelation="+relation+"]");
	}
	
	@Override
	public void onDelete(Relation relation, RequestSpec reqspec) {
		if(!isQonQueriesRelation(relation)) { return; }
	
		Query q = getQOnQueryFromModel(reqspec);
		if(q==null) {
			//log.warn("onDelete: qon_table not found on model: "+reqspec.getParams()+" ; "+qonRelation);
			return;
		}
		boolean removed = model.getViews().remove(q);
		log.info("onDelete: removed "+q+"? "+removed);
	}
	
	@Override
	protected int addQueryFromDB(String schemaName, String queryName, PreparedStatement stmt, String sql, String remarks,
			String rolesFilterStr, ServletContext context) {
		Query q = newQuery(schemaName, queryName, sql, remarks,
				Utils.getStringList(rolesFilterStr, PIPE_SPLIT));
		return addQueryToModel(q);
	}
		
	protected int addQueryToModel(Query q) {
		Savepoint sp = null;
		try {
			String sql = SQL.getFinalSqlNoUsername(q.getQuery());
			sp = conn.setSavepoint();
			removeWarning(servletContext, model.getModelId(), q);
			DBObjectUtils.validateQuery(q, sql, conn, true);
		}
		catch(RuntimeException e) {
			putWarning(servletContext, model.getModelId(), q, e.toString());
			log.warn("Error validating query '"+q.getQualifiedName()+"': "+e.toString().trim());
			DBUtil.doRollback(conn, sp);
		}
		catch(SQLException e) {
			putWarning(servletContext, model.getModelId(), q, e.toString());
			log.warn("Error validating query '"+q.getQualifiedName()+"': "+e.toString().trim());
			DBUtil.doRollback(conn, sp);
		}
	
		if(model.getViews().contains(q)) {
			model.getViews().remove(q);
		}
		boolean added = model.getViews().add(q);
		if(added) {
			return 1;
		}
		return 0;
	}
	
	@Override
	protected void addQueriesFromProperties() {
		String queriesStr = prop.getProperty(PROP_QUERIES);
		String[] queriesArr = queriesStr.split(",");
		if(queriesArr.length!=1) {
			throw new InternalServerException("queriesArr.length!=1: "+queriesArr.length);
		}
		String qid = queriesArr[0];
		qid = qid.trim();
		
		String schemaName = prop.getProperty("sqldump.query."+qid+".schemaname");
		String queryName = prop.getProperty("sqldump.query."+qid+".name");
		String sql = prop.getProperty("sqldump.query."+qid+".sql");
		String remarks = prop.getProperty("sqldump.query."+qid+".remarks");
		String roles = prop.getProperty("sqldump.query."+qid+".roles");
		//queriesGrabbed += addQueryToModelInternal(qid, queryName, defaultSchemaName, stmt, sql, keyCols, params, remarks, roles, rsDecoratorFactory, rsFactoryArgs, rsArgPrepend);
		
		Query q = newQuery(schemaName, queryName, sql, remarks,
				Utils.getStringList(roles, PIPE_SPLIT));
		addQueryToModel(q);
		/*
		
		Query query = new Query();
		query.setId(qid);
		query.setName(queryName);
		//add schemaName
		query.setSchemaName(schemaName);
		
		query.setQuery(sql);
		query.setRemarks(remarks);
		setQueryRoles(query, roles);
		*/
	}
	
	void putWarning(ServletContext context, String modelId, Relation r, String warning) {
		super.putWarning(context, modelId, r.getSchemaName(), r.getName(), warning);
	}

	void removeWarning(ServletContext context, String modelId, Relation r) {
		super.removeWarning(context, modelId, r.getSchemaName(), r.getName());
	}
	
	public static Map<String, String> readQueryFromDatabase(Properties prop, String modelId, String schemaName, String name) throws SQLException, ClassNotFoundException, NamingException {
		String qonQueriesTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_QUERIES_TABLE);
		String sql = "select schema_name, name, query, remarks, roles_filter, version_seq\n" +
				"from "+qonQueriesTable+"\n" +
				"where (disabled = 0 or disabled is null)\n" +
				"and schema_name = ? and name = ?";
		
		Connection conn = null;
		try {
			conn = DBUtil.initDBConn(prop, modelId);
			ResultSet rs = null;
			PreparedStatement st = conn.prepareStatement(sql);
			st.setString(1, schemaName);
			st.setString(2, name);
			rs = st.executeQuery();
		
			if(rs.next()) {
				Map<String, String> ret = new HashMap<String, String>();
				ret.put("schema_name", rs.getString(1));
				ret.put("name", rs.getString(2));
				ret.put("query", rs.getString(3));
				ret.put("remarks", rs.getString(4));
				ret.put("roles_filter", rs.getString(5));
				ret.put("version_seq", rs.getString(6));
				
				if(rs.next()) {
					throw new IllegalStateException("more than 1 query with schema '"+schemaName+"' & name '"+name+"'");
				}
				return ret;
			}
			return null;
		}
		catch(SQLException e) {
			throw new SQLException("Error fetching queries ["+e.getMessage()+"; sql: "+sql+"]", e);
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
		
	}

}

