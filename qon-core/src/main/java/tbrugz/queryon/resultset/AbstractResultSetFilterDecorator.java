package tbrugz.queryon.resultset;

import java.sql.ResultSet;
import java.sql.SQLException;

import tbrugz.sqldump.resultset.AbstractResultSetDecorator;

public abstract class AbstractResultSetFilterDecorator extends AbstractResultSetDecorator {

	public AbstractResultSetFilterDecorator(ResultSet rs) {
		super(rs);
	}

	abstract boolean matchesValues() throws SQLException;
	
	@Override
	public boolean absolute(int row) throws SQLException {
		/*boolean ret = super.absolute(row);
		if(!matchesValues()) {
			return next();
		}
		return ret;*/
		boolean status = first();
		int countMatched = 0;
		while(countMatched < row) {
			status = next();
			countMatched++;
		}
		return status;
	}
	
	@Override
	public boolean next() throws SQLException {
		boolean hasNext = super.next();
		while(hasNext) {
			boolean matched = matchesValues();
			if(matched) {
				break;
			}
			hasNext = super.next();
		}
		return hasNext;
	}
	
}
