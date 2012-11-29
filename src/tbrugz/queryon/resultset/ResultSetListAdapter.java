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
		this(name, uniqueCols, null, list);
	}
	
	public ResultSetListAdapter(String name, List<String> uniqueCols, List<String> allCols, List<E> list) throws IntrospectionException {
		super(name, uniqueCols, allCols, list.iterator().next());
		this.list = list;
		position = -1;
	}
	
	@Override
	public int getType() throws SQLException {
		return ResultSet.TYPE_SCROLL_SENSITIVE;
	}
	
	@Override
	public boolean first() throws SQLException {
		position = 0;
		updateCurrentElement();
		return true;
	}
	
	@Override
	public boolean absolute(int row) throws SQLException {
		if(list.size()>=row) {
			position = row;
			updateCurrentElement();
		}
		return super.absolute(row);
	}

	@Override
	public boolean next() throws SQLException {
		if(list.size()-1 > position) {
			position++;
			updateCurrentElement();
			return true;
		}
		return false;
	}
	
	void updateCurrentElement() {
		currentElement = list.get(position);
	}

}
