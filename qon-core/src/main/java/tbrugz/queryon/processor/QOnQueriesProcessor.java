package tbrugz.queryon.processor;

import java.io.IOException;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.ProcessorServlet;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.SQL;
import tbrugz.queryon.WebProcessor;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.util.DBUtil;
import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.datadump.SQLQueries;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.Column;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.def.ProcessingException;
import tbrugz.sqldump.util.Utils;

/*
 * XXX add 'enabled' column?
 * XXX add audit table (inserts, updates & deletes on table qon_queries)?
 * TODO: saving with 'SQLQueries+QOnQueries' may create inconsistencies when exception occurs in 2nd (QOnQueries) processor (can't rollback 1st processor actions)
 * TODO: should implement UpdatePlugin
 */
public class QOnQueriesProcessor extends SQLQueries implements WebProcessor {

	static final Log log = LogFactory.getLog(QOnQueriesProcessor.class);
	
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
	
	public static final String DEFAULT_QUERIES_TABLE = "QON_QUERIES";
	
	public static final String ATTR_QUERIES_WARNINGS_PREFIX = "qon-queries-warnings";
	
	boolean metadataAllowQueryExec = false;
	Subject currentUser;
	DBMSFeatures features;
	
	ServletContext servletContext;
	
	public QOnQueriesProcessor() {
		setFailOnError(false);
	}
	
	@Override
	public void setProperties(Properties prop) {
		super.setProperties(prop);
		metadataAllowQueryExec = Utils.getPropBool(prop, PROP_PREFIX+SUFFIX_METADATA_ALLOW_QUERY_EXEC, metadataAllowQueryExec);
	}
	
	@Override
	public void process() {
		Set<View> origViews = new HashSet<View>();
		origViews.addAll(model.getViews());
		
		try {
			final DBMSResources res = DBMSResources.instance();
			features = res.getSpecificFeatures(conn.getMetaData());
			
			String action = prop.getProperty(PROP_PREFIX+SUFFIX_ACTION, ACTION_READ);
			if(ACTION_READ.equals(action)) {
				readFromDatabase(servletContext);
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
	void readFromDatabase(ServletContext context) throws SQLException {
		String qonQueriesTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_QUERIES_TABLE);
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
			//count += addQueryToModel(queryName, queryName, schema,
			//		/*String colNames*/ null, /*boolean grabInfoFromMetadata*/ true, /*addAlsoAsTable*/ false,
			//		stinn, query, /*List<String> keyCols*/ null,
			//		/*List<String> params*/ null,
			//		/*String rsDecoratorFactory, List<String> rsFactoryArgs, String rsArgPrepend*/ null, null, null);
			
			count += addQueryFromDB(schema, queryName, stinn, query, remarks, rolesFilterStr, context);
			}
			catch(SQLException e) {
				String message = "error reading query '"+queryName+"': "+e;
				log.warn(message);
				UpdatePluginUtils.putWarning(context, getWarnKey(model.getModelId()), schema, queryName, message);
			}
		}
		
		log.info("QOnQueries(Processor) processed ["+
				(model.getModelId()!=null?"model="+model.getModelId()+"; ":"")+
				"added/replaced "+count+" queries]");
	}
	
	protected int addQueryFromDB(String schemaName, String queryName, PreparedStatement stmt, String sql, String remarks, String rolesFilterStr, ServletContext context) {
		Query query = new Query();
		query.setSchemaName(schemaName);
		query.setName(queryName);
		query.setQuery(sql);
		query.setRemarks(remarks);
		setQueryRoles(query, rolesFilterStr);
		String queryFullName = query.getQualifiedName();

		try {
			// resultset metadata
			// XXX: test for 'queryon.validate.x-getmetadata'?
			ResultSetMetaData rsmd = stmt.getMetaData();
			query.setColumns(DataDumpUtils.getColumns(rsmd));
		} catch (SQLException e) {
			if(features.sqlExceptionRequiresRollback()) {
				DBUtil.doRollback(conn);
			}
			query.setColumns(new ArrayList<Column>());
			if(metadataAllowQueryExec) {
				long initTime = System.currentTimeMillis();
				log.debug("executing query '"+queryFullName+"' to grab metadata");
				try {
					ParameterMetaData pmd = stmt.getParameterMetaData();
					int params = pmd.getParameterCount();
					stmt.setFetchSize(1);
					for(int i=1;i<=params;i++) {
						stmt.setObject(i, null);
					}
					ResultSet rs = stmt.executeQuery();
					query.setColumns(DataDumpUtils.getColumns(rs.getMetaData()));
					log.info("executed query '"+queryFullName+"' [elapsed: "+(System.currentTimeMillis()-initTime)+"ms]");
				}
				catch(SQLException e2) {
					log.warn("resultset exec sqlexception: "+e2.toString().trim()+" [query='"+queryFullName+"']");
					log.debug("resultset exec sqlexception: "+e2.getMessage().trim()+" [query='"+queryFullName+"']", e2);
				}
			}
			else {
				String message = "resultset metadata's sqlexception: "+e.toString().trim()+" [query='"+queryFullName+"']";
				log.warn("resultset metadata's sqlexception: "+e.toString().trim()+" [query='"+queryFullName+"']");
				log.debug("resultset metadata's sqlexception: "+e.getMessage().trim()+" [query='"+queryFullName+"']", e);
				UpdatePluginUtils.putWarning(context, getWarnKey(model.getModelId()), schemaName, queryName, message);
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
					log.debug("Exception getting parameter mode ["+queryFullName+"/"+i+"]: "+e);
				}
				
				try {
					ptype = pmd.getParameterTypeName(i);
				}
				catch(SQLException e) {
					log.debug("Exception getting parameter type ["+queryFullName+"/"+i+"]: "+e);
				}
				
				if(pmode==ParameterMetaData.parameterModeIn) {
					inParams++;
					paramsTypes.add(ptype);
				}
				else if(pmode==ParameterMetaData.parameterModeInOut) {
					inParams++;
					paramsTypes.add(ptype);
					log.debug("INOUT parameter type in Query ["+queryFullName+"/"+i+"/"+pmode+"]");
				}
				else {
					log.warn("Parameter of mode '"+pmode+"' not understood for query '"+queryFullName+"/"+i+"'");
				}
			}
			query.setParameterCount(inParams);
			query.setParameterTypes(paramsTypes);
			//log.info("#params = "+inParams+" types = "+paramsTypes); 
		} catch (SQLException e) {
			query.setParameterCount(null);
			String message = "parameter metadata's sqlexception: "+e.toString().trim()+" [query='"+queryFullName+"']";
			log.warn(message);
			log.debug("parameter metadata's sqlexception: "+e.getMessage(), e);
			UpdatePluginUtils.putWarning(context, getWarnKey(model.getModelId()), schemaName, queryName, message);
		}
		
		View v = DBIdentifiable.getDBIdentifiableByName(model.getViews(), query.getName());
		if(v!=null) {
			boolean removed = model.getViews().remove(v);
			log.info("removed query '"+v+"'? "+removed);
		}

		boolean added = model.getViews().add(query);
		return added?1:0;
	}
	
	protected void addQueriesFromProperties() {
		//TODO: return warning if can't get metadata
		//-- running SQLQueries...
		prop.setProperty(SQLQueries.PROP_QUERIES_ADD_TO_MODEL, "true");
		prop.setProperty(SQLQueries.PROP_QUERIES_RUN, "false");
		prop.setProperty(SQLQueries.PROP_QUERIES_GRABCOLSINFOFROMMETADATA, "true");
		super.process();
		//------ end SQLQueries...
	}
	
	protected String processQuery(String sql) {
		return SQL.getFinalSqlNoUsername(sql);
	}

	/*
	 * XXX validate?
	 * - name cannot be null
	 * - add '.limit.[insert|update].[min|max]' (..exact already done)
	 */
	void writeToDatabase() throws SQLException {
		String qonQueriesTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_QUERIES_TABLE);
		String updateSql = "update "+qonQueriesTable+" set schema_name = ?, query = ?, remarks = ?, roles_filter = ?, updated_at = ?, updated_by = ? where name = ?";
		String insertSql = "insert into "+qonQueriesTable+" (schema_name, query, remarks, roles_filter, name, created_at, created_by) values (?, ?, ?, ?, ?, ?, ?)";
		PreparedStatement updateSt = conn.prepareStatement(updateSql);

		Integer limitUpdateCountExact = Utils.getPropInt(prop, PROP_PREFIX+SUFFIX_LIMIT_UPDATE_EXACT);
		Integer limitInsertCountExact = Utils.getPropInt(prop, PROP_PREFIX+SUFFIX_LIMIT_INSERT_EXACT);
		log.debug("limitUpdateCountExact="+limitUpdateCountExact+" ; limitInsertCountExact="+limitInsertCountExact); 
		
		List<String> queriesToUpdate = Utils.getStringListFromProp(prop, PROP_PREFIX+SUFFIX_QUERY_NAMES, ",");
		if(queriesToUpdate==null) {
			throw new ProcessingException("prop '"+PROP_PREFIX+SUFFIX_QUERY_NAMES+"' must be set");
		}
		
		String username = QueryOn.getUsername(currentUser);
		Date now = new Date();
		Timestamp ts = new Timestamp(now.getTime());
		//log.info("ts: "+ts+" ; now: "+now);
		
		Set<View> vs = model.getViews();
		int countUpdates = 0;
		int countInserts = 0;
		for(View v: vs) {
			if(v instanceof Query) {
				if(queriesToUpdate.contains(v.getName())) {
					try {
					// schema, query, name
					updateSt.setString(1, v.getSchemaName());
					updateSt.setString(2, v.getQuery());
					updateSt.setString(3, v.getRemarks());
					updateSt.setString(4, getGrantsStr(v.getGrants()));
					updateSt.setTimestamp(5, ts);
					updateSt.setString(6, username);
					// update key
					updateSt.setString(7, v.getName());
					int countU = updateSt.executeUpdate();
					countUpdates += countU;
					
					// if no updates, try insert
					if(countU==0) {
						PreparedStatement insertSt = conn.prepareStatement(insertSql);
						
						insertSt.setString(1, v.getSchemaName());
						insertSt.setString(2, v.getQuery());
						insertSt.setString(3, v.getRemarks());
						insertSt.setString(4, getGrantsStr(v.getGrants()));
						insertSt.setString(5, v.getName());
						insertSt.setTimestamp(6, ts);
						insertSt.setString(7, username);
						
						try {
							int countI = insertSt.executeUpdate();
							countInserts += countI;
							if(countI==0) {
								throw new ProcessingException("error updating/inserting query '"+v.getName()+"': no insert or update executed");
							}
						}
						catch(SQLException e) {
							String message = "sqlexception: "+e.toString().trim()+"\nsql-insert: "+insertSql;
							throw new InternalServerException(message, e);
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
						String message = "sqlexception: "+e.toString().trim()+"\nsql-update: "+updateSql;
						throw new InternalServerException(message, e);
					}
				}
			}
		}
		
		if(countInserts+countUpdates>0) {
			DBUtil.doCommit(conn);
		}
		
		log.info("QOnQueries(Processor) processed [updated/inserted "+countUpdates+"/"+countInserts+" queries in table "+qonQueriesTable+"]");
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
			//String prettyQname = qname.trim();
			deleteSt.setString(1, qname);
			int countDeletes = deleteSt.executeUpdate();
			if(countDeletes!=1) {
				//XXX "client/user error" - throw BadRequest or Processing Exception?
				log.warn("Error deleting query '"+qname+"' [#deletes = "+countDeletes+"]\nsql="+deleteSql);
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
			DBUtil.doCommit(conn);
		}
		
		log.info("QOnQueries(Processor) processed ["+countAllDeletes+" deleted queries in table "+qonQueriesTable+"]");
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

	@Override
	public void setDBIdentifiable(DBIdentifiable dbid) {
	}

	@Override
	public void setSubject(Subject currentUser) {
		this.currentUser = currentUser;
	}
	
	@Override
	public void setServletContext(ServletContext context) {
		this.servletContext = context;
	}

	@Override
	public void process(RequestSpec reqspec, HttpServletResponse resp) {
		try {
			ProcessorServlet.setOutput(this, resp);
		}
		catch(IOException e) {
			throw new InternalServerException(e.getMessage(), e);
		}
		process();
	}
	
	String getWarnKey(String modelId) {
		return ATTR_QUERIES_WARNINGS_PREFIX+"."+modelId;
	}
	
}
