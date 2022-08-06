package tbrugz.queryon.processor;

import java.io.IOException;
import java.util.Properties;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.UpdatePlugin;
import tbrugz.sqldump.def.AbstractSQLProc;
import tbrugz.sqldump.util.Utils;

public abstract class AbstractUpdatePlugin extends AbstractSQLProc implements UpdatePlugin {

	static final Log log = LogFactory.getLog(AbstractUpdatePlugin.class);
	
	public static final String DEFAULT_SCHEMA_NAME = "queryon";

	String modelId;
	
	static final String COMMA_SPLIT = ",";
	static final String PIPE_SPLIT = "\\|";

	public static final String SUFFIX_TABLE = ".table";
	public static final String SUFFIX_SCHEMA_NAME = ".schema-name";

	@Override
	public void setModelId(String modelId) {
		this.modelId = modelId;
	}
	
	protected String getProperty(String prefix, String suffix, String defaultValue) {
		/*
		String ret = prop.getProperty(prefix+"@"+modelId+suffix);
		if(ret!=null) { return ret; }
		return prop.getProperty(prefix+suffix, defaultValue);
		*/
		String ret = getProperty(prop, modelId, prefix, suffix, defaultValue);
		//log.info("getProperty(): "+ret+" ;; modelId = "+modelId+" / prefix = "+prefix+" / suffix = "+suffix);
		return ret;
	}

	public static String getProperty(Properties prop, String modelId, String prefix, String suffix, String defaultValue) {
		String ret = prop.getProperty(prefix+"@"+modelId+suffix);
		if(ret!=null) { return ret; }
		return prop.getProperty(prefix+suffix, defaultValue);
	}

	public static void updateProperty(Properties prop, String modelId, String prefix, String suffix, String value) {
		String key = prefix+"@"+modelId+suffix;
		String ret = prop.getProperty(key);
		if(ret==null) {
			key = prefix+suffix;
			ret = prop.getProperty(key);
		}
		if(ret==null) {
			String message = "key not found: "+key;
			log.warn(message);
			return;
		}
		prop.setProperty(key, value);
	}

	/**
	 * Executes "direct" plugin action
	 */
	public void executePluginAction(RequestSpec reqspec, HttpServletResponse resp) throws IOException, SQLException {
		throw new UnsupportedOperationException(Utils.join(reqspec.getParams(), "/"));
	}

	/*
	public String getSchemaName(final String propPrefix, final String defaultSchemaName) {
		return getProperty(propPrefix, SUFFIX_SCHEMA_NAME, defaultSchemaName);
	}
	*/

	public String getTableName(final String propPrefix, final String defaultTableName) {
		return getTableName(propPrefix, defaultTableName, false);
	}

	/*
	public String getQualifiedTableName(final String propPrefix, final String defaultTableName) {
		return getTableName(propPrefix, defaultTableName, true);
	}
	*/

	protected String getTableName(final String propPrefix, final String defaultTableName, boolean grabSchemaName) {
		String tableName = getProperty(propPrefix, SUFFIX_TABLE, defaultTableName);
		if(grabSchemaName) {
			String schemaName = getProperty(propPrefix, SUFFIX_SCHEMA_NAME, null);
			if(schemaName!=null && !schemaName.equals("")) {
				return schemaName + "." + tableName;
			}
		}
		return tableName;
	}

	public void updateTableNameProperty(final String propPrefix, DatabaseMetaData dbmd) throws SQLException {
		String tableName = getProperty(propPrefix, SUFFIX_TABLE, null);
		if(tableName==null) {
			log.debug("updateTableNameProperty: property not found: prefix="+propPrefix+" ; suffix="+SUFFIX_TABLE);
			return;
		}
		if(dbmd.storesLowerCaseIdentifiers()) {
			tableName = tableName.toLowerCase();
			updateProperty(prop, modelId, propPrefix, SUFFIX_TABLE, tableName);
			log.debug("updateTableNameProperty: updated tableName to '"+tableName+"' (lower) [prefix="+propPrefix+" ; suffix="+SUFFIX_TABLE+"]");
		}
		else if(dbmd.storesUpperCaseIdentifiers()) {
			tableName = tableName.toUpperCase();
			updateProperty(prop, modelId, propPrefix, SUFFIX_TABLE, tableName);
			log.debug("updateTableNameProperty: updated tableName to '"+tableName+"' (upper) [prefix="+propPrefix+" ; suffix="+SUFFIX_TABLE+"]");
		}
	}

	/*
	public boolean supportsSchemasInDataManipulation(Connection conn) {
		try {
			boolean ret = conn.getMetaData().supportsSchemasInDataManipulation();
			//log.debug("supportsSchemasInDataManipulation: "+ret);
			return ret;
		}
		catch(SQLException e) {
			log.warn("SQLException: "+e, e);
			return true;
		}
	}
	*/

}
