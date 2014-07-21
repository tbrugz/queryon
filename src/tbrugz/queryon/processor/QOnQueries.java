package tbrugz.queryon.processor;

import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.datadump.SQLQueries;
import tbrugz.sqldump.dbmodel.Column;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.def.ProcessingException;
import tbrugz.sqldump.util.Utils;

//XXX add remarks
public class QOnQueries extends SQLQueries {

	static final Log log = LogFactory.getLog(QOnQueries.class);
	
	static final String PROP_PREFIX = "queryon.qon-queries";
	
	static final String SUFFIX_ACTION = ".action";
	static final String SUFFIX_TABLE = ".table";
	static final String SUFFIX_QUERY_NAMES = ".querynames";
	
	static final String ACTION_READ = "read";
	static final String ACTION_WRITE = "write";
	static final String ACTION_REMOVE = "remove";
	
	static final String DEFAULT_QUERIES_TABLE = "qon_queries";
	
	public void process() {
		try {
			String action = prop.getProperty(PROP_PREFIX+SUFFIX_ACTION, ACTION_READ);
			if(ACTION_READ.equals(action)) {
				readFromDatabase();
			}
			else if(ACTION_WRITE.equals(action)) {
				writeToDatabase();
			}
			else if(ACTION_REMOVE.equals(action)) {
				removeFromDatabase();
			}
			else {
				throw new ProcessingException("unknown action: "+action);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	void readFromDatabase() throws SQLException {
		String qonQueriesTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_QUERIES_TABLE);
		String sql = "select schema, name, query, remarks from "+qonQueriesTable;
				//+" order by schema, name";
		
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
			
			PreparedStatement stinn = conn.prepareStatement(query);
			//count += addQueryToModel(queryName, queryName, schema,
			//		/*String colNames*/ null, /*boolean grabInfoFromMetadata*/ true, /*addAlsoAsTable*/ false,
			//		stinn, query, /*List<String> keyCols*/ null,
			//		/*List<String> params*/ null,
			//		/*String rsDecoratorFactory, List<String> rsFactoryArgs, String rsArgPrepend*/ null, null, null);
			
			count += addQ2M(schema, queryName, stinn, query, remarks); 
		}
		
		log.info("QOn processed [added/replaced "+count+" queries]");
	}
	
	int addQ2M(String schemaName, String queryName, PreparedStatement stmt, String sql, String remarks) {
		Query query = new Query();
		query.setSchemaName(schemaName);
		query.setName(queryName);
		query.setQuery(sql);
		query.setRemarks(remarks);

		try {
			ResultSetMetaData rsmd = stmt.getMetaData();
			query.setColumns(DataDumpUtils.getColumns(rsmd));
			ParameterMetaData pmd = stmt.getParameterMetaData();
			int params = pmd.getParameterCount();
			int inParams = 0;
			for(int i=1;i<=params;i++) {
				int pmode = ParameterMetaData.parameterModeIn; // assuming IN parameter
				try {
					pmode = pmd.getParameterMode(i);
				}
				catch(SQLException e) {
					log.debug("Exception getting parameter mode ["+queryName+"/"+i+"]: "+e);
				} 
				if(pmode==ParameterMetaData.parameterModeIn) { inParams++; }
				else {
					log.warn("Parameter of mode '"+pmode+"' not understood for queries ["+queryName+"/"+i+"]");
				}
			}
			query.setParameterCount(inParams);
		} catch (SQLException e) {
			query.setColumns(new ArrayList<Column>());
			log.warn("sqlexception: "+e.toString().trim(), e);
			log.debug("sqlexception: "+e.getMessage(), e);
		}
		
		View v = DBIdentifiable.getDBIdentifiableByName(model.getViews(), query.getName());
		if(v!=null) {
			boolean removed = model.getViews().remove(v);
			log.info("removed query '"+v+"'? "+removed);
		}

		boolean added = model.getViews().add(query);
		return added?1:0;
	}

	void writeToDatabase() throws SQLException {
		String qonQueriesTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_QUERIES_TABLE);
		String updateSql = "update "+qonQueriesTable+" set schema = ?, query = ?, remarks = ? where name = ?";
		String insertSql = "insert into "+qonQueriesTable+" (schema, query, remarks, name) values (?, ?, ?, ?)";
		PreparedStatement updateSt = conn.prepareStatement(updateSql);
		PreparedStatement insertSt = conn.prepareStatement(insertSql);

		List<String> queriesToUpdate = Utils.getStringListFromProp(prop, PROP_PREFIX+SUFFIX_QUERY_NAMES, ",");
		if(queriesToUpdate==null) {
			throw new ProcessingException("prop '"+PROP_PREFIX+SUFFIX_QUERY_NAMES+"' must be set");
		}
		Set<View> vs = model.getViews();
		int countUpdates = 0;
		int countInserts = 0;
		for(View v: vs) {
			if(v instanceof Query) {
				if(queriesToUpdate.contains(v.getName())) {
					//schema, query, name
					updateSt.setString(1, v.getSchemaName());
					updateSt.setString(2, v.getQuery());
					updateSt.setString(3, v.getRemarks());
					updateSt.setString(4, v.getName());
					int countU = updateSt.executeUpdate();
					countUpdates += countU;
					if(countU==0) {
						insertSt.setString(1, v.getSchemaName());
						insertSt.setString(2, v.getQuery());
						insertSt.setString(3, v.getRemarks());
						insertSt.setString(4, v.getName());
						int countI = insertSt.executeUpdate();
						countInserts += countI;
						if(countI==0) {
							throw new ProcessingException("error updating/inserting query '"+v.getName()+"'");
						}
					}
				}
			}
		}
		
		if(countInserts+countUpdates>0) {
			conn.commit();
		}
		
		log.info("QOn processed [updated/inserted "+countUpdates+"/"+countInserts+" queries in table "+qonQueriesTable+"]");
	}
	
	void removeFromDatabase() throws SQLException {
		List<String> queriesToRemove = Utils.getStringListFromProp(prop, PROP_PREFIX+SUFFIX_QUERY_NAMES, ",");
		if(queriesToRemove==null) {
			throw new ProcessingException("prop '"+PROP_PREFIX+SUFFIX_QUERY_NAMES+"' must be set");
		}
		
		String qonQueriesTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_QUERIES_TABLE);
		String deleteSql = "delete from "+qonQueriesTable+" where name = ?";
		PreparedStatement deleteSt = conn.prepareStatement(deleteSql);
		
		int countAllDeletes = 0;
		for(String qname: queriesToRemove) {
			qname = qname.trim();
			deleteSt.setString(1, qname);
			int countDeletes = deleteSt.executeUpdate();
			if(countDeletes!=1) {
				conn.rollback();
				//XXX "client/user error" - throw BadRequest or Processing Exception?
				throw new BadRequestException("error deleting query '"+qname+"' [#deletes = "+countDeletes+"]");
			}
			
			View v = DBIdentifiable.getDBIdentifiableByName(model.getViews(), qname);
			if(v!=null) {
				boolean removed = model.getViews().remove(v);
				if(!removed) {
					log.warn("query '"+qname+"' not removed from model");
				}
			}
			
			countAllDeletes += countDeletes;
		}

		if(countAllDeletes>0) {
			conn.commit();
		}
		
		log.info("QOn processed ["+countAllDeletes+" deleted queries in table "+qonQueriesTable+"]");
	}
	
}
