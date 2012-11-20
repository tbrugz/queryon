package tbrugz.queryon;

import java.sql.Types;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DBUtil {
	static final Log log = LogFactory.getLog(DBUtil.class);
	
	//XXX: getSQLTypeForColumnType(String colType): add dbid as parameter? 
	public static int getSQLTypeForColumnType(String colType) {
		if(colType==null) {	return Types.VARCHAR; }
		
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

}
