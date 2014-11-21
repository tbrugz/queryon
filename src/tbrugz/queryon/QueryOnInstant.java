package tbrugz.queryon;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.sqldump.JDBCSchemaGrabber;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.Column;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.FK;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.TableType;
import tbrugz.sqldump.def.DBMSResources;

public class QueryOnInstant extends QueryOn {
	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(QueryOnInstant.class);
	
	static final TableType[] tableTypes = new TableType[]{ TableType.TABLE };
	static final List<TableType> tableTypesList = Arrays.asList(tableTypes);
	
	@Override
	void doStatus(SchemaModel model, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws IntrospectionException, SQLException, IOException, ServletException, ClassNotFoundException, NamingException {
		if(reqspec.params==null || reqspec.params.size()<1) {
			throw new BadRequestException("no schema informed");
		}
		
		Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		DBMSResources res = DBMSResources.instance();
		DBMSFeatures feat = res.databaseSpecificFeaturesClass();
		String schemaName = reqspec.params.get(0);
		
		if(SO_TABLE.equalsIgnoreCase(reqspec.object)) {
			grabTablesNames(model, schemaName, conn.getMetaData());
		}
		else if(SO_VIEW.equalsIgnoreCase(reqspec.object)) {
			feat.grabDBViews(model, schemaName, conn); //XXX: too much data?
		}
		else if(SO_RELATION.equalsIgnoreCase(reqspec.object)) {
			throw new BadRequestException("status not implemented for "+reqspec.object+" object");
			
			//grabTables(model, schemaName, conn.getMetaData());
			//feat.grabDBViews(model, schemaName, conn); //XXX: too much data?
		}
		else if(SO_EXECUTABLE.equalsIgnoreCase(reqspec.object)) {
			feat.grabDBExecutables(model, schemaName, conn); //XXX: too much data?
		}
		else if(SO_FK.equalsIgnoreCase(reqspec.object)) {
			grabFKs(model, schemaName, conn.getMetaData());
		}
		else {
			throw new BadRequestException("unknown object: "+reqspec.object);
		}
		
		conn.close();
		
		super.doStatus(model, reqspec, currentUser, resp);
	}
	
	static void grabTablesNames(SchemaModel model, String schemaName, DatabaseMetaData dbmd) throws SQLException {
		ResultSet rs = dbmd.getTables(null, schemaName, null, null);
		while(rs.next()) {
			String name = rs.getString("TABLE_NAME");
			Table t = DBIdentifiable.getDBIdentifiableBySchemaAndName(model.getTables(), schemaName, name);
			if(t!=null) { continue; }
			TableType ttype = TableType.getTableType(rs.getString("TABLE_TYPE"), name);
			if(!tableTypesList.contains(ttype)) { continue; }
			
			Table newt = new Table();
			newt.setSchemaName(schemaName);
			newt.setName(name);
			newt.setRemarks(rs.getString("REMARKS"));
			boolean added = model.getTables().add(newt);
			if(!added) {
				log.warn("error adding table: "+name);
			}
		}
	}

	static void grabTable(SchemaModel model, String schemaName, String tableName, DatabaseMetaData dbmd, DBMSFeatures feat) throws SQLException {
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
	
	static void grabFKs(SchemaModel model, String schemaName, DatabaseMetaData dbmd) throws SQLException {
		ResultSet fkrs = dbmd.getExportedKeys(null, schemaName, null);
		int count = 0;
		while(fkrs.next()) {
			String fkName = fkrs.getString("FK_NAME");
			FK fk = DBIdentifiable.getDBIdentifiableBySchemaAndName(model.getForeignKeys(), schemaName, fkName);
			if(fk!=null) { continue; }
			
			FK newfk = new FK();
			newfk.setSchemaName(schemaName);
			newfk.setName(fkName);
			newfk.setFkTable(fkrs.getString("FKTABLE_NAME"));
			newfk.setPkTable(fkrs.getString("PKTABLE_NAME"));
			model.getForeignKeys().add(newfk);
			count++;
		}
		log.info("FKs count = "+count);
	}
}
