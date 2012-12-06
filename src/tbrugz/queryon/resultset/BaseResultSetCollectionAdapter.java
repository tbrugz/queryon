package tbrugz.queryon.resultset;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BaseResultSetCollectionAdapter<E extends Object> extends AbstractResultSet {
	
	static final Log log = LogFactory.getLog(ResultSetCollectionAdapter.class);

	final String name;
	final ResultSetMetaData metadata;
	final List<Method> methods = new ArrayList<Method>();

	E currentElement;

	public BaseResultSetCollectionAdapter(String name, List<String> uniqueCols, E value) throws IntrospectionException {
		this(name, uniqueCols, null, false, value);
	}
	
	public BaseResultSetCollectionAdapter(String name, List<String> uniqueCols, List<String> allCols, E value) throws IntrospectionException {
		this(name, uniqueCols, allCols, false, value);
	}

	//XXX: change 'E value' to 'Class<E> clazz'?
	public BaseResultSetCollectionAdapter(String name, List<String> uniqueCols, List<String> allCols, boolean onlyUniqueCols, E value) throws IntrospectionException {
		this.name = name;
		
		List<String> columnNames = new ArrayList<String>();
		metadata = new RSMetaDataAdapter(null, name, columnNames);
		
		Class<E> clazz = (Class<E>) value.getClass();
		
		BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
		PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
		if(uniqueCols!=null) {
			for(String col: uniqueCols) {
				addMatchProperties(propertyDescriptors, col, columnNames);
			}
		}
		if(!onlyUniqueCols) {
			if(allCols!=null) {
				for(String col: allCols) {
					addMatchProperties(propertyDescriptors, col, columnNames);
				}
			}
			else {
				addMatchProperties(propertyDescriptors, null, columnNames);
			}
		}
		log.debug("resultset:cols: "+columnNames);
	}
	
	void addMatchProperties(PropertyDescriptor[] propertyDescriptors, String matchCol, List<String> columnNames) {
		for (PropertyDescriptor prop : propertyDescriptors) {
			if(matchCol==null || matchCol.equals(prop.getName())) {
				String pname = prop.getName();
				if("class".equals(pname)) { continue; }
				if(columnNames.contains(pname)) { continue; }
				//XXX: continue on transient, ... ??
				
				Method m = prop.getReadMethod();
				columnNames.add(pname);
				methods.add(m);
			}
		}
	}

	@Override
	public String getString(int columnIndex) throws SQLException {
		String ret = null;
		try {
			Method m = methods.get(columnIndex-1);
			if(m==null) { log.warn("method is null: "+(columnIndex-1)); return null; }
			Object oret = m.invoke(currentElement, (Object[]) null);
			if(oret==null) { return null; }
			ret = String.valueOf(oret);
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} /*catch (IndexOutOfBoundsException e) {
			e.printStackTrace();
		}*/
		return ret;
	}

	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		return metadata;
	}

	@Override
	public Object getObject(int columnIndex) throws SQLException {
		return getString(columnIndex);
	}

}
