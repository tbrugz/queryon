package tbrugz.queryon.resultset;

import java.beans.IntrospectionException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ResultSetListAdapter<E extends Object> extends BaseResultSetCollectionAdapter<E> {
	static final Log log = LogFactory.getLog(ResultSetCollectionAdapter.class);

	final List<E> list;
	int position;

	public ResultSetListAdapter(String name, List<String> uniqueCols, List<E> list) throws IntrospectionException {
		super(name, uniqueCols, list);
		this.list = list;
		position = 0;
	}
	
	@Override
	public int getType() throws SQLException {
		return ResultSet.TYPE_SCROLL_SENSITIVE;
	}
	
	@Override
	public boolean first() throws SQLException {
		position = 0;
		currentElement = list.get(position);
		return true;
	}
	
	@Override
	public boolean absolute(int row) throws SQLException {
		if(list.size()>=row) {
			position = row;
			currentElement = list.get(position);
		}
		return super.absolute(row);
	}

	@Override
	public boolean next() throws SQLException {
		if(list.size() > position) {
			position++;
			currentElement = list.get(position);
			return true;
		}
		return false;
	}

}
