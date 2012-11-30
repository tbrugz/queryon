package tbrugz.queryon.resultset;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ResultSetLimitOffsetDecorator extends AbstractResultSetDecorator {

	static final Log log = LogFactory.getLog(ResultSetLimitOffsetDecorator.class);
	
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
			else {
				log.warn("cant offset: ResultSet type is FORWARD_ONLY");
				// XXX: do rs.next() times 'reqspec.offset'? will fetch all: not really optimized
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
