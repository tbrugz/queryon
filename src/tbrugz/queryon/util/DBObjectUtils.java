package tbrugz.queryon.util;

import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.dbmodel.Column;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Table;

public class DBObjectUtils {

	static final Log log = LogFactory.getLog(DBObjectUtils.class);
	
	public static void validateQuery(Query rel, Connection conn, boolean update) throws SQLException {
		String finalSql = rel.getQuery();
		log.debug("grabbing colums name & type from prepared statement's metadata [id="+rel.getId()+"; name="+rel.getQualifiedName()+"]");
		PreparedStatement stmt = conn.prepareStatement(finalSql);
		
		try {
			ResultSetMetaData rsmd = stmt.getMetaData();
			if(rsmd!=null && update) {
				rel.setColumns(DataDumpUtils.getColumns(rsmd));
			}
			else {
				log.warn("getMetaData() returned null: empty query? [query="+rel.getQualifiedName()+"] sql:\n"+finalSql);
			}
		} catch (SQLException e) {
			DBUtil.doRollback(conn);
			rel.setColumns(new ArrayList<Column>());
			log.warn("resultset metadata's sqlexception [query="+rel.getQualifiedName()+"]: "+e.toString().trim());
			log.debug("resultset metadata's sqlexception: "+e.getMessage(), e);
		}
			
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
						log.warn("Exception getting parameter mode ["+queryFullName+"/"+i+"]: "+e);
					}
					
					try {
						ptype = pmd.getParameterTypeName(i);
					}
					catch(SQLException e) {
						log.warn("Exception getting parameter type ["+queryFullName+"/"+i+"]: "+e);
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
				log.info("["+rel.getQualifiedName()+"] params: "+paramsTypes);
			}
		} catch (SQLException e) {
			DBUtil.doRollback(conn);
			rel.setParameterCount(null);
			log.warn("parameter metadata's sqlexception: "+e.toString().trim());
			log.debug("parameter metadata's sqlexception: "+e.getMessage(), e);
		}
		
	}

	public static void validateTable(Table rel, Connection conn, boolean update) throws SQLException {
		String finalSql = "select * from "+rel.getQualifiedName();
		
		log.debug("grabbing colums name & type from prepared statement's metadata [name="+rel.getQualifiedName()+"]");
		PreparedStatement stmt = conn.prepareStatement(finalSql);
		
		try {
			ResultSetMetaData rsmd = stmt.getMetaData();
			if(rsmd!=null && update) {
				rel.setColumns(DataDumpUtils.getColumns(rsmd));
			}
			else {
				log.warn("getMetaData() returned null: empty query? sql:\n"+finalSql);
			}
		} catch (SQLException e) {
			DBUtil.doRollback(conn);
			rel.setColumns(new ArrayList<Column>());
			log.warn("resultset metadata's sqlexception: "+e.toString().trim());
			log.debug("resultset metadata's sqlexception: "+e.getMessage(), e);
		}
		
	}
	
}
