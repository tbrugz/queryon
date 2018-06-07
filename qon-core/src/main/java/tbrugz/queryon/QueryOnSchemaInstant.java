package tbrugz.queryon;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.exception.NotFoundException;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldiff.model.ColumnDiff;
import tbrugz.sqldump.JDBCSchemaGrabber;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.Column;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.Utils;

/*
 * TODOne: 'instant' servlets SHOULD NOT modify model, right?
 */
public class QueryOnSchemaInstant extends QueryOnSchema {

	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(QueryOnSchemaInstant.class);
	
	boolean doSchemaGrabTableGrants = false,
			doSchemaGrabTableIndexes = false,
			doSchemaGrabTableTriggers = false;

	@Override
	void dumpObject(DBObjectType type, Properties prop, String modelId, SchemaModel model, String schemaName, String objectName, HttpServletResponse resp)
			throws ClassNotFoundException, SQLException, NamingException, IOException {
		/*Connection conn = DBUtil.initDBConn(prop, modelId);
		try {
			DBIdentifiable dbid = getObject(type, schemaName, objectName, conn);
			if(dbid==null) {
				throw new BadRequestException("null object? [ "+schemaName+"."+objectName+" ; type="+type+" ]");
			}
			dump(dbid, resp);
		}
		finally {
			conn.close();
		}*/

		DBIdentifiable dbid = getObject(type, schemaName, objectName, model, prop, modelId);
		dump(dbid, resp);
	}

	@Override
	public DBIdentifiable getObject(DBObjectType type, String schemaName, String objectName, SchemaModel model, Properties prop, String modelId) throws SQLException, ClassNotFoundException, NamingException {
		Connection conn = null;
		try {
			conn = DBUtil.initDBConn(prop, modelId);
			DBIdentifiable dbid = getObject(type, schemaName, objectName, conn);
			if(dbid==null) {
				throw new NotFoundException("null object? [ "+schemaName+"."+objectName+" ; type="+type+" ]");
			}
			lastDialect = DBMSResources.instance().detectDbId(conn.getMetaData());
			//log.debug("lastDialect: "+getLastDialect());
			return dbid;
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public DBIdentifiable getObject(DBObjectType type, String schemaName, String objectName, Connection conn) throws SQLException {
		final DBMSResources res = DBMSResources.instance();
		//res.updateMetaData(conn.getMetaData(), true);
		//DBMSFeatures feat = res.databaseSpecificFeaturesClass();
		final DBMSFeatures feat = res.getSpecificFeatures(conn.getMetaData());
		DatabaseMetaData dbmd = feat.getMetadataDecorator(conn.getMetaData());
		
		//log.info("feats: "+feat.getClass().getName()+" metadata: "+dbmd.getClass().getName());

		List ret = new ArrayList();
		
		switch(type) {
		case TABLE:
			ColumnDiff.updateFeatures(feat);
			Table t = grabTable(schemaName, objectName, dbmd, feat); // grab table & update model
			//dump(t, resp);
			//XXX: grab (& dump) triggers & FKs?
			return t;
			//break;
		case VIEW:
			//log.info("before:: #view = "+model.getViews().size()+" ["+schemaName+"."+objectName+"]");
			//List<View> views = new ArrayList<View>();
			feat.grabDBViews(ret, schemaName, objectName, conn);
			break;
		case MATERIALIZED_VIEW:
			feat.grabDBMaterializedViews(ret, schemaName, objectName, conn);
			break;
		case PROCEDURE:
		case FUNCTION:
		case JAVA_SOURCE:
		case PACKAGE:
		case PACKAGE_BODY:
		case TYPE:
		case TYPE_BODY:
		case EXECUTABLE:
			feat.grabDBExecutables(ret, schemaName, objectName, conn);
			if(type==DBObjectType.PACKAGE || type==DBObjectType.PACKAGE_BODY) {
				QueryOnInstant.keepExecsByType(ret, type); //DBObjectType.PACKAGE_BODY);
			}
			break;
		case TRIGGER:
			//XXX: really grab trigger?
			feat.grabDBTriggers(ret, schemaName, null, objectName, conn);
			break;
		case SEQUENCE:
			feat.grabDBSequences(ret, schemaName, objectName, conn);
			break;
		case FK:
			//XXX how to get FK from dbmd by name (only filtering by table or schema...)? grab from model (the 'cache')? not "instant"...
			log.info("FK [type "+type+"] '"+objectName+"' cannot be grabbed");
			break;
		case SYNONYM:
			feat.grabDBSynonyms(ret, schemaName, objectName, conn);
			break;
		default:
			log.warn("object of type "+type+" cannot be grabbed");
		}

		if(ret.size()==0) {
			throw new NotFoundException("Object of type '"+type+"' not found: "+schemaName+"."+objectName);
		}
		if(ret.size()>1) {
			log.warn("more than one [#"+ret.size()+"] object of type '"+type+"' grabbed: "+schemaName+"."+objectName);
		}

		return (DBIdentifiable) ret.get(0);
	}
	
	void dump(DBIdentifiable dbid, HttpServletResponse resp) throws IOException {
		resp.setContentType(MIME_SQL);
		if(dbid==null) {
			throw new BadRequestException("null object?");
		}
		if(dbid instanceof Relation) {
			Constraint pk = SchemaModelUtils.getPK((Relation) dbid);
			if(pk!=null && pk.getUniqueColumns()!=null) {
				resp.addHeader(ResponseSpec.HEADER_RELATION_UK, Utils.join(pk.getUniqueColumns(), ", "));
			}
		}
		resp.getWriter().write(dbid.getDefinition(true));
		writeFooter(dbid, resp.getWriter());
	}
	
	Table grabTable(String schemaName, String tableName, DatabaseMetaData dbmd, DBMSFeatures feat) throws SQLException {
		if(tableName==null) throw new IllegalArgumentException("tableName cannot be null");
		String fullTableName = (schemaName!=null?schemaName+".":"")+tableName;
		log.debug("grabbing new table: "+fullTableName);
		Table ret = null;
		String[] ttypes = QueryOnInstant.tableTypeArr2StringArr(QueryOnInstant.tableTypes);
		ResultSet rs = dbmd.getTables(null, schemaName, tableName, ttypes);
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
				feat.addColumnSpecificFeatures(c, cols);
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
				newt.setGrants( grabber.grabSchemaGrants(grantrs, false) );
				JDBCSchemaGrabber.closeResultSetAndStatement(grantrs);
				//XXX: grab column privileges ?
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
