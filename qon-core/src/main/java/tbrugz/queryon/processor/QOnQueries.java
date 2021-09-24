package tbrugz.queryon.processor;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.SQL;
import tbrugz.queryon.exception.NotFoundException;
import tbrugz.queryon.util.DBObjectUtils;
import tbrugz.queryon.util.DBUtil;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.def.ProcessingException;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.Utils;
import tbrugz.queryon.util.WebUtils;

public class QOnQueries extends AbstractUpdatePlugin {

	static final Log log = LogFactory.getLog(QOnQueries.class);

	public static final String ATTR_QUERIES_WARNINGS_PREFIX = QOnQueriesProcessor.ATTR_QUERIES_WARNINGS_PREFIX;
	
	/*static final String PROP_PREFIX = "queryon.qon-queries";
	
	static final String SUFFIX_ACTION = ".action";
	static final String SUFFIX_TABLE = ".table";
	static final String SUFFIX_QUERY_NAMES = ".querynames";
	static final String SUFFIX_LIMIT_INSERT_EXACT = ".limit.insert.exact";
	static final String SUFFIX_LIMIT_UPDATE_EXACT = ".limit.update.exact";
	static final String SUFFIX_METADATA_ALLOW_QUERY_EXEC = ".metadata.allow-query-exec";
	
	static final String DEFAULT_QUERIES_TABLE = "qon_queries";*/

	static final String PIPE_SPLIT = "\\|";

	static final String ACTION_READ_QUERY = "readQuery";
	
	ServletContext servletContext;
	
	String getQonQueriesTable() {
		return getProperty(QOnQueriesProcessor.PROP_PREFIX, QOnQueriesProcessor.SUFFIX_TABLE, QOnQueriesProcessor.DEFAULT_QUERIES_TABLE);
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
		this.servletContext = context;
		
		Set<View> origViews = new HashSet<View>();
		origViews.addAll(model.getViews());
		
		try {
			readFromDatabase(servletContext);
		} catch (BadRequestException e) { // BadRequestException | ProcessingException ?
			modelRollback(origViews);
			DBUtil.doRollback(conn);
			throw e;
		} catch (ProcessingException pe) {
			modelRollback(origViews);
			throw pe;
		} catch (SQLException e) {
			modelRollback(origViews);
			throw new RuntimeException(e);
		}
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
	
	protected int addQueryFromDB(String schemaName, String queryName, PreparedStatement stmt, String sql, String remarks,
			String rolesFilterStr, ServletContext context) {
		Query q = newQuery(schemaName, queryName, sql, remarks,
				Utils.getStringList(rolesFilterStr, PIPE_SPLIT));
		return addQueryToModel(q);
	}
		
	protected int addQueryToModel(Query q) {
		Savepoint sp = null;
		try {
			String sql = SQL.getSqlWithNamedParameters(q.getQuery());
			sp = ConnectionUtil.setSavepointIfNotAutocommit(conn);
			UpdatePluginUtils.removeWarning(servletContext, getWarnKey(model.getModelId()), q);
			
			DBObjectUtils.validateQuery(q, sql, conn, true);
		}
		catch(RuntimeException e) {
			UpdatePluginUtils.putWarning(servletContext, getWarnKey(model.getModelId()), q, e.toString());
			log.warn("Error validating query '"+q.getQualifiedName()+"': "+e.toString().trim());
			DBUtil.doRollback(conn, sp);
		}
		catch(SQLException e) {
			UpdatePluginUtils.putWarning(servletContext, getWarnKey(model.getModelId()), q, e.toString());
			log.warn("Error validating query '"+q.getQualifiedName()+"': "+e.toString().trim());
			DBUtil.doRollback(conn, sp);
		}
		finally {
			DBUtil.releaseSavepoint(conn, sp);
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
	
	public static Map<String, String> readQueryFromDatabase(Properties prop, String modelId, String schemaName, String name) throws SQLException, ClassNotFoundException, NamingException {
		String qonQueriesTable = AbstractUpdatePlugin.getProperty(prop, modelId, QOnQueriesProcessor.PROP_PREFIX, QOnQueriesProcessor.SUFFIX_TABLE, QOnQueriesProcessor.DEFAULT_QUERIES_TABLE);
		Connection conn = null;
		conn = DBUtil.initDBConn(prop, modelId);
		try {
			return readQueryFromDatabase(conn, qonQueriesTable, schemaName, name);
		}	
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}

	public static Map<String, String> readQueryFromDatabase(Connection conn, String qonQueriesTable, String schemaName, String name) throws SQLException {
		String sql = "select schema_name, name, query, remarks, roles_filter, version_seq\n" +
				"from "+qonQueriesTable+"\n" +
				"where (disabled = 0 or disabled is null)\n" +
				"and schema_name = ? and name = ?";
		
		try {
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
		
	}

	@Override
	public void process() {
		throw new RuntimeException("process() should be called?");
	}
	
	/*
	 * see: QOnQueriesProcessor
	 */
	void readFromDatabase(ServletContext context) throws SQLException {
		String qonQueriesTable = getProperty(QOnQueriesProcessor.PROP_PREFIX, QOnQueriesProcessor.SUFFIX_TABLE, QOnQueriesProcessor.DEFAULT_QUERIES_TABLE);
		String sql = "select schema_name, name, query, remarks, roles_filter from "+qonQueriesTable+
				" where (disabled = 0 or disabled is null)"
				;
				//+" order by schema, name";
		
		UpdatePluginUtils.clearWarnings(context, getWarnKey(model.getModelId()));
		
		ResultSet rs = null;
		try {
			PreparedStatement st = conn.prepareStatement(sql);
			rs = st.executeQuery();
		}
		catch(SQLException e) {
			throw new SQLException("Error fetching queries, sql: "+sql, e);
		}
		
		int count = 0;
		while(rs.next()) {
			String schema = rs.getString(1);
			String queryName = rs.getString(2);
			String query = rs.getString(3);
			String remarks = rs.getString(4);
			String rolesFilterStr = rs.getString(5);
			
			try {
				PreparedStatement stinn = conn.prepareStatement( processQuery(query) );
				count += addQueryFromDB(schema, queryName, stinn, query, remarks, rolesFilterStr, context);
			}
			catch(SQLException | IllegalStateException e) {
				String message = "error reading query '"+queryName+"': "+e;
				log.warn(message);
				UpdatePluginUtils.putWarning(servletContext, getWarnKey(model.getModelId()), schema, queryName, message);
			}
		}
		
		log.info("QOnQueries processed ["+
				(model.getModelId()!=null?"model="+model.getModelId()+"; ":"")+
				"added/replaced "+count+" queries]");
	}

	protected String processQuery(String sql) {
		return SQL.getFinalSqlNoUsername(sql);
	}
	
	void modelRollback(Set<View> origViews) {
		model.getViews().clear();
		model.getViews().addAll(origViews);
	}

	@Override
	public boolean accepts(Relation relation) {
		return isQonQueriesRelation(relation);
	}

	String getWarnKey(String modelId) {
		return ATTR_QUERIES_WARNINGS_PREFIX+"."+modelId;
	}
	
	@Override
	public void executePluginAction(RequestSpec reqspec, HttpServletResponse resp) throws IOException, SQLException {
		log.info("executePluginAction: params: "+reqspec.getParams());
		//params: QOnQueries, <action>, <object>
		if(reqspec.getParams().size() != 3) {
			throw new BadRequestException("Must have 3 parameters");
		}

		if(ACTION_READ_QUERY.equals(reqspec.getParams().get(1))) {
			String tablename = getQonQueriesTable();
			String fullObjectName = reqspec.getParams().get(2).toString();

			String[] partz = fullObjectName.split("\\.");
			if(partz.length>2) {
				throw new BadRequestException("Malformed object name: "+fullObjectName);
			}
			String schemaName = null;
			String objectName = partz[0];
			if(partz.length==2) {
				schemaName = partz[0];
				objectName = partz[1];
			}

			Map<String, String> map = readQueryFromDatabase(conn, tablename, schemaName, objectName);
			if(map==null) {
				throw new NotFoundException("Query not found: "+fullObjectName);
			}
			WebUtils.writeJsonResponse(map, resp);
		}
		else {
			throw new BadRequestException("Unknown action '"+reqspec.getParams().get(1)+"'");
		}
	}

}
