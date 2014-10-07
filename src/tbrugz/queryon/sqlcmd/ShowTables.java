package tbrugz.queryon.sqlcmd;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tbrugz.queryon.SqlCommand;

public class ShowTables implements SqlCommand {

	static final Pattern CMD_DESCRIBE_TABLES  = Pattern.compile("\\s*desc\\s*", Pattern.CASE_INSENSITIVE);
	
	public boolean matches(String sql) {
		Matcher m = CMD_DESCRIBE_TABLES.matcher(sql);
		return m.matches();
	}

	public ResultSet run(Connection conn) throws SQLException {
		return conn.getMetaData().getTables(null, null, null, null);
	}

}
