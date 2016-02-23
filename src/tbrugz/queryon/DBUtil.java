package tbrugz.queryon;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Properties;

import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.util.QOnModelUtils;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.util.ConnectionUtil;

public class DBUtil {
	static final Log log = LogFactory.getLog(DBUtil.class);
	
	//XXX: getSQLTypeForColumnType(String colType): add dbid as parameter? 
	public static int getSQLTypeForColumnType(String colType) {
		if(colType==null) { return Types.VARCHAR; }
		
		if(colType.equalsIgnoreCase("BINARY_INTEGER")) {
			return Types.INTEGER;
		}
		if(colType.equalsIgnoreCase("BLOB")) {
			return Types.BLOB;
		}
		if(colType.equalsIgnoreCase("CLOB")) {
			return Types.CLOB;
		}
		if(colType.equalsIgnoreCase("DATE")) {
			return Types.DATE;
		}
		if(colType.equalsIgnoreCase("NUMBER")) {
			return Types.NUMERIC;
		}
		if(colType.equalsIgnoreCase("OBJECT")) {
			return Types.OTHER;
		}
		if(colType.equalsIgnoreCase("RAW")) {
			return Types.BINARY;
		}
		if(colType.equalsIgnoreCase("REF CURSOR")) {
			//log.info("OracleTypes.CURSOR: "+OracleTypes.CURSOR);
			return -10; //XXX: OracleTypes.CURSOR;
		}
		if(colType.equalsIgnoreCase("ROWID")) {
			return Types.ROWID;
		}
		if(colType.equalsIgnoreCase("VARCHAR")) {
			return Types.VARCHAR;
		}
		if(colType.equalsIgnoreCase("VARCHAR2")) {
			return Types.VARCHAR;
		}
		log.info("unknown sql type for column type: "+colType);
		return Types.OTHER;
	}
	
	public static String getDBConnPrefix(Properties prop, String modelId) {
		String prefix = QueryOn.CONN_PROPS_PREFIX+(modelId!=null?"."+modelId:"");
		String ret = prop.getProperty(prefix+".connpropprefix", prefix);
		//log.info("getDBConnPrefix: modelId = "+modelId+" ; ret = "+ret);
		return ret;
	}
	
	public static Connection initDBConn(Properties prop, String modelId) throws ClassNotFoundException, SQLException, NamingException {
		//String prefix = QueryOn.CONN_PROPS_PREFIX+(modelId!=null?"."+modelId:"");
		//prefix = prop.getProperty(prefix+".connpropprefix", prefix);
		boolean autocommit = false;
		log.debug("initDBConn: modelId = "+modelId+" ; prefix = "+getDBConnPrefix(prop, modelId)+" ; autocommit = "+autocommit);
		Connection conn = ConnectionUtil.initDBConnection(getDBConnPrefix(prop, modelId), prop, autocommit);
		initStatics(conn);
		return conn;
	}

	public static Connection initDBConn(Properties prop, String modelId, SchemaModel model) throws ClassNotFoundException, SQLException, NamingException {
		try {
			Connection conn = initDBConn(prop, modelId);
			QOnModelUtils.setModelMetadata(model, conn);
			return conn;
		}
		catch(ClassNotFoundException e) {
			QOnModelUtils.setModelExceptionMetadata(model, e);
			throw e;
		}
		catch(SQLException e) {
			QOnModelUtils.setModelExceptionMetadata(model, e);
			throw e;
		}
		catch(NamingException e) {
			QOnModelUtils.setModelExceptionMetadata(model, e);
			throw e;
		}
	}
	
	public static boolean doRollback(Connection conn) {
		boolean auto = false;
		try {
			auto = conn.getAutoCommit();
			conn.rollback();
		} catch (SQLException sqle) {
			log.warn("Error in rollback [autocommit="+auto+"]: "+sqle.getMessage(), sqle);
			return false;
		}
		return true;
	}
	
	static boolean staticsInited = false;
	
	protected static void initStatics(Connection conn) throws SQLException {
		if(!staticsInited) {
			DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(conn.getMetaData());
			String quote = feat.getIdentifierQuoteString();
			log.debug("quote:: "+quote);
			//SQL.sqlIdDecorator = new StringDecorator.StringQuoterDecorator(quote);
			SQL.setDBMSFeatures(feat);
			staticsInited = true;
		}
	}
	
}
