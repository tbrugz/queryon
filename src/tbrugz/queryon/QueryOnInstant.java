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
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Sequence;
import tbrugz.sqldump.dbmodel.Synonym;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.TableType;
import tbrugz.sqldump.dbmodel.Trigger;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.resultset.ResultSetListAdapter;
import tbrugz.sqldump.util.ConnectionUtil;

/*
 * TODO: 'instant' servlets SHOULD NOT modify model, right?
 */
public class QueryOnInstant extends QueryOn {
	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(QueryOnInstant.class);
	
	static final TableType[] tableTypes = new TableType[]{ TableType.TABLE };
	static final TableType[] viewTypes = new TableType[]{ TableType.VIEW }; //, TableType.MATERIALIZED_VIEW, TableType.SYSTEM_VIEW };
	static final TableType[] materializedViewTypes = new TableType[]{ TableType.MATERIALIZED_VIEW };
	
	static final List<TableType> tableTypesList = Arrays.asList(tableTypes);
	static final List<TableType> viewTypesList = Arrays.asList(viewTypes);
	static final List<TableType> materializedViewTypesList = Arrays.asList(materializedViewTypes);
	
	static final List<String> statusTriggerAllColumns;
	//static final List<String> statusSynonymAllColumns;
	static {
		statusTriggerAllColumns = new ArrayList<String>(); statusTriggerAllColumns.addAll(statusUniqueColumns); statusTriggerAllColumns.addAll(Arrays.asList(new String[]{"tableName"}));
		//statusSynonymAllColumns = new ArrayList<String>(); statusSynonymAllColumns.addAll(statusUniqueColumns); statusSynonymAllColumns.addAll(Arrays.asList(new String[]{"objectOwner", "referencedObject"}));
	}
	
	@SuppressWarnings("resource")
	@Override
	void doStatus(SchemaModel model, DBObjectType statusType, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws IntrospectionException, SQLException, IOException, ServletException, ClassNotFoundException, NamingException {
		if(reqspec.params==null || reqspec.params.size()<1) {
			throw new BadRequestException("no schema informed");
		}
		
		final Connection conn = DBUtil.initDBConn(prop, reqspec.modelId);

		try {
		final DBMSResources res = DBMSResources.instance();
		//String dbid = res.detectDbId(conn.getMetaData(), true);
		//final DBMSFeatures feat = res.getSpecificFeatures(dbid);
		final DBMSFeatures feat = res.getSpecificFeatures(conn.getMetaData());
		
		//res.updateMetaData(conn.getMetaData(), true);
		//final DBMSFeatures feat = res.databaseSpecificFeaturesClass();
		final DatabaseMetaData dbmd = feat.getMetadataDecorator(conn.getMetaData());
		final String schemaName = reqspec.params.get(0);
		ResultSet rs;
		
		//final DBObjectType type = DBObjectType.valueOf(reqspec.object.toUpperCase());
		final String objectName = statusType.desc();
		//log.info("doStatus: "+statusType+" ; "+objectName);
		
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
		case MATERIALIZED_VIEW: {
			List<View> rels = new ArrayList<View>();
			//feat.grabDBViews(rels, schemaName, null, conn);
			//keepRelationsByType(rels, "materialized view");
			feat.grabDBMaterializedViews(rels, schemaName, null, conn);
			rs = new ResultSetListAdapter<View>(objectName, statusUniqueColumns, viewAllColumns, rels, View.class);
			
			//List<Relation> rels = grabRelationNames(schemaName, dbmd, viewTypesList);
			//keepRelationsByType(rels, "materialized view");
			//List<Relation> rels = grabRelationNames(schemaName, dbmd, materializedViewTypesList);
			//rs = new ResultSetListAdapter<Relation>(objectName, statusUniqueColumns, viewAllColumns, rels, Relation.class);
			break;
		}
		case RELATION: {
			throw new BadRequestException("status not implemented for "+statusType+" object");
			
			//grabTables(model, schemaName, conn.getMetaData());
			//feat.grabDBViews(model, schemaName, conn); //XXX: too much data?
		}
		case FUNCTION: {
			JDBCSchemaGrabber jgrab = new JDBCSchemaGrabber();
			List<ExecutableObject> func = grabExecutables(jgrab, dbmd, schemaName, true);
			removeExecsWithinPackages(func);
			rs = new ResultSetListAdapter<ExecutableObject>(objectName, statusUniqueColumns, func, ExecutableObject.class);
			//XXXdone: filter by type 'FUNCTION', filter by packageName == null ?
			break;
		}
		case PROCEDURE: {
			//XXXxx: procedures/functions: remove elements with catalog!=null (element belogs to package - oracle)
			JDBCSchemaGrabber jgrab = new JDBCSchemaGrabber();
			List<ExecutableObject> proc = grabExecutables(jgrab, dbmd, schemaName, false);
			removeExecsWithinPackages(proc);
			keepExecsByType(proc, DBObjectType.PROCEDURE);
			rs = new ResultSetListAdapter<ExecutableObject>(objectName, statusUniqueColumns, proc, ExecutableObject.class);
			//XXXdone: filter by type 'PROCEDURE', filter by packageName == null ?
			break;
		}
		case PACKAGE_BODY:
		case PACKAGE: {
			//XXXxx: packages: get package names from procedures/functions catalog names
			JDBCSchemaGrabber jgrab = new JDBCSchemaGrabber();
			List<ExecutableObject> proc = grabExecutables(jgrab, dbmd, schemaName, false);
			List<ExecutableObject> func = grabExecutables(jgrab, dbmd, schemaName, true);
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
		/*case INDEX: {
			List<Index> indexes = grabIndexes(schemaName, dbmd);
			log.info("#indexes: "+indexes.size());

			rs = new ResultSetListAdapter<Index>(objectName, statusUniqueColumns, indexes, Index.class);
			break;
		}*/
		case TRIGGER: {
			List<Trigger> triggers = new ArrayList<Trigger>();
			//XXX: DBMSFeatures show have a grabDbTriggerNames ... trigger body is retrieved every time...
			feat.grabDBTriggers(triggers, schemaName, null, null, conn);
			//log.info("#triggers: "+triggers.size());

			rs = new ResultSetListAdapter<Trigger>(objectName, statusTriggerAllColumns, triggers, Trigger.class);
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
		case SEQUENCE: {
			List<Sequence> seqs = new ArrayList<Sequence>();
			feat.grabDBSequences(seqs, schemaName, null, conn);
			log.info("#sequences: "+seqs.size());

			rs = new ResultSetListAdapter<Sequence>(objectName, statusUniqueColumns, seqs, Sequence.class);
			break;
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
		case SYNONYM: {
			List<Synonym> synonyms = new ArrayList<Synonym>();
			feat.grabDBSynonyms(synonyms, schemaName, null, conn);
			log.info("#synonyms: "+synonyms.size()); //+" ; synonyms: "+synonyms+" ; cols:: "+statusSynonymAllColumns);

			rs = new ResultSetListAdapter<Synonym>(objectName, statusUniqueColumns, /*statusSynonymAllColumns,*/ synonyms, Synonym.class);
			break;
		}
		default: {
			log.warn("doStatus: unknown object: "+statusType);
			throw new BadRequestException("unknown object: "+statusType);
		}
		}
		
		rs = filterStatus(rs, reqspec, currentUser, PrivilegeType.SELECT); //XXX: should be SHOW privilege?
		
		dumpResultSet(rs, reqspec, null, objectName, statusUniqueColumns, null, null, true, resp);
		if(rs!=null) { rs.close(); }
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}
	
	static List<Relation> grabRelationNames(String schemaName, DatabaseMetaData dbmd, List<TableType> tableTypesList) throws SQLException {
		List<Relation> ret = new ArrayList<Relation>();
		long initTime = System.currentTimeMillis();
		//String[] ttypes = null;
		//if(tableTypesList!=null) { ttypes = tableTypeArr2StringArr(tableTypesList); }
		//ResultSet rs = dbmd.getTables(null, schemaName, null, ttypes);
		ResultSet rs = dbmd.getTables(null, schemaName, null, null);
		long elapsed = (System.currentTimeMillis()-initTime);
		//taking too long? monitor generated SQL? jdbc connection proxy?
		log.debug("grabRelationNames: elapsed = "+elapsed);
		int count = 0;
		while(rs.next()) {
			Table newt = newTable(rs, schemaName);
			if(!tableTypesList.contains(newt.getType())) continue;

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
		log.info(count+" relations retrieved [elapsed="+elapsed+"ms]");
		return ret;
	}

	/*
	static List<Relation> grabRelationNames(String schemaName, DatabaseMetaData dbmd, TableType tableType) throws SQLException {
		List<Relation> ret = new ArrayList<Relation>();
		long initTime = System.currentTimeMillis();
		ResultSet rs = dbmd.getTables(null, schemaName, null, new String[]{ tableType.getName() });
		long elapsed = (System.currentTimeMillis()-initTime);
		log.info("grabRelationNames[2]: elapsed = "+elapsed);
		int count = 0;
		while(rs.next()) {
			Table newt = newTable(rs, schemaName);
			if(tableType!=newt.getType()) continue;

			ret.add(newt);
			count++;
		}
		log.info(count+" relations retrieved [elapsed="+elapsed+"ms]");
		return ret;
	}
	*/
	
	static Table newTable(ResultSet rs, String schemaName) throws SQLException {
		String name = rs.getString("TABLE_NAME");
		//Table t = DBIdentifiable.getDBIdentifiableBySchemaAndName(model.getTables(), schemaName, name);
		String type = rs.getString("TABLE_TYPE");
		TableType ttype = TableType.getTableType(type, name);
		//if(t!=null) { continue; }
		
		Table newt = new Table();
		newt.setSchemaName(schemaName);
		newt.setName(name);
		newt.setType(ttype);
		newt.setRemarks(rs.getString("REMARKS"));
		return newt;
	}
	
	/*
	static List<Index> grabIndexes(String schemaName, String tableName, DatabaseMetaData dbmd) throws SQLException {
		List<Index> ret = new ArrayList<Index>();
		//long initTime = System.currentTimeMillis();
		
		//XXX: table name must not be null :(
		ResultSet indexesrs = dbmd.getIndexInfo(null, schemaName, tableName, false, false);
		JDBCSchemaGrabber.grabSchemaIndexes(indexesrs, ret);
		JDBCSchemaGrabber.closeResultSetAndStatement(indexesrs);
		
		//long elapsed = (System.currentTimeMillis()-initTime);
		//log.debug("grabIndexes: elapsed = "+elapsed);
		return ret;
	}
	*/
	
	static List<ExecutableObject> grabExecutables(JDBCSchemaGrabber jgrab, DatabaseMetaData dbmd, String schemaName, boolean grabFunctions) {
		try {
			if(grabFunctions) {
				return jgrab.doGrabFunctions(dbmd, schemaName, false);
			}
			return jgrab.doGrabProcedures(dbmd, schemaName, false);
		}
		catch(LinkageError e) {
			log.warn("abstract method error: "+e);
		}
		catch(RuntimeException e) {
			log.warn("runtime exception grabbing functions: "+e);
		}
		catch(SQLException e) {
			log.warn("sql exception grabbing functions: "+e);
		}
		return new ArrayList<ExecutableObject>();
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
		log.info("keepExecsByType:: iniSize="+initSize+" ; removed="+removed+" ; finalSize="+execs.size());
	}

	static void keepRelationsByType(List<? extends Relation> relations, String relationType) {
		int initSize = relations.size();
		int removed = 0;
		Iterator<? extends Relation> iter = relations.iterator();
		while(iter.hasNext()){
			Relation v = iter.next();
			if(!v.getRelationType().equals(relationType)) {
				iter.remove();
				removed++;
			}
		}
		log.info("keepRelationsByType:: iniSize="+initSize+" ; removed="+removed+" ; finalSize="+relations.size());
	}
	
	/*
	static String[] tableTypeArr2StringArr(List<TableType> types) {
		String[] ret = new String[types.size()];
		for(int i=0;i<types.size();i++) {
			ret[i] = types.get(i).toString();
		}
		return ret;
	}
	*/
	
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
