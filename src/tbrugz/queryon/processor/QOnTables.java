package tbrugz.queryon.processor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.SQL;
import tbrugz.sqldump.JDBCSchemaGrabber;
import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.Constraint.ConstraintType;
import tbrugz.sqldump.def.AbstractSQLProc;
import tbrugz.sqldump.util.StringDecorator;
import tbrugz.sqldump.util.Utils;

public class QOnTables extends AbstractSQLProc {

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
	
	static final String DEFAULT_TABLES_TABLE = "qon_tables";
	
	public static StringDecorator sqlStringValuesDecorator = new StringQuoterEscaperDecorator("'");
	
	public void process() {
		try {
			String action = prop.getProperty(PROP_PREFIX+SUFFIX_ACTION, ACTION_READ);
			if(ACTION_READ.equals(action)) {
				readFromDatabase();
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw new BadRequestException("SQL exception: "+e);
		}
	}
	
	void readFromDatabase() throws SQLException {
		String qonTablesTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_TABLES_TABLE);
		List<String> tables = Utils.getStringListFromProp(prop, PROP_PREFIX+SUFFIX_TABLE_NAMES, ",");
		String sql = "select schema, name, column_names, pk_column_names, remarks"
				+", roles_select, roles_insert, roles_update, roles_delete"
				+", roles_insert_columns, roles_update_columns"
				+" from "+qonTablesTable
				+(tables!=null?" where name in ("+Utils.join(tables, ",", sqlStringValuesDecorator)+")":"");
		
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
			String remarks = rs.getString(5);
			String rolesSelectFilterStr = rs.getString(6);
			String rolesInsertFilterStr = rs.getString(7);
			String rolesUpdateFilterStr = rs.getString(8);
			String rolesDeleteFilterStr = rs.getString(9);
			String rolesInsertColumnsFilterStr = rs.getString(10);
			String rolesUpdateColumnsFilterStr = rs.getString(11);
			//XXX default_select_filter
			//XXXdone default_select_projection: column_names
			
			List<String> pkColumnNames = Utils.getStringList(pkColumnNamesStr, ",");
			
			try {
				String split = "\\|";
				List<String> rolesSelect = Utils.getStringList(rolesSelectFilterStr, split);
				List<String> rolesInsert = Utils.getStringList(rolesInsertFilterStr, split);
				List<String> rolesUpdate = Utils.getStringList(rolesUpdateFilterStr, split);
				List<String> rolesDelete = Utils.getStringList(rolesDeleteFilterStr, split);
				
				//List<String> rolesInsertCols = Utils.getStringList(rolesInsertColumnsFilterStr, "|");
				//List<String> rolesUpdate = Utils.getStringList(rolesUpdateColumnsFilterStr, "|");
				
				count += addTable(schema, tableName, columnNames, pkColumnNames, remarks,
						rolesSelect, rolesInsert, rolesUpdate, rolesDelete,
						rolesInsertColumnsFilterStr, rolesUpdateColumnsFilterStr);
			}
			catch(SQLException e) {
				log.warn("error reading table '"+tableName+"': "+e);
			}
		}
		
		log.info("QOnTables processed [added/replaced "+count+" tables]");
	}

	private int addTable(String schema, String tableName, String columnNames, List<String> pkColumnNames, String remarks,
			List<String> rolesSelect, List<String> rolesInsert, List<String> rolesUpdate, List<String> rolesDelete,
			String rolesInsertColumnsFilterStr, String rolesUpdateColumnsFilterStr) throws SQLException {
		Table t = new Table();
		t.setSchemaName(schema);
		t.setName(tableName);
		//XXX table type: what if it is a view? t.setType(TableType.VIEW); ?
		//XXXdone option to add primary key (for tables/views that doesn't have them, or to setup a different PK for a table)

		if(columnNames==null) {
			columnNames = "*";
		}
		PreparedStatement stmt = conn.prepareStatement("select "+columnNames+" from "
				+ (schema!=null?SQL.sqlIdDecorator.get(schema)+".":"")
				+ (SQL.sqlIdDecorator.get(tableName))
				);
		ResultSetMetaData rsmd = stmt.getMetaData();
		t.setColumns(DataDumpUtils.getColumns(rsmd));
		
		if(pkColumnNames!=null) {
			Constraint pk = new Constraint();
			pk.setType(ConstraintType.PK);
			pk.setUniqueColumns(pkColumnNames);
			t.getConstraints().add(pk);
		}
		else {
			List<Constraint> pks = JDBCSchemaGrabber.grabRelationPKs(conn.getMetaData(), t);
			t.getConstraints().addAll(pks);
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
	
}
