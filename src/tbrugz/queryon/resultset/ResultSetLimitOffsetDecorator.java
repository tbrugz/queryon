package tbrugz.queryon.resultset;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ResultSetLimitOffsetDecorator extends AbstractResultSetDecorator {

	static final Log log = LogFactory.getLog(ResultSetLimitOffsetDecorator.class);
	static final int FORWARD_ONLY_FETCH_MAX = 100; //XXX add property for FORWARD_ONLY_FETCH_MAX ?
	
	final int limit;
	final int offset;
	
	public ResultSetLimitOffsetDecorator(ResultSet rs, int limit, int offset) throws SQLException {
		super(rs);
		this.limit = limit;
		this.offset = offset;
		
		int resultSetType = rs.getType();
		if(offset>0) {
			if(resultSetType!=ResultSet.TYPE_FORWARD_ONLY) {
				rs.absolute(offset); //XXX: should be relative() ?
			}
			else if(offset>FORWARD_ONLY_FETCH_MAX) {
				String message = "cant offset: ResultSet type is FORWARD_ONLY & offset ["+offset+"] is greater than FORWARD_ONLY_FETCH_MAX ["+FORWARD_ONLY_FETCH_MAX+"]";
				log.warn(message);
				throw new SQLException(message);
			}
			else {
				log.warn("ResultSet type is FORWARD_ONLY, "+offset+" rows will be read without being used");
				//rs.setFetchSize(offset+2);
				// XXX: do rs.next() times 'reqspec.offset'? will fetch all: not really optimized
				for(int i=0; i<offset; i++) {
					rs.next();
				}
			}
		}
	}
	
	int nextCount = 0;
	
	@Override
	public boolean next() throws SQLException {
		nextCount++;
		if(limit>0 && limit<nextCount) {
			return false;
		}
		return super.next();
	}

}
