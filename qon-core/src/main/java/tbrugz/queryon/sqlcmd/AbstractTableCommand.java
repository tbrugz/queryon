package tbrugz.queryon.sqlcmd;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tbrugz.queryon.SqlCommand;

/*
 * sqlcmd: see
 *   https://github.com/julianhyde/sqlline/blob/master/doc/manual.xml
 *   https://github.com/julianhyde/sqlline ; https://github.com/mapr/sqlline ; beeline: https://cwiki.apache.org/confluence/display/Hive/HiveServer2+Clients
 * h2: http://www.h2database.com/html/tutorial.html#console_syntax ; http://www.h2database.com/html/tutorial.html#shell_tool
 */
public abstract class AbstractTableCommand implements SqlCommand {

	//static final Pattern CMD  = Pattern.compile ...
	
	String schema;
	String table;
	
	void init() {
		schema = null; table = null;
	}
	
	abstract Pattern getCmdPattern();
	
	public boolean matches(String sql) {
		Matcher m = getCmdPattern().matcher(sql);
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
			return true;
		}
		return false;
	}

	public abstract ResultSet run(Connection conn) throws SQLException;

}
