package tbrugz.queryon.sqlcmd;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class ShowImportedKeys extends AbstractTableCommand {

	static final Pattern CMD  = Pattern.compile("\\s*\\$importedkeys(\\s+([\\w\\.%]+))?\\s*", Pattern.CASE_INSENSITIVE);
	
	@Override
	Pattern getCmdPattern() {
		return CMD;
	}
	
	@Override
	public ResultSet run(Connection conn) throws SQLException {
		return conn.getMetaData().getImportedKeys(null
				,schema!=null?schema:null
				,table!=null?table:null);
	}

}
