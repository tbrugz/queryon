package tbrugz.queryon.util;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.SQL;
import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.dbmodel.Column;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.ExecutableParameter;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.dbmodel.ExecutableParameter.INOUT;

public class DBObjectUtils {

	static final Log log = LogFactory.getLog(DBObjectUtils.class);
	
	static boolean logParameterMetaDataExceptions = false;
	
	/*public static void validateQuery(Query rel, Connection conn, boolean update) throws SQLException {
		String finalSql = rel.getQuery();
		validateQuery(rel, finalSql, conn, update);
	}*/

	public static void validateQuery(Query rel, String finalSql, Connection conn, boolean update) throws SQLException {
		PreparedStatement stmt = conn.prepareStatement(finalSql);
		validateQueryColumns(rel, finalSql, conn, stmt, update);
		validateQueryParameters(rel, finalSql, conn, stmt, update);
	}

	public static void validateQueryParameters(Query rel, String finalSql, Connection conn, boolean update) throws SQLException {
		PreparedStatement stmt = conn.prepareStatement(finalSql);
		validateQueryParameters(rel, finalSql, conn, stmt, update);
	}
	
	static void validateQueryColumns(Query rel, String finalSql, Connection conn, PreparedStatement stmt, boolean update) throws SQLException {
		log.debug("grabbing colums name & type from prepared statement's metadata [id="+rel.getId()+"; name="+rel.getQualifiedName()+"]");
		
		try {
			ResultSetMetaData rsmd = stmt.getMetaData();
			if(rsmd!=null && update) {
				rel.setColumns(DataDumpUtils.getColumns(rsmd));
			}
			else {
				log.warn("getMetaData() returned null: empty query? [query="+rel.getQualifiedName()+"] sql:\n"+finalSql);
			}
		} catch (SQLException e) {
			//DBUtil.doRollback(conn);
			rel.setColumns(new ArrayList<Column>());
			log.warn("resultset metadata's sqlexception [query="+rel.getQualifiedName()+"]: "+e.toString().trim());
			log.debug("resultset metadata's sqlexception [query="+rel.getQualifiedName()+"]: "+e.getMessage(), e);
			throw e;
		}
	}
		
	static void validateQueryParameters(Query rel, String finalSql, Connection conn, PreparedStatement stmt, boolean update) throws SQLException {
		log.debug("grabbing parameters from prepared statement's metadata [id="+rel.getId()+"; name="+rel.getQualifiedName()+"]");
		
		try {
			ParameterMetaData pmd = stmt.getParameterMetaData();
			if(pmd!=null && update) {
				int params = pmd.getParameterCount();
				//rel.setParameterCount(params);
				
				int inParams = 0;
				List<String> paramsTypes = new ArrayList<String>();
				String queryFullName = rel.getFinalQualifiedName();
				
				for(int i=1;i<=params;i++) {
					int pmode = ParameterMetaData.parameterModeIn; // assuming IN parameter
					String ptype = null;
					
					try {
						pmode = pmd.getParameterMode(i);
					}
					catch(SQLException e) {
						//DBUtil.doRollback(conn);
						if(logParameterMetaDataExceptions) {
							log.warn("Exception getting parameter mode ["+queryFullName+"/"+i+"]: "+e);
						}
					}
					
					try {
						ptype = pmd.getParameterTypeName(i);
					}
					catch(SQLException e) {
						//DBUtil.doRollback(conn);
						if(logParameterMetaDataExceptions) {
							log.warn("Exception getting parameter type ["+queryFullName+"/"+i+"]: "+e);
						}
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
				rel.setParameterCount(inParams);
				rel.setParameterTypes(paramsTypes);
				//log.info("["+rel.getQualifiedName()+"] params: "+paramsTypes);
			}
		} catch (SQLException e) {
			//DBUtil.doRollback(conn);
			rel.setParameterCount(null);
			log.warn("parameter metadata's sqlexception [query="+rel.getQualifiedName()+"]: "+e.toString().trim());
			log.debug("parameter metadata's sqlexception [query="+rel.getQualifiedName()+"]: "+e.getMessage(), e);
			throw e;
		}
		
		if(update) {
			List<String> namedParameterNames = SQL.getNamedParameterNames(finalSql, rel.getParameterCount());
			rel.setNamedParameterNames(namedParameterNames);
		}
	}

	public static void validateTable(Relation rel, Connection conn, boolean update) throws SQLException {
		String finalSql = "select * from "+rel.getQualifiedName();
		
		log.debug("grabbing colums name & type from prepared statement's metadata [name="+rel.getQualifiedName()+"]");
		
		try {
			PreparedStatement stmt = conn.prepareStatement(finalSql);
			ResultSetMetaData rsmd = stmt.getMetaData();
			if(rsmd!=null && update) {
				//rel.setColumns(DataDumpUtils.getColumns(rsmd));
				setColumns(rel, DataDumpUtils.getColumns(rsmd));
			}
			else {
				log.warn("getMetaData() returned null: empty query? sql:\n"+finalSql);
			}
		} catch (SQLException e) {
			//DBUtil.doRollback(conn);
			//rel.setColumns(new ArrayList<Column>());
			setColumns(rel, new ArrayList<Column>());
			log.warn("resultset metadata's sqlexception: "+e.toString().trim());
			log.debug("resultset metadata's sqlexception: "+e.getMessage(), e);
			throw e;
		}
		
	}

	public static void validateExecutable(ExecutableObject exec, Connection conn, boolean update) throws SQLException {
		ParameterMetaData pmd = null;
		if(exec.getType().equals(DBObjectType.SCRIPT)) {
			try {
				PreparedStatement stmt = conn.prepareStatement(exec.getBody());
				pmd = stmt.getParameterMetaData();
			} catch (SQLException e) {
				log.warn("parameter metadata's sqlexception [exec="+exec.getQualifiedName()+"]: "+e.toString().trim());
				log.debug("parameter metadata's sqlexception [exec="+exec.getQualifiedName()+"]: "+e.getMessage(), e);
				throw e;
			}
		}
		else {
			try {
				//XXX: use JDBCSchemaGrabber's grabFunctionsColumns & grabProceduresColumns ?
				String sql = SQL.createExecuteSQLstr(exec); // must have right number of parameters...
				CallableStatement stmt = conn.prepareCall(sql.toString());
				pmd = stmt.getParameterMetaData();
				//log.info("sql: "+sql);
			} catch (SQLException e) {
				log.warn("parameter metadata's sqlexception [exec="+exec.getQualifiedName()+"]: "+e.toString().trim());
				log.debug("parameter metadata's sqlexception [exec="+exec.getQualifiedName()+"]: "+e.getMessage(), e);
				throw e;
			}
		}
		
		int pc = 0;
		try {
			pc = pmd.getParameterCount();
			//log.info("addExecutable: "+exec.getQualifiedName()+" ["+exec.getType()+"]: parameter count = "+pc);
			List<ExecutableParameter> eps = new ArrayList<ExecutableParameter>();
			
			for(int i=0;i<pc;i++) {
				ExecutableParameter ep = new ExecutableParameter();
				int pmode = ParameterMetaData.parameterModeIn; // assuming IN parameter
				String ptype = null;
				
				try {
					pmode = pmd.getParameterMode(i);
				}
				catch(SQLException e) {
					//DBUtil.doRollback(conn);
					if(logParameterMetaDataExceptions) {
						log.warn("exception getting parameter mode [exec="+exec.getQualifiedName()+"]: "+e);
					}
				}
				if(i==0 && exec.getType()==DBObjectType.FUNCTION && pmode!=ParameterMetaData.parameterModeOut) {
					pmode = ParameterMetaData.parameterModeOut;
					log.debug("changing return parameter mode to ParameterMetaData.parameterModeOut ["+ParameterMetaData.parameterModeOut+"]");
				}
				
				try {
					ptype = pmd.getParameterTypeName(i);
				}
				catch(SQLException e) {
					//DBUtil.doRollback(conn);
					if(logParameterMetaDataExceptions) {
						log.warn("Exception getting parameter type [exec="+exec.getQualifiedName()+"]: "+e);
					}
				}
				
				ep.setInout(getInout(pmode));
				if(ep.getInout()==null) {
					log.warn("Parameter of mode '"+pmode+"' not understood for executable "+exec.getQualifiedName());
				}
				
				ep.setDataType(ptype);
				
				//log.info("parameter: "+ep);
				eps.add(ep);
			}
			if(pc!=eps.size()) {
				log.warn("pc!=eps.size(): pc=="+pc+" ; eps.size()=="+eps.size());
			}
			//log.info("old params [update="+update+"]: "+exec.getParams());
			if(update) {
				exec.setParams(eps);
			}
		}
		catch(SQLException e) {
			log.warn("parameter metadata's sqlexception: "+e.toString().trim());
			log.debug("parameter metadata's sqlexception: "+e.getMessage(), e);
			throw e;
		}
	}
	
	static void setColumns(Relation rel, List<Column> columns) {
		if(rel instanceof Table) {
			Table t = (Table) rel;
			t.setColumns(columns);
		}
		else if(rel instanceof View) {
			View v = (View) rel;
			v.setColumns(columns);
		}
		else {
			log.warn("Relation '"+rel+"' not table or view");
		}
	}
	
	static INOUT getInout(int pmode) {
		if(pmode==ParameterMetaData.parameterModeIn) {
			return ExecutableParameter.INOUT.IN;
		}
		else if(pmode==ParameterMetaData.parameterModeInOut) {
			return ExecutableParameter.INOUT.INOUT;
		}
		else if(pmode==ParameterMetaData.parameterModeOut) {
			return ExecutableParameter.INOUT.OUT;
		}
		
		//log.debug("Parameter of mode '"+pmode+"' not understood");
		return null;
	}
	
}
