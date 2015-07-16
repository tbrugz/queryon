package tbrugz.queryon.resultset;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import tbrugz.sqldump.resultset.RSMetaDataTypedAdapter;

public class ResultSetObjectAdaptor extends AbstractNavigationalResultSet {
	
	final String name;
	final Object object;
	final ResultSetMetaData metadata;

	final int rowCount;
	int position = -1;
	
	public ResultSetObjectAdaptor(String name, Object object) {
		this.name = name;
		this.object = object;
		metadata = new RSMetaDataTypedAdapter(null, null, getList(name), getList(Types.VARCHAR));
		rowCount = 1;
	}
	
	<E> List<E> getList(E obj) {
		List<E> ret = new ArrayList<E>();
		ret.add(obj);
		return ret;
	}
	
	@Override
	void updateCurrentElement() {
	}
	
	@Override
	int getPosition() {
		return position;
	}
	
	@Override
	int getRowCount() {
		return rowCount;
	}
	
	@Override
	void setPosition(int position) {
		this.position = position;
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		return metadata;
	}
	
	@Override
	public Object getObject(int columnIndex) throws SQLException {
		return object;
	}
	
	@Override
	public Object getObject(String columnLabel) throws SQLException {
		return object;
	}
	
	@Override
	public String getString(int columnIndex) throws SQLException {
		return String.valueOf(object);
	}
	
	@Override
	public String getString(String columnLabel) throws SQLException {
		return String.valueOf(object);
	}
}
