package tbrugz.queryon.sqlcmd;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tbrugz.queryon.SqlCommand;

public class ShowSchemas implements SqlCommand {

	static final Pattern CMD  = Pattern.compile("\\s*\\$schemas\\s*", Pattern.CASE_INSENSITIVE);
	
	public boolean matches(String sql) {
		Matcher m = CMD.matcher(sql);
		return m.matches();
	}

	public ResultSet run(Connection conn) throws SQLException {
		return conn.getMetaData().getSchemas();
	}

}
