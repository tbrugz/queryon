package tbrugz.queryon.resultset;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

//import tbrugz.sqldump.util.SQLUtils;

public class ResultSetPermissionFilterDecorator extends AbstractResultSetFilterDecorator {
	
	static final Log log = LogFactory.getLog(ResultSetPermissionFilterDecorator.class);

	final Subject subject;
	final String permissionPattern;
	final int maxCol;
	
	public ResultSetPermissionFilterDecorator(ResultSet rs, Subject subject, String permissionPattern) throws SQLException {
		super(rs);
		this.subject = subject;
		this.permissionPattern = permissionPattern;
		maxCol = rs.getMetaData().getColumnCount();
		//log.info("permissionPattern: "+permissionPattern+" ; columns [#"+maxCol+"]: "+SQLUtils.getColumnNames(rs.getMetaData()));
		//XXX: pre-compile pattern?
	}

	@Override
	boolean matchesValues() throws SQLException {
		String permission = permissionPattern;
		for(int i=1;i<maxCol;i++) {
			String colValue = rs.getString(i);
			if(colValue!=null) {
				colValue = Matcher.quoteReplacement(colValue);
			}
			else {
				colValue = "";
			}
			permission = permission.replaceAll("\\["+i+"\\]", colValue);
		}
		boolean permitted = subject.isPermitted(permission);
		//log.debug("RSPFD: permission: "+permissionPattern+" / "+permission+" / "+permitted);
		//if(!permitted) {
		//	log.info("RSPFD: not permitted: "+permissionPattern+" / "+permission);
		//}
		return permitted;
	}
	
}
