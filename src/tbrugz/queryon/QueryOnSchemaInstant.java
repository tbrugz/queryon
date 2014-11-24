package tbrugz.queryon;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import javax.naming.NamingException;

import tbrugz.sqldump.JDBCSchemaGrabber;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.Column;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.def.DBMSResources;

public class QueryOnSchemaInstant extends QueryOnSchema {

	private static final long serialVersionUID = 1L;

	void refreshModel(DBObjectType type, Properties prop, String modelId, SchemaModel model, String schemaName, String objectName) throws ClassNotFoundException, SQLException, NamingException {
		//XXX update table|fks?
		if(type==DBObjectType.TABLE) {
			Connection conn = DBUtil.initDBConn(prop, modelId);
			DBMSResources res = DBMSResources.instance();
			DBMSFeatures feat = res.databaseSpecificFeaturesClass();
			grabTable(model, schemaName, objectName, conn.getMetaData(), feat); // grab table & update model
			conn.close();
		}
	}
	
	void grabTable(SchemaModel model, String schemaName, String tableName, DatabaseMetaData dbmd, DBMSFeatures feat) throws SQLException {
		if(tableName==null) throw new IllegalArgumentException("tableName cannot be null");
		String fullTableName = (schemaName!=null?schemaName+".":"")+tableName;
		log.info("grabbing new table: "+fullTableName);
		ResultSet rs = dbmd.getTables(null, schemaName, tableName, null);
		while(rs.next()) {
			String name = rs.getString("TABLE_NAME");
			Table t = DBIdentifiable.getDBIdentifiableBySchemaAndName(model.getTables(), schemaName, name);
			//if(t!=null && t.getColumns()!=null && t.getColumns().size()>0) { continue; }
			if(t!=null && t.getColumnCount()>0) { continue; }
			
			Table newt = new Table();
			newt.setSchemaName(schemaName);
			newt.setName(name);
			newt.setRemarks(rs.getString("REMARKS"));

			//feat.addTableSpecificFeatures(newt, rs);
			
			//----
			//columns
			ResultSet cols = dbmd.getColumns(null, schemaName, tableName, null);
			int numCol = 0;
			while(cols.next()) {
				Column c = JDBCSchemaGrabber.retrieveColumn(cols);
				newt.getColumns().add(c);
				//feat.addColumnSpecificFeatures(c, cols);
				numCol++;
				//String colDesc = getColumnDesc(c, columnTypeMapping, papp.getProperty(PROP_FROM_DB_ID), papp.getProperty(PROP_TO_DB_ID));
			}
			JDBCSchemaGrabber.closeResultSetAndStatement(cols);
			if(numCol==0) {
				throw new SQLException("Table "+fullTableName+" contains no columns");
			}
			
			//PKs
			newt.getConstraints().addAll(JDBCSchemaGrabber.grabRelationPKs(dbmd, newt));

			//FKs
			model.getForeignKeys().addAll(JDBCSchemaGrabber.grabRelationFKs(dbmd, feat, newt, true, false));
			
			/*
			//GRANTs
			if(doSchemaGrabTableGrants) {
				log.debug("getting grants from "+fullTablename);
				ResultSet grantrs = dbmd.getTablePrivileges(null, newt.getSchemaName(), tableName);
				newt.setGrants( grabSchemaGrants(grantrs) );
				closeResultSetAndStatement(grantrs);
			}
			
			//INDEXes
			if(doSchemaGrabIndexes && TableType.TABLE.equals(table.getType()) && !tableOnly) {
				log.debug("getting indexes from "+fullTablename);
				ResultSet indexesrs = dbmd.getIndexInfo(null, newt.getSchemaName(), tableName, false, false);
				grabSchemaIndexes(indexesrs, model.getIndexes());
				closeResultSetAndStatement(indexesrs);
			}
			*/
			
			//----
			boolean added = model.getTables().add(newt);
			if(!added) {
				model.getTables().remove(newt);
				added = model.getTables().add(newt);
			}
			if(!added) {
				log.warn("error adding table: "+name);
			}
		}
	}
	
}
