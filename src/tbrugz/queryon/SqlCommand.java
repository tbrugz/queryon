package tbrugz.queryon;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface SqlCommand {
	
	boolean matches(String sql);

	ResultSet run(Connection conn) throws SQLException;
	
	//XXX: use DatabaseMetaData as param?
	//ResultSet run(DatabaseMetaData dbmd) throws SQLException;
	
}
