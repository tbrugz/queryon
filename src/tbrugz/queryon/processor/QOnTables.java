package tbrugz.queryon.processor;

import java.io.IOException;
import java.io.Writer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.SQL;
import tbrugz.queryon.UpdatePlugin;
import tbrugz.sqldump.JDBCSchemaGrabber;
import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.dbmodel.Column;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.Constraint.ConstraintType;
import tbrugz.sqldump.def.AbstractSQLProc;
import tbrugz.sqldump.util.StringDecorator;
import tbrugz.sqldump.util.Utils;

public class QOnTables extends AbstractSQLProc implements UpdatePlugin {

	static final Log log = LogFactory.getLog(QOnTables.class);
	
	public static class StringQuoterEscaperDecorator extends StringDecorator {
		final String quote;
		final String quotePtrn;
		
		public StringQuoterEscaperDecorator(String quote) {
			this.quote = quote;
			this.quotePtrn = Pattern.quote(quote);
		}
		
		@Override
		public String get(String str) {
			return str==null?null:quote+str.replaceAll(quotePtrn, "")+quote;
		}
	}
	
	static final String PROP_PREFIX = "queryon.qon-tables";
	
	static final String SUFFIX_ACTION = ".action";
	static final String SUFFIX_TABLE = ".table";
	static final String SUFFIX_TABLE_NAMES = ".names";

	static final String ACTION_READ = "read";
	
	static final String PIPE_SPLIT = "\\|";
	
	public static final String DEFAULT_TABLES_TABLE = "QON_TABLES";
	
	public static StringDecorator sqlStringValuesDecorator = new StringQuoterEscaperDecorator("'");

	Writer writer;
	
	public void process() {
		try {
			String action = prop.getProperty(PROP_PREFIX+SUFFIX_ACTION, ACTION_READ);
			if(ACTION_READ.equals(action)) {
				int count = readFromDatabase();
				if(writer!=null) {
					writer.write(String.valueOf(count));
				}
			}
		} catch (SQLException e) {
			//e.printStackTrace();
			throw new BadRequestException("SQL exception: "+e, e);
		} catch (IOException e) {
			//e.printStackTrace();
			throw new BadRequestException("IO exception: "+e, e);
		}
	}
	
	int readFromDatabase() throws SQLException {
		String qonTablesTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_TABLES_TABLE);
		List<String> tables = Utils.getStringListFromProp(prop, PROP_PREFIX+SUFFIX_TABLE_NAMES, ",");
		String sql = "select schema_name, name, column_names, pk_column_names, column_remarks, remarks"
				+", roles_select, roles_insert, roles_update, roles_delete"
				+", roles_insert_columns, roles_update_columns"
				+" from "+qonTablesTable
				+" where (disabled = 0 or disabled is null)"
				+(tables!=null?" and name in ("+Utils.join(tables, ",", sqlStringValuesDecorator)+")":""); //XXX: possible sql injection?
		
		ResultSet rs = null;
		try {
			PreparedStatement st = conn.prepareStatement(sql);
			rs = st.executeQuery();
		}
		catch(SQLException e) {
			throw new SQLException("Error fetching tables, sql: "+sql, e);
		}

		int count = 0;
		while(rs.next()) {
			String schema = rs.getString(1);
			String tableName = rs.getString(2);
			String columnNames = rs.getString(3);
			String pkColumnNamesStr = rs.getString(4);
			String columnRemarksStr = rs.getString(5);
			String remarks = rs.getString(6);
			String rolesSelectFilterStr = rs.getString(7);
			String rolesInsertFilterStr = rs.getString(8);
			String rolesUpdateFilterStr = rs.getString(9);
			String rolesDeleteFilterStr = rs.getString(10);
			String rolesInsertColumnsFilterStr = rs.getString(11);
			String rolesUpdateColumnsFilterStr = rs.getString(12);
			//XXX default_select_filter
			//XXXdone default_select_projection: column_names
			
			List<String> pkColumnNames = Utils.getStringList(pkColumnNamesStr, ",");
			List<String> columnRemarks = Utils.getStringList(columnRemarksStr, PIPE_SPLIT);
			
			try {
				List<String> rolesSelect = Utils.getStringList(rolesSelectFilterStr, PIPE_SPLIT);
				List<String> rolesInsert = Utils.getStringList(rolesInsertFilterStr, PIPE_SPLIT);
				List<String> rolesUpdate = Utils.getStringList(rolesUpdateFilterStr, PIPE_SPLIT);
				List<String> rolesDelete = Utils.getStringList(rolesDeleteFilterStr, PIPE_SPLIT);
				
				count += addTable(schema, tableName, columnNames, pkColumnNames, columnRemarks, remarks,
						rolesSelect, rolesInsert, rolesUpdate, rolesDelete,
						rolesInsertColumnsFilterStr, rolesUpdateColumnsFilterStr);
			}
			catch(RuntimeException e) {
				log.warn("error reading table '"+tableName+"': "+e);
			}
		}
		
		log.info("QOnTables processed [added/replaced "+count+" tables]");
		return count;
	}

	private int addTable(String schema, String tableName, String columnNames, List<String> pkColumnNames, List<String> columnRemarks,
			String remarks, List<String> rolesSelect, List<String> rolesInsert, List<String> rolesUpdate, List<String> rolesDelete,
			String rolesInsertColumnsFilterStr, String rolesUpdateColumnsFilterStr) {
		Table t = new Table();
		t.setSchemaName(schema);
		t.setName(tableName);
		//XXX table type: what if it is a view? t.setType(TableType.VIEW); ?
		//XXXdone option to add primary key (for tables/views that doesn't have them, or to setup a different PK for a table)

		if(!SQL.valid(columnNames)) {
			columnNames = "*";
		}
		
		String sql = "select "+columnNames+" from "
				+ (SQL.valid(schema)?SQL.sqlIdDecorator.get(schema)+".":"")
				+ (SQL.sqlIdDecorator.get(tableName))
				;
		if(validColumnList(pkColumnNames)) {
			for(int i=0;i<pkColumnNames.size();i++) {
				if(!"".equals(pkColumnNames.get(i))) {
					sql += (i==0?" where ":" and ")+pkColumnNames.get(i) + " = ? ";
				}
			}
		}
		
		//XXX validate pk_column_names
		/*
		 * PreparedStatement.getMetaData:
		 * http://stackoverflow.com/questions/14245411/returning-just-column-names-of-resultset-without-actually-performing-the-query
		 * http://stackoverflow.com/questions/9207073/column-names-for-an-ad-hoc-sql
		 */
		try {
			PreparedStatement stmt = conn.prepareStatement(sql);
			ResultSetMetaData rsmd = stmt.getMetaData();
			t.setColumns(DataDumpUtils.getColumns(rsmd));
		}
		catch(SQLException e) {
			// XXX get conn info (closed, valid)
			String message = "addTable ["+tableName+"]: exception: "+e.getMessage().trim();
			log.warn(message+"\nsql: "+sql, e);
			throw new BadRequestException(message, "\nsql: "+sql, e);
		}
		
		//log.info("pkColumnNames: ["+pkColumnNames+"]");
		
		if(validColumnList(pkColumnNames)) {
			Constraint pk = new Constraint();
			pk.setType(ConstraintType.PK);
			pk.setUniqueColumns(pkColumnNames);
			t.getConstraints().add(pk);
			//log.info("pk: ["+pk+"]");
		}
		else {
			try {
				List<Constraint> pks = JDBCSchemaGrabber.grabRelationPKs(conn.getMetaData(), t);
				t.getConstraints().addAll(pks);
				//log.info("pks: ["+pks+"]");
			}
			catch(SQLException e) {
				String message = "addTable ["+tableName+"]: grabRelationPKs: exception: "+e.getMessage().trim();
				log.warn(message);
			}
		}

		if(columnRemarks!=null) {
			List<Column> cols = t.getColumns();
			for(int i=0;i<cols.size();i++) {
				if(cols.get(i)!=null && columnRemarks.size()>i) {
					cols.get(i).setRemarks(columnRemarks.get(i));
				}
			}
		}
		
		//log.info("PKs: "+pks);
		
		List<Grant> grants = t.getGrants();
		addGrants(grants, tableName, PrivilegeType.SELECT, rolesSelect);
		addGrants(grants, tableName, PrivilegeType.INSERT, rolesInsert);
		addGrants(grants, tableName, PrivilegeType.UPDATE, rolesUpdate);
		addGrants(grants, tableName, PrivilegeType.DELETE, rolesDelete);
		
		addColumnGrants(grants, tableName, PrivilegeType.INSERT, rolesInsertColumnsFilterStr);
		addColumnGrants(grants, tableName, PrivilegeType.UPDATE, rolesUpdateColumnsFilterStr);
		
		if(model.getTables().contains(t)) {
			model.getTables().remove(t);
		}
		return model.getTables().add(t)?1:0;
	}
	
	void addGrants(List<Grant> grants, String owner, PrivilegeType pt, List<String> roles) {
		if(roles==null) { return; }
		for(String s: roles) {
			grants.add(new Grant(owner, pt, s));
		}
	}

	void addColumnGrants(List<Grant> grants, String owner, PrivilegeType pt, String columnRoles) {
		if(columnRoles==null) { return; }
		List<String> rolesCols = Utils.getStringList(columnRoles, "\\|");
		for(String s: rolesCols) {
			if(!SQL.valid(s)) { continue; }
			
			String[] sarr = s.split(":");
			if(sarr.length!=2) {
				log.warn("addColumnGrants: col syntax error: "+s);
				continue;
			}
			List<String> grantees = Utils.getStringList(sarr[1], ",");
			for(String grantee: grantees) {
				grants.add(new Grant(owner, sarr[0], pt, grantee, false));
			}
		}
	}
	
	@Override
	public boolean acceptsOutputWriter() {
		return true;
	}
	
	@Override
	public void setOutputWriter(Writer writer) {
		this.writer = writer;
	}

	@Override
	public void onInit() {
		process();
	}

	@Override
	public void onInsert(Relation qonRelation, RequestSpec reqspec) {
		if(!isQonTablesRelationUpdate(qonRelation)) { return; }
		//XXXxx: validate new relation? createQonTable() + addTable() already does it
		//XXXdone: read from DB? or reprocess RequestSpec? reprocessing ReqSpec...
		//boolean added = model.getTables().add((Table) qonRelation);
		boolean added = createQonTable(reqspec);
		log.info("onInsert: added "+qonRelation+"? "+added);
	}

	@Override
	public void onUpdate(Relation qonRelation, RequestSpec reqspec) {
		if(!isQonTablesRelationUpdate(qonRelation)) { return; }
		
		Table t = getQOnTableFromModel(reqspec);
		
		//XXXxx: validate updated relation? createQonTable + addTable already does it
		boolean removed = false;
		if(t==null) {
			log.warn("onUpdate: qon_table not found on model: "+reqspec.getParams()+" ; "+qonRelation); //+" ; uv: "+uv);
			//return;
		}
		else {
			removed = model.getTables().remove(t);
		}

		//merge t's info with requestspec info
		Map<String, String> uv = reqspec.getUpdateValues();
		if(reqspec.getParams().size()==1 && uv.get("NAME")==null) {
			uv.put("NAME", reqspec.getParams().get(0));
		}
		if(reqspec.getParams().size()==2) {
			if(uv.get("NAME")==null) {
				uv.put("NAME", reqspec.getParams().get(1));
			}
			if(uv.get("SCHEMA_NAME")==null) {
				uv.put("SCHEMA_NAME", reqspec.getParams().get(0));
			}
		}
		/*if(uv.get("SCHEMA_NAME")==null && t!=null) {
			uv.put("SCHEMA_NAME", t.getSchemaName());
		}
		if(uv.get("NAME")==null && t!=null) {
			uv.put("NAME", t.getName());
		}*/
			
		//boolean added = model.getTables().add((Table) qonRelation);
		boolean added = false;
		try {
			added = createQonTable(reqspec);
		}
		catch(BadRequestException e) {
			if(t!=null) {
				// "model rollback"
				model.getTables().add(t);
			}
			throw e;
		}
		log.info("onUpdate: removed "+t+"? "+removed+" ; added "+qonRelation+"? "+added);
	}

	@Override
	public void onDelete(Relation qonRelation, RequestSpec reqspec) {
		if(!isQonTablesRelationUpdate(qonRelation)) { return; }
		
		Table t = getQOnTableFromModel(reqspec);
		if(t==null) {
			//log.warn("onDelete: qon_table not found on model: "+reqspec.getParams()+" ; "+qonRelation);
			return;
		}
		boolean removed = model.getTables().remove(t);
		log.info("onDelete: removed "+t+"? "+removed);
	}
	
	boolean createQonTable(RequestSpec reqspec) {
		Map<String,String> v = reqspec.getUpdateValues();
		
		return addTable(v.get("SCHEMA_NAME"), v.get("NAME"), v.get("COLUMN_NAMES"), Utils.getStringList(v.get("PK_COLUMN_NAMES"), ","), Utils.getStringList(v.get("COLUMN_REMARKS"), PIPE_SPLIT),
				v.get("REMARKS"), Utils.getStringList(v.get("ROLES_SELECT"), PIPE_SPLIT), Utils.getStringList(v.get("ROLES_INSERT"), PIPE_SPLIT), Utils.getStringList(v.get("ROLES_UPDATE"), PIPE_SPLIT), Utils.getStringList(v.get("ROLES_DELETE"), PIPE_SPLIT),
				v.get("ROLES_INSERT_COLUMNS"), v.get("ROLES_UPDATE_COLUMNS"))>0;
	}
	
	boolean isQonTablesRelationUpdate(Relation relation) {
		String qonTablesTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_TABLES_TABLE);
		//log.info("isQonTablesRelation: "+qonTablesTable+" relation.getName(): "+relation.getName()+" ; relation.getSchemaName(): "+relation.getSchemaName()); 
		if( (! qonTablesTable.equalsIgnoreCase(relation.getName()))
			&& (! qonTablesTable.equalsIgnoreCase(relation.getSchemaName()+"."+relation.getName())) ) {
			//log.info("isQonTablesRelationUpdate: no qon_tables:: qonTablesTable: "+qonTablesTable+" relation.getName(): "+relation.getName()+" ; relation.getSchemaName(): "+relation.getSchemaName()); 
			return false;
		}
		return true;
	}
	
	Table getQOnTableFromModel(RequestSpec reqspec) {
		return (Table) DBIdentifiable.getDBIdentifiableByTypeAndName(model.getTables(), DBObjectType.TABLE, reqspec.getParams().get(0));
	}
	
	static boolean validColumnList(List<String> cols) {
		return cols!=null && cols.size()>0 && cols.get(0)!=null && !"".equals(cols.get(0));
	}
	
	/*Table getQOnTableFromModel(Relation relation, RequestSpec reqspec) {
		if(!isQonTablesRelationUpdate(relation)) {
			//log.info("getQOnTableFromModel: not qon_tables relation: "+relation); 
			return null;
		}
		
		Table dbid = (Table) DBIdentifiable.getDBIdentifiableByTypeAndName(model.getTables(), DBObjectType.TABLE, reqspec.getParams().get(0));
		if(dbid==null || !(dbid instanceof Table)) {
			log.warn("getQOnTableFromModel: dbid: "+dbid+" ; reqspec.getParams(): "+reqspec.getParams());
			return null;
		}
		
		return (Table) dbid;
	}*/
	
}
