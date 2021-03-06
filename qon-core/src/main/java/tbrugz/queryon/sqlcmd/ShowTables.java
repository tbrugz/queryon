package tbrugz.queryon.sqlcmd;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tbrugz.queryon.SqlCommand;

public class ShowTables implements SqlCommand {

	static final Pattern CMD  = Pattern.compile("\\s*\\$tables(\\s+([\\w\\.%]+))?\\s*", Pattern.CASE_INSENSITIVE);
	
	String schema;
	String table;
	
	void init() {
		schema = null; table = null;
	}
	
	public boolean matches(String sql) {
		Matcher m = CMD.matcher(sql);
		if(m.matches()) {
			init();
			String nameGroup = m.group(2);
			if(nameGroup==null) { return true; }
			
			String[] nameParts = nameGroup.split("\\.");
			switch(nameParts.length) {
			case 2:
				table = nameParts[1];
			case 1:
				schema = nameParts[0];
				break;
			default:
				return false;
			}
			//schema = m.group(2);
			return true;
		}
		return false;
	}

	public ResultSet run(Connection conn) throws SQLException {
		return conn.getMetaData().getTables(null
				,schema!=null?schema:null
				,table!=null?table:null
				,null);
	}

}
