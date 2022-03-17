package tbrugz.queryon.processor;

import static tbrugz.queryon.util.MiscUtils.getLowerAlso;

import java.io.IOException;
import java.io.Writer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.SQL;
import tbrugz.queryon.UpdatePlugin;
import tbrugz.queryon.model.QonTable;
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
import tbrugz.sqldump.util.StringDecorator;
import tbrugz.sqldump.util.Utils;

public class QOnTables extends AbstractUpdatePlugin implements UpdatePlugin {

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

	public static final String ATTR_TABLES_WARNINGS_PREFIX = "qon-tables-warnings";
	
	static final String PROP_PREFIX = "queryon.qon-tables";
	
	static final String SUFFIX_ACTION = ".action";
	static final String SUFFIX_TABLE_NAMES = ".names";

	static final String ACTION_READ = "read";
	
	//static final String PIPE_SPLIT = "\\|";
	
	public static final String DEFAULT_TABLES_TABLE = "qon_tables";
	
	public static StringDecorator sqlStringValuesDecorator = new StringQuoterEscaperDecorator("'");

	Writer writer;
	
	@Override
	public void process() {
		throw new RuntimeException("process() should not be called");
	}
	
	public void process(ServletContext context) {
		try {
			String action = prop.getProperty(PROP_PREFIX+SUFFIX_ACTION, ACTION_READ);
			if(ACTION_READ.equals(action)) {
				int count = readFromDatabase(context);
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
	
	int readFromDatabase(ServletContext context) throws SQLException {
		//String qonTablesTable = getProperty(PROP_PREFIX, SUFFIX_TABLE, DEFAULT_TABLES_TABLE);
		String qonTablesTable = getTableName(PROP_PREFIX, DEFAULT_TABLES_TABLE);
		String qonTablesNames = getProperty(PROP_PREFIX, SUFFIX_TABLE_NAMES, null);
		List<String> tables = Utils.getStringList(qonTablesNames, ",");
		String sql = "select schema_name, name, column_names, pk_column_names, default_column_names"
				+ ", column_remarks, remarks"
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
		
		clearWarnings(context, model.getModelId());

		int count = 0;
		while(rs.next()) {
			String schema = rs.getString(1);
			String tableName = rs.getString(2);
			String columnNames = rs.getString(3);
			String pkColumnNamesStr = rs.getString(4);
			String defaultColumnNamesStr = rs.getString(5);
			String columnRemarksStr = rs.getString(6);
			String remarks = rs.getString(7);
			String rolesSelectFilterStr = rs.getString(8);
			String rolesInsertFilterStr = rs.getString(9);
			String rolesUpdateFilterStr = rs.getString(10);
			String rolesDeleteFilterStr = rs.getString(11);
			String rolesInsertColumnsFilterStr = rs.getString(12);
			String rolesUpdateColumnsFilterStr = rs.getString(13);
			//XXX default_select_filter
			//XXXdone default_select_projection: column_names
			
			List<String> pkColumnNames = Utils.getStringList(pkColumnNamesStr, COMMA_SPLIT);
			List<String> defaultColumnNames = Utils.getStringList(defaultColumnNamesStr, COMMA_SPLIT);
			List<String> columnRemarks = Utils.getStringList(columnRemarksStr, PIPE_SPLIT);
			
			try {
				List<String> rolesSelect = Utils.getStringList(rolesSelectFilterStr, PIPE_SPLIT);
				List<String> rolesInsert = Utils.getStringList(rolesInsertFilterStr, PIPE_SPLIT);
				List<String> rolesUpdate = Utils.getStringList(rolesUpdateFilterStr, PIPE_SPLIT);
				List<String> rolesDelete = Utils.getStringList(rolesDeleteFilterStr, PIPE_SPLIT);
				
				Table t = addTable(schema, tableName, columnNames, pkColumnNames, defaultColumnNames,
						columnRemarks, remarks,
						rolesSelect, rolesInsert, rolesUpdate, rolesDelete,
						rolesInsertColumnsFilterStr, rolesUpdateColumnsFilterStr);
				if(t!=null) { count++; }
			}
			catch(RuntimeException e) {
				String message = "error reading table '"+tableName+"': "+e;
				log.warn(message);
				putWarning(context, model.getModelId(), schema, tableName, message);
			}
		}
		
		log.info("QOnTables processed ["+
				(model.getModelId()!=null?"model="+model.getModelId()+"; ":"")+
				"added/replaced "+count+" tables]");
		return count;
	}
	
	void clearWarnings(ServletContext context, String modelId) {
		String warnKey = ATTR_TABLES_WARNINGS_PREFIX+"."+modelId;
		Map<String, String> warnings = new LinkedHashMap<String, String>();
		context.setAttribute(warnKey, warnings);
	}
	
	@SuppressWarnings("unchecked")
	void putWarning(ServletContext context, String modelId, String schemaName, String name, String warning) {
		String warnKey = ATTR_TABLES_WARNINGS_PREFIX+"."+modelId;
		Map<String, String> warnings = (Map<String, String>) context.getAttribute(warnKey);
		warnings.put((schemaName!=null?schemaName+".":"") + name, warning);
	}

	private Table addTable(String schema, String tableName, String columnNames, List<String> pkColumnNames, 
			List<String> defaultColumnNames, List<String> columnRemarks,
			String remarks, List<String> rolesSelect, List<String> rolesInsert, List<String> rolesUpdate, List<String> rolesDelete,
			String rolesInsertColumnsFilterStr, String rolesUpdateColumnsFilterStr) {
		QonTable t = new QonTable();
		t.setSchemaName(schema);
		t.setName(tableName);
		t.setRemarks(remarks);
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
			log.warn(message+"\nsql: "+sql);
			log.debug(message+"\nsql: "+sql, e);
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
		
		if(validColumnList(defaultColumnNames)) {
			t.setDefaultColumnNames(defaultColumnNames);
		}
		else {
			log.warn("invalid defaultColumnNames: "+defaultColumnNames);
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
		boolean added = model.getTables().add(t);
		if(added) {
			return t;
		}
		return null;
	}
	
	void addGrants(List<Grant> grants, String owner, PrivilegeType pt, List<String> roles) {
		if(roles==null) { return; }
		for(String s: roles) {
			grants.add(new Grant(owner, pt, s));
		}
	}

	void addColumnGrants(List<Grant> grants, String owner, PrivilegeType pt, String columnRoles) {
		if(columnRoles==null) { return; }
		List<String> rolesCols = Utils.getStringList(columnRoles, PIPE_SPLIT);
		for(String s: rolesCols) {
			if(!SQL.valid(s)) { continue; }
			
			String[] sarr = s.split(":");
			if(sarr.length!=2) {
				log.warn("addColumnGrants: col syntax error: "+s);
				continue;
			}
			List<String> grantees = Utils.getStringList(sarr[1], COMMA_SPLIT);
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
	public void onInit(ServletContext context) {
		process(context);
	}

	@Override
	public void onInsert(Relation qonRelation, RequestSpec reqspec) {
		if(!isQonTablesRelationUpdate(qonRelation)) { return; }
		//XXXxx: validate new relation? createQonTable() + addTable() already does it
		//XXXdone: read from DB? or reprocess RequestSpec? reprocessing ReqSpec...
		//boolean added = model.getTables().add((Table) qonRelation);
		Table t = createQonTable(reqspec);
		boolean added = t!=null;
		log.info("onInsert: added "+qonRelation+"? "+added+" [table="+t+"]");
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
		/*Map<String, String> uv = reqspec.getUpdateValues();
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
		}*/
		/*if(uv.get("SCHEMA_NAME")==null && t!=null) {
			uv.put("SCHEMA_NAME", t.getSchemaName());
		}
		if(uv.get("NAME")==null && t!=null) {
			uv.put("NAME", t.getName());
		}*/
			
		//boolean added = model.getTables().add((Table) qonRelation);
		boolean added = false;
		Table tnew = null;
		try {
			tnew = createQonTable(reqspec);
			added = tnew!=null;
		}
		catch(BadRequestException e) {
			if(t!=null) {
				// "model rollback"
				model.getTables().add(t);
			}
			throw e;
		}
		log.info("onUpdate: removed "+t+"? "+removed+" ; added "+tnew+"? "+added+" [qonRelation="+qonRelation+";newT.remarks="+tnew.getRemarks()+"]");
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
	
	Table createQonTable(RequestSpec reqspec) {
		Map<String, String> v = reqspec.getUpdateValues();
		
		return addTable(getLowerAlso(v, "SCHEMA_NAME"), getLowerAlso(v, "NAME"), getLowerAlso(v, "COLUMN_NAMES"),
				Utils.getStringList(getLowerAlso(v, "PK_COLUMN_NAMES"), COMMA_SPLIT),
				Utils.getStringList(getLowerAlso(v, "DEFAULT_COLUMN_NAMES"), COMMA_SPLIT),
				Utils.getStringList(getLowerAlso(v, "COLUMN_REMARKS"), PIPE_SPLIT),
				getLowerAlso(v, "REMARKS"),
				Utils.getStringList(getLowerAlso(v, "ROLES_SELECT"), PIPE_SPLIT),
				Utils.getStringList(getLowerAlso(v, "ROLES_INSERT"), PIPE_SPLIT),
				Utils.getStringList(getLowerAlso(v, "ROLES_UPDATE"), PIPE_SPLIT),
				Utils.getStringList(getLowerAlso(v, "ROLES_DELETE"), PIPE_SPLIT),
				getLowerAlso(v, "ROLES_INSERT_COLUMNS"), getLowerAlso(v, "ROLES_UPDATE_COLUMNS"));
	}
	
	boolean isQonTablesRelationUpdate(Relation relation) {
		String qonTablesTable = getProperty(PROP_PREFIX, SUFFIX_TABLE, DEFAULT_TABLES_TABLE);
		//log.info("isQonTablesRelation: "+qonTablesTable+" relation.getName(): "+relation.getName()+" ; relation.getSchemaName(): "+relation.getSchemaName()); 
		if( (! qonTablesTable.equalsIgnoreCase(relation.getName()))
			&& (! qonTablesTable.equalsIgnoreCase(relation.getSchemaName()+"."+relation.getName())) ) {
			//log.info("isQonTablesRelationUpdate: no qon_tables:: qonTablesTable: "+qonTablesTable+" relation.getName(): "+relation.getName()+" ; relation.getSchemaName(): "+relation.getSchemaName()); 
			return false;
		}
		return true;
	}
	
	Table getQOnTableFromModel(RequestSpec reqspec) {
		return (Table) DBIdentifiable.getDBIdentifiableByTypeAndName(model.getTables(), DBObjectType.TABLE, String.valueOf( reqspec.getParams().get(0) ));
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
	
	@Override
	public boolean accepts(Relation relation) {
		return isQonTablesRelationUpdate(relation);
	}
	
}
