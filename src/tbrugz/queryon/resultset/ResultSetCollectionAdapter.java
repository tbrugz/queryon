package tbrugz.queryon.resultset;

import java.beans.IntrospectionException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ResultSetCollectionAdapter<E extends Object> extends BaseResultSetCollectionAdapter<E> {
	
	static final Log log = LogFactory.getLog(ResultSetCollectionAdapter.class);

	final Iterator<E> iterator;
	
	public ResultSetCollectionAdapter(String name, List<String> uniqueCols, Collection<E> list) throws IntrospectionException {
		super(name, uniqueCols, (Class<E>) list.iterator().next().getClass());
		iterator = list.iterator();
	}
	
	@Override
	public int getType() throws SQLException {
		return ResultSet.TYPE_FORWARD_ONLY;
	}
	
	//XXX: absolute, relative? List needed...
	
	/*@Override
	public boolean first() throws SQLException {
		iterator = list.iterator();
		return true;
	}*/

	@Override
	public boolean next() throws SQLException {
		if(iterator.hasNext()) {
			currentElement = iterator.next();
			return true;
		}
		return false;
	}

}
