package tbrugz.queryon.processor;

import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.DBUtil;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.datadump.SQLQueries;
import tbrugz.sqldump.dbmodel.Column;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.def.ProcessingException;
import tbrugz.sqldump.util.Utils;

/*
 * XXX add 'enabled' column?
 * XXX add audit table (inserts, updates & deletes on table qon_queries)?
 * TODO: saving with 'SQLQueries+QOnQueries' may create inconsistencies when exception occurs in 2nd (QOnQueries) processor (can't rollback 1st processor actions)
 * TODO: should implement UpdatePlugin
 */
public class QOnQueries extends SQLQueries {

	static final Log log = LogFactory.getLog(QOnQueries.class);
	
	static final String PROP_PREFIX = "queryon.qon-queries";
	
	static final String SUFFIX_ACTION = ".action";
	static final String SUFFIX_TABLE = ".table";
	static final String SUFFIX_QUERY_NAMES = ".querynames";
	static final String SUFFIX_LIMIT_INSERT_EXACT = ".limit.insert.exact";
	static final String SUFFIX_LIMIT_UPDATE_EXACT = ".limit.update.exact";
	static final String SUFFIX_METADATA_ALLOW_QUERY_EXEC = ".metadata.allow-query-exec";
	
	static final String ACTION_READ = "read";
	static final String ACTION_WRITE = "write";
	static final String ACTION_REMOVE = "remove";
	
	static final String DEFAULT_QUERIES_TABLE = "qon_queries";
	
	boolean metadataAllowQueryExec = false;
	
	@Override
	public void setProperties(Properties prop) {
		super.setProperties(prop);
		metadataAllowQueryExec = Utils.getPropBool(prop, PROP_PREFIX+SUFFIX_METADATA_ALLOW_QUERY_EXEC, metadataAllowQueryExec);
	}
	
	public void process() {
		Set<View> origViews = new HashSet<View>();
		origViews.addAll(model.getViews());
		
		try {
			String action = prop.getProperty(PROP_PREFIX+SUFFIX_ACTION, ACTION_READ);
			if(ACTION_READ.equals(action)) {
				readFromDatabase();
			}
			else if(ACTION_WRITE.equals(action)) {
				//XXXdone: call SQLQueries processor before writeToDatabase() ?
				addQueriesFromProperties();
				writeToDatabase();
			}
			else if(ACTION_REMOVE.equals(action)) {
				removeFromDatabase();
			}
			else {
				throw new ProcessingException("unknown action: "+action);
			}
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
	
	void modelRollback(Set<View> origViews) {
		model.getViews().clear();
		model.getViews().addAll(origViews);
	}
	
	//XXX: test if table has all columns?
	void readFromDatabase() throws SQLException {
		String qonQueriesTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_QUERIES_TABLE);
		String sql = "select schema_name, name, query, remarks, roles_filter from "+qonQueriesTable+
				" where (disabled = 0 or disabled is null)"
				;
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
			String rolesFilterStr = rs.getString(5);
			
			try {
			PreparedStatement stinn = conn.prepareStatement(query);
			//count += addQueryToModel(queryName, queryName, schema,
			//		/*String colNames*/ null, /*boolean grabInfoFromMetadata*/ true, /*addAlsoAsTable*/ false,
			//		stinn, query, /*List<String> keyCols*/ null,
			//		/*List<String> params*/ null,
			//		/*String rsDecoratorFactory, List<String> rsFactoryArgs, String rsArgPrepend*/ null, null, null);
			
			count += addQueryFromDB(schema, queryName, stinn, query, remarks, rolesFilterStr);
			}
			catch(SQLException e) {
				log.warn("error reading query '"+queryName+"': "+e);
			}
		}
		
		log.info("QOn processed ["+
				(model.getModelId()!=null?"model="+model.getModelId()+"; ":"")+
				"added/replaced "+count+" queries]");
	}
	
	int addQueryFromDB(String schemaName, String queryName, PreparedStatement stmt, String sql, String remarks, String rolesFilterStr) {
		Query query = new Query();
		query.setSchemaName(schemaName);
		query.setName(queryName);
		query.setQuery(sql);
		query.setRemarks(remarks);
		setQueryRoles(query, rolesFilterStr);

		try {
			// resultset metadata
			// XXX: test for 'queryon.validate.x-getmetadata'?
			ResultSetMetaData rsmd = stmt.getMetaData();
			query.setColumns(DataDumpUtils.getColumns(rsmd));
		} catch (SQLException e) {
			query.setColumns(new ArrayList<Column>());
			if(metadataAllowQueryExec) {
				long initTime = System.currentTimeMillis();
				log.debug("executing query '"+queryName+"' to grab metadata");
				try {
					ParameterMetaData pmd = stmt.getParameterMetaData();
					int params = pmd.getParameterCount();
					stmt.setFetchSize(1);
					for(int i=1;i<=params;i++) {
						stmt.setObject(i, null);
					}
					ResultSet rs = stmt.executeQuery();
					query.setColumns(DataDumpUtils.getColumns(rs.getMetaData()));
					log.info("executed query '"+queryName+"' [elapsed: "+(System.currentTimeMillis()-initTime)+"ms]");
				}
				catch(SQLException e2) {
					log.warn("resultset exec sqlexception: "+e2.toString().trim()+" [query='"+queryName+"']");
					log.debug("resultset exec sqlexception: "+e2.getMessage().trim()+" [query='"+queryName+"']", e2);
				}
			}
			else {
				log.warn("resultset metadata's sqlexception: "+e.toString().trim()+" [query='"+queryName+"']");
				log.debug("resultset metadata's sqlexception: "+e.getMessage().trim()+" [query='"+queryName+"']", e);
			}
		}
		
		try {
			// parameter metadata
			ParameterMetaData pmd = stmt.getParameterMetaData();
			int params = pmd.getParameterCount();
			int inParams = 0;
			List<String> paramsTypes = new ArrayList<String>();
			
			for(int i=1;i<=params;i++) {
				int pmode = ParameterMetaData.parameterModeIn; // assuming IN parameter
				String ptype = null;
				
				try {
					pmode = pmd.getParameterMode(i);
				}
				catch(SQLException e) {
					log.debug("Exception getting parameter mode ["+queryName+"/"+i+"]: "+e);
				}
				
				try {
					ptype = pmd.getParameterTypeName(i);
				}
				catch(SQLException e) {
					log.debug("Exception getting parameter type ["+queryName+"/"+i+"]: "+e);
				}
				
				if(pmode==ParameterMetaData.parameterModeIn) {
					inParams++;
					paramsTypes.add(ptype);
				}
				else {
					log.warn("Parameter of mode '"+pmode+"' not understood for query '"+queryName+"/"+i+"'");
				}
			}
			query.setParameterCount(inParams);
			query.setParameterTypes(paramsTypes);
			//log.info("#params = "+inParams+" types = "+paramsTypes); 
		} catch (SQLException e) {
			query.setParameterCount(null);
			log.warn("parameter metadata's sqlexception: "+e.toString().trim()+" [query='"+queryName+"']");
			log.debug("parameter metadata's sqlexception: "+e.getMessage(), e);
		}
		
		View v = DBIdentifiable.getDBIdentifiableByName(model.getViews(), query.getName());
		if(v!=null) {
			boolean removed = model.getViews().remove(v);
			log.info("removed query '"+v+"'? "+removed);
		}

		boolean added = model.getViews().add(query);
		return added?1:0;
	}
	
	void addQueriesFromProperties() {
		//-- running SQLQueries...
		prop.setProperty(SQLQueries.PROP_QUERIES_ADD_TO_MODEL, "true");
		prop.setProperty(SQLQueries.PROP_QUERIES_RUN, "false");
		prop.setProperty(SQLQueries.PROP_QUERIES_GRABCOLSINFOFROMMETADATA, "true");
		super.process();
		//------ end SQLQueries...
	}

	/*
	 * XXX validate?
	 * - name cannot be null
	 * - add '.limit.[insert|update].[min|max]' (..exact already done)
	 */
	void writeToDatabase() throws SQLException {
		String qonQueriesTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_QUERIES_TABLE);
		String updateSql = "update "+qonQueriesTable+" set schema_name = ?, query = ?, remarks = ?, roles_filter = ? where name = ?";
		String insertSql = "insert into "+qonQueriesTable+" (schema_name, query, remarks, roles_filter, name) values (?, ?, ?, ?, ?)";
		PreparedStatement updateSt = conn.prepareStatement(updateSql);
		PreparedStatement insertSt = conn.prepareStatement(insertSql);

		Integer limitUpdateCountExact = Utils.getPropInt(prop, PROP_PREFIX+SUFFIX_LIMIT_UPDATE_EXACT);
		Integer limitInsertCountExact = Utils.getPropInt(prop, PROP_PREFIX+SUFFIX_LIMIT_INSERT_EXACT);
		log.debug("limitUpdateCountExact="+limitUpdateCountExact+" ; limitInsertCountExact="+limitInsertCountExact); 
		
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
					try {
					//schema, query, name
					updateSt.setString(1, v.getSchemaName());
					updateSt.setString(2, v.getQuery());
					updateSt.setString(3, v.getRemarks());
					updateSt.setString(4, getGrantsStr(v.getGrants()));
					updateSt.setString(5, v.getName());
					int countU = updateSt.executeUpdate();
					countUpdates += countU;
					if(countU==0) {
						insertSt.setString(1, v.getSchemaName());
						insertSt.setString(2, v.getQuery());
						insertSt.setString(3, v.getRemarks());
						insertSt.setString(4, getGrantsStr(v.getGrants()));
						insertSt.setString(5, v.getName());
						int countI = insertSt.executeUpdate();
						countInserts += countI;
						if(countI==0) {
							throw new ProcessingException("error updating/inserting query '"+v.getName()+"': no insert or update executed");
						}
					}
					//test limits
					if(limitInsertCountExact!=null && limitInsertCountExact!=countInserts) {
						throw new BadRequestException("error updating/inserting query '"+v.getName()+"': insert count ["+countInserts+"] does not match exact limit ["+limitInsertCountExact+"]");
					}
					if(limitUpdateCountExact!=null && limitUpdateCountExact!=countUpdates) {
						throw new BadRequestException("error updating/inserting query '"+v.getName()+"': update count ["+countUpdates+"] does not match exact limit ["+limitUpdateCountExact+"]");
					}
					}
					catch(SQLException e) {
						String message = "sqlexception: "+e.toString().trim()+"\nsql-update: "+updateSql+"\nsql-insert: "+insertSql;
						throw new InternalServerException(message, e);
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
	
	public static String getGrantsStr(List<Grant> grants) {
		if(grants==null || grants.size()==0) {
			return null;
		}
		
		List<String> grantees = new ArrayList<String>();
		for(Grant g: grants) {
			grantees.add(g.getGrantee());
		}
		return Utils.join(grantees, ROLES_DELIMITER_STR);
	}
	
}
