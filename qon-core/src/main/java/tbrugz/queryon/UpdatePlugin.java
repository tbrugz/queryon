package tbrugz.queryon;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;

import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;

public interface UpdatePlugin {

	public void setModelId(String modelId);
	
	public void setProperties(Properties prop); // equals to AbstractProcessor's
	
	public void setSchemaModel(SchemaModel schemamodel); // equals to AbstractSchemaProcessor's
	
	public void setConnection(Connection conn); // equals to AbstractSQLProc's
	
	public void onInit(ServletContext context); // XXX throws SQLException ?
	
	public boolean accepts(Relation relation);
	
	/**
	 * Inserts relation on model
	 */
	public void onInsert(Relation relation, RequestSpec reqspec);

	/**
	 * Updates relation on model
	 */
	public void onUpdate(Relation relation, RequestSpec reqspec);
	
	/**
	 * Deletes relation from model
	 */
	public void onDelete(Relation relation, RequestSpec reqspec);

	/**
	 * Executes "direct" plugin action
	 */
	public void executePluginAction(RequestSpec reqspec, HttpServletResponse resp)
		throws IOException, SQLException;

}
