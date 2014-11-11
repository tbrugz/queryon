package tbrugz.queryon.sqlcmd;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tbrugz.queryon.SqlCommand;

public class ShowColumns implements SqlCommand {

	static final Pattern CMD = Pattern.compile("\\s*\\$columns(\\s+([\\w\\.]+))?\\s*", Pattern.CASE_INSENSITIVE);
	
	String name;
	
	public boolean matches(String sql) {
		Matcher m = CMD.matcher(sql);
		if(m.matches()) {
			name = m.group(2);
			return true;
		}
		return false;
	}

	public ResultSet run(Connection conn) throws SQLException {
		return conn.getMetaData().getColumns(null, null, name!=null?name.toUpperCase():null, null);
	}

}