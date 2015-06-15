package tbrugz.queryon.sqlcmd;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class ShowExportedKeys extends AbstractTableCommand {

	static final Pattern CMD  = Pattern.compile("\\s*\\$exportedkeys(\\s+([\\w\\.%]+))?\\s*", Pattern.CASE_INSENSITIVE);
	
	@Override
	Pattern getCmdPattern() {
		return CMD;
	}
	
	@Override
	public ResultSet run(Connection conn) throws SQLException {
		return conn.getMetaData().getExportedKeys(null
				,schema!=null?schema.toUpperCase():null
				,table!=null?table.toUpperCase():null);
	}

}
