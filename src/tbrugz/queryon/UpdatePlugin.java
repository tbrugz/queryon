package tbrugz.queryon;

import java.sql.Connection;
import java.util.Properties;

import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;

public interface UpdatePlugin {

	public void setProperties(Properties prop); // equals to AbstractProcessor's
	
	public void setSchemaModel(SchemaModel schemamodel); // equals to AbstractSchemaProcessor's
	
	public void setConnection(Connection conn); // equals to AbstractSQLProc's
	
	public void onInit();
	
	public void onInsert(Relation relation, RequestSpec reqspec);
	
	public void onUpdate(Relation relation, RequestSpec reqspec);
	
	public void onDelete(Relation relation, RequestSpec reqspec);
	
}
