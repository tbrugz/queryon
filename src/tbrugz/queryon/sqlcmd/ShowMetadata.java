package tbrugz.queryon.sqlcmd;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.SqlCommand;
import tbrugz.queryon.resultset.ResultSetObjectAdaptor;

public class ShowMetadata implements SqlCommand {
	//	storesLowerCaseIdentifiers()

	static final Log log = LogFactory.getLog(ShowMetadata.class);
	
	static final Pattern CMD  = Pattern.compile("\\s*\\$metadata(\\s+([\\w\\.%]+))?\\s*", Pattern.CASE_INSENSITIVE);
	
	String methodName;
	Method method;
	Throwable throwable;
	
	void init() {
		methodName = null;
		method = null;
		throwable = null;
	}
	
	public boolean matches(String sql) {
		Matcher m = CMD.matcher(sql);
		if(m.matches()) {
			init();
			String nameGroup = m.group(2);
			if(nameGroup==null) { return true; }
			
			String[] nameParts = nameGroup.split("\\.");
			switch(nameParts.length) {
			case 1:
				methodName = nameParts[0];
				try {
					method = DatabaseMetaData.class.getMethod(methodName);
				} catch (Exception e) {
					throwable = e;
					log.warn("exception: "+e);
				}
				break;
			default:
				return false;
			}
			//schema = m.group(2);
			return true;
		}
		return false;
	}
	
	public ResultSet run(Connection conn) throws SQLException {
		if(throwable!=null) {
			throw new SQLException("invoke exception: "+throwable);
		}
		
		try {
			Object o = method.invoke(conn.getMetaData());
			if(o instanceof ResultSet) {
				return (ResultSet) o;
			}
			//List<Object> l = new ArrayList<Object>();
			//l.add(o);
			return new ResultSetObjectAdaptor(methodName, o);
		} catch (Exception e) {
			//log.warn();
			throw new SQLException("invoke exception: "+e, e);
		}
	}
}
