package tbrugz.queryon;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.sqldump.JDBCSchemaGrabber;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.FK;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.TableType;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.resultset.ResultSetListAdapter;

/*
 * TODO: 'instant' servlets SHOULD NOT modify model, right?
 */
public class QueryOnInstant extends QueryOn {
	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(QueryOnInstant.class);
	
	static final TableType[] tableTypes = new TableType[]{ TableType.TABLE };
	static final TableType[] viewTypes = new TableType[]{ TableType.VIEW, TableType.MATERIALIZED_VIEW, TableType.SYSTEM_VIEW };
	
	static final List<TableType> tableTypesList = Arrays.asList(tableTypes);
	static final List<TableType> viewTypesList = Arrays.asList(viewTypes);
	
	@Override
	void doStatus(SchemaModel model, DBObjectType statusType, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws IntrospectionException, SQLException, IOException, ServletException, ClassNotFoundException, NamingException {
		if(reqspec.params==null || reqspec.params.size()<1) {
			throw new BadRequestException("no schema informed");
		}
		
		final Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);
		final DBMSResources res = DBMSResources.instance();
		final DBMSFeatures feat = res.databaseSpecificFeaturesClass();
		final DatabaseMetaData dbmd = feat.getMetadataDecorator(conn.getMetaData());
		final String schemaName = reqspec.params.get(0);
		ResultSet rs;
		
		//final DBObjectType type = DBObjectType.valueOf(reqspec.object.toUpperCase());
		final String objectName = statusType.desc();
		
		switch (statusType) {
		case TABLE: {
			List<Relation> rels = grabRelationNames(schemaName, dbmd, tableTypesList);
			rs = new ResultSetListAdapter<Relation>(objectName, statusUniqueColumns, tableAllColumns, rels, Relation.class);
			break;
		}
		case VIEW: {
			List<Relation> rels = grabRelationNames(schemaName, dbmd, viewTypesList);
			rs = new ResultSetListAdapter<Relation>(objectName, statusUniqueColumns, viewAllColumns, rels, Relation.class);
			break;
		}
		case RELATION: {
			throw new BadRequestException("status not implemented for "+statusType+" object");
			
			//grabTables(model, schemaName, conn.getMetaData());
			//feat.grabDBViews(model, schemaName, conn); //XXX: too much data?
		}
		case FUNCTION: {
			JDBCSchemaGrabber jgrab = new JDBCSchemaGrabber();
			List<ExecutableObject> func = jgrab.doGrabFunctions(dbmd, schemaName, false);
			removeExecsWithinPackages(func);
			rs = new ResultSetListAdapter<ExecutableObject>(objectName, statusUniqueColumns, func, ExecutableObject.class);
			//XXX: filter by type 'FUNCTION', filter by packageName == null ?
			break;
		}
		case PROCEDURE: {
			//XXXxx: procedures/functions: remove elements with catalog!=null (element belogs to package - oracle)
			JDBCSchemaGrabber jgrab = new JDBCSchemaGrabber();
			List<ExecutableObject> proc = jgrab.doGrabProcedures(dbmd, schemaName, false);
			removeExecsWithinPackages(proc);
			keepExecsByType(proc, DBObjectType.PROCEDURE);
			rs = new ResultSetListAdapter<ExecutableObject>(objectName, statusUniqueColumns, proc, ExecutableObject.class);
			//XXX: filter by type 'PROCEDURE', filter by packageName == null ?
			break;
		}
		case PACKAGE_BODY:
		case PACKAGE: {
			//XXXxx: packages: get package names from procedures/functions catalog names
			JDBCSchemaGrabber jgrab = new JDBCSchemaGrabber();
			List<ExecutableObject> proc = jgrab.doGrabProcedures(dbmd, schemaName, false);
			List<ExecutableObject> func = jgrab.doGrabFunctions(dbmd, schemaName, false);
			proc.addAll(func);

			List<ExecutableObject> pkgs = new ArrayList<ExecutableObject>();
			Set<String> pkgNames = new TreeSet<String>();
			for(ExecutableObject eo: proc) {
				if(eo.getPackageName()!=null) {
					pkgNames.add(eo.getPackageName());
				}
			}
			for(String pkg: pkgNames) {
				ExecutableObject eo = new ExecutableObject();
				eo.setName(pkg);
				eo.setSchemaName(schemaName);
				eo.setType(DBObjectType.PACKAGE);
				pkgs.add(eo);
			}

			rs = new ResultSetListAdapter<ExecutableObject>(objectName, statusUniqueColumns, pkgs, ExecutableObject.class);
			break;
		}
		case EXECUTABLE: {
			throw new BadRequestException("status not implemented for "+statusType+" object");
			//TODOne: split executable's types
			/*
			JDBCSchemaGrabber jgrab = new JDBCSchemaGrabber();
			
			//long initT = System.currentTimeMillis();
			List<ExecutableObject> proc = jgrab.doGrabProcedures(dbmd, schemaName, false);
			List<ExecutableObject> func = jgrab.doGrabFunctions(dbmd, schemaName, false);
			//System.out.println("grab executables on ["+schemaName+"]: elapsed="+(System.currentTimeMillis()-initT));
			
			proc.addAll(func);

			rs = new ResultSetListAdapter<ExecutableObject>(objectName, statusUniqueColumns, proc, ExecutableObject.class);
			break;
			*/
		}
		case FK: {
			//List<FK> list = grabFKs(schemaName, dbmd);
			
			ResultSet fkrs = dbmd.getImportedKeys(null, schemaName, null);
			List<FK> list = JDBCSchemaGrabber.grabSchemaFKs(fkrs, feat);
			
			//XXXxx: not caching into model...
			//model.getForeignKeys().addAll(list);
			rs = new ResultSetListAdapter<FK>(objectName, statusUniqueColumns, list, FK.class);
			break;
		}
		default: {
			conn.close();
			throw new BadRequestException("unknown object: "+statusType);
		}
		}
		
		conn.close();
		
		rs = filterStatus(rs, reqspec, currentUser);
		
		dumpResultSet(rs, reqspec, objectName, statusUniqueColumns, null, null, true, resp);
		if(rs!=null) { rs.close(); }
	}
	
	static List<Relation> grabRelationNames(String schemaName, DatabaseMetaData dbmd, List<TableType> tableTypesList) throws SQLException {
		List<Relation> ret = new ArrayList<Relation>();
		ResultSet rs = dbmd.getTables(null, schemaName, null, null);
		int count = 0;
		while(rs.next()) {
			String name = rs.getString("TABLE_NAME");
			//Table t = DBIdentifiable.getDBIdentifiableBySchemaAndName(model.getTables(), schemaName, name);
			String type = rs.getString("TABLE_TYPE");
			TableType ttype = TableType.getTableType(type, name);
			if(!tableTypesList.contains(ttype)) {
				//log.info("ignored table: "+name+" ["+type+"/"+ttype+"] should be of type ["+tableTypesList+"]");
				continue;
			}
			//if(t!=null) { continue; }
			
			Table newt = new Table();
			newt.setSchemaName(schemaName);
			newt.setName(name);
			newt.setType(ttype);
			newt.setRemarks(rs.getString("REMARKS"));
			ret.add(newt);
			count++;
			//boolean added = model.getTables().add(newt);
			/*if(!added) {
				log.warn("error adding table: "+name);
			}
			else {
				count++;
			}*/
		}
		log.info(count+" objects added");
		return ret;
	}
	
	static void removeExecsWithinPackages(List<ExecutableObject> execs) {
		int initSize = execs.size();
		int removed = 0;
		Iterator<ExecutableObject> iter = execs.iterator();
		while(iter.hasNext()){
			ExecutableObject eo = iter.next();
			if(eo.getPackageName()!=null) {
				iter.remove();
				removed++;
			}
		}
		log.info("removeExecsWithinPackages:: iniSize="+initSize+" ; removed="+removed+" ; finalSize="+execs.size());
	}

	static void keepExecsByType(List<ExecutableObject> execs, DBObjectType type) {
		int initSize = execs.size();
		int removed = 0;
		Iterator<ExecutableObject> iter = execs.iterator();
		while(iter.hasNext()){
			ExecutableObject eo = iter.next();
			if(eo.getType()!=type) {
				iter.remove();
				removed++;
			}
		}
		log.info("removeExecsByType:: iniSize="+initSize+" ; removed="+removed+" ; finalSize="+execs.size());
	}
	
	/*
	static List<FK> grabFKs(String schemaName, DatabaseMetaData dbmd) throws SQLException {
		List<FK> ret = new ArrayList<FK>();
		ResultSet fkrs = dbmd.getExportedKeys(null, schemaName, null);
		int count = 0;
		while(fkrs.next()) {
			String fkName = fkrs.getString("FK_NAME");
			//FK fk = DBIdentifiable.getDBIdentifiableBySchemaAndName(model.getForeignKeys(), schemaName, fkName);
			//if(fk!=null) { continue; }
			
			FK newfk = new FK();
			newfk.setSchemaName(schemaName); //fkSchemaName
			newfk.setName(fkName);
			newfk.setFkTable(fkrs.getString("FKTABLE_NAME"));
			newfk.setPkTable(fkrs.getString("PKTABLE_NAME"));
			newfk.setPkTableSchemaName(fkrs.getString("PKTABLE_SCHEM"));
			ret.add(newfk);
			count++;
		}
		//log.info("FKs count = "+count);
		return ret;
	}
	*/
}