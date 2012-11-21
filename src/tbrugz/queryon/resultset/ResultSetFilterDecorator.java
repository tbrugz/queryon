package tbrugz.queryon.resultset;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ResultSetFilterDecorator extends AbstractResultSetDecorator {

	final List<Integer> colPositions;
	final List<String> colValues;
	final int columnsToMatch;
	
	public ResultSetFilterDecorator(ResultSet rs, List<Integer> colPositions, List<String> colValues) {
		super(rs);
		this.colPositions = colPositions;
		this.colValues = colValues;
		//matches the shorter
		columnsToMatch = this.colPositions.size()<this.colValues.size() ? this.colPositions.size() : this.colValues.size(); 
	}
	
	@Override
	public boolean next() throws SQLException {
		boolean hasNext = super.next();
		while(hasNext) {
			boolean matched = true;
			for(int i=0;i<columnsToMatch;i++) {
				int colpos = colPositions.get(i);
				String rsvalue = rs.getString(colpos);
				if(colValues.get(i).equals(rsvalue)) {}
				else { matched = false; break; }
			}
			if(matched) {
				break;
			}
			hasNext = super.next();
		}
		return hasNext;
	}
}
