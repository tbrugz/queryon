package tbrugz.queryon;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletResponse;

import tbrugz.sqldump.JDBCSchemaGrabber;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.Column;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.def.DBMSResources;

/*
 * TODO: 'instant' servlets SHOULD NOT modify model, right?
 */
public class QueryOnSchemaInstant extends QueryOnSchema {

	private static final long serialVersionUID = 1L;
	
	boolean doSchemaGrabTableGrants = false,
			doSchemaGrabTableIndexes = false,
			doSchemaGrabTableTriggers = false;

	@Override
	void dumpObject(DBObjectType type, Properties prop, String modelId, SchemaModel model, String schemaName, String objectName, HttpServletResponse resp)
			throws ClassNotFoundException, SQLException, NamingException, IOException {
		//XXX update table|fks?
		Connection conn = DBUtil.initDBConn(prop, modelId);
		DBMSResources res = DBMSResources.instance();
		DBMSFeatures feat = res.databaseSpecificFeaturesClass();
		DatabaseMetaData dbmd = feat.getMetadataDecorator(conn.getMetaData());
		
		switch(type) {
		case TABLE:
			Table t = grabTable(schemaName, objectName, dbmd, feat); // grab table & update model
			dump(t, resp);
			//XXX: grab (& dump) triggers & FKs?
			conn.close(); return;
			//break;
		case VIEW:
		case MATERIALIZED_VIEW:
			//log.info("before:: #view = "+model.getViews().size()+" ["+schemaName+"."+objectName+"]");
			feat.grabDBViews(model.getViews(), schemaName, objectName, conn);
			//log.info("after:: #view = "+model.getViews().size());
			break;
		case PROCEDURE:
		case FUNCTION:
		case PACKAGE:
		case PACKAGE_BODY:
		case EXECUTABLE:
			feat.grabDBExecutables(model.getExecutables(), schemaName, objectName, conn);
			break;
		case TRIGGER:
			//XXX: really grab trigger?
			feat.grabDBTriggers(model.getTriggers(), schemaName, null, objectName, conn);
			break;
		case FK:
			//XXX how to get FK from dbmd by name? just grab from model (the 'cache')
			log.info("object of type "+type+" grabbed (?) from model cache");
			break;
		default:
			log.warn("object of type "+type+" cannot be refreshed");
		}
		
		super.dumpObject(type, prop, modelId, model, schemaName, objectName, resp);
		
		conn.close();
	}
	
	void dump(DBIdentifiable dbid, HttpServletResponse resp) throws IOException {
		resp.getWriter().write(dbid.getDefinition(true));
	}
	
	Table grabTable(String schemaName, String tableName, DatabaseMetaData dbmd, DBMSFeatures feat) throws SQLException {
		if(tableName==null) throw new IllegalArgumentException("tableName cannot be null");
		String fullTableName = (schemaName!=null?schemaName+".":"")+tableName;
		log.info("grabbing new table: "+fullTableName);
		Table ret = null;
		ResultSet rs = dbmd.getTables(null, schemaName, tableName, null);
		JDBCSchemaGrabber grabber = new JDBCSchemaGrabber();
		while(rs.next()) {
			String name = rs.getString("TABLE_NAME");
			//Table t = DBIdentifiable.getDBIdentifiableBySchemaAndName(model.getTables(), schemaName, name);
			//if(t!=null && t.getColumns()!=null && t.getColumns().size()>0) { continue; }
			//if(t!=null && t.getColumnCount()>0) { continue; }
			
			Table newt = feat.getTableObject();
			newt.setSchemaName(schemaName);
			newt.setName(name);
			newt.setRemarks(rs.getString("REMARKS"));
			feat.addTableSpecificFeatures(newt, rs);

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
			//model.getForeignKeys().addAll(JDBCSchemaGrabber.grabRelationFKs(dbmd, feat, newt, true, false));
			
			//GRANTs
			if(doSchemaGrabTableGrants) {
				//log.debug("getting grants from "+fullTableName);
				ResultSet grantrs = dbmd.getTablePrivileges(null, newt.getSchemaName(), tableName);
				newt.setGrants( grabber.grabSchemaGrants(grantrs) );
				JDBCSchemaGrabber.closeResultSetAndStatement(grantrs);
			}
			
			/*
			//INDEXes
			if(doSchemaGrabTableIndexes) {// && TableType.TABLE.equals(newt.getType())) {
				//log.debug("getting indexes from "+fullTableName);
				ResultSet indexesrs = null;
				try {
					indexesrs = dbmd.getIndexInfo(null, newt.getSchemaName(), tableName, false, false);
					JDBCSchemaGrabber.grabSchemaIndexes(indexesrs, model.getIndexes());
				}
				catch (SQLException e) {
					log.warn("error getting index info: "+e.getMessage().trim());//,e);
				}
				finally {
					JDBCSchemaGrabber.closeResultSetAndStatement(indexesrs);
				}
			}
			
			//TRIGGERs
			if(doSchemaGrabTableTriggers) {
				feat.grabDBTriggers(model, schemaName, name, null, dbmd.getConnection());
			}
			*/
			if(ret==null) {
				ret = newt;
			}
			else {
				rs.close();
				throw new IllegalStateException("more than 1 table with same name? ["+fullTableName+"]");
			}
			//----
			/*boolean added = tables.add(newt);
			if(!added) {
				tables.remove(newt);
				added = tables.add(newt);
				if(!added) {
					log.warn("error adding table: "+name);
				}
			}
			*/
		}
		rs.close();
		return ret;
	}
	
}
