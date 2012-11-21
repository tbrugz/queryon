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
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class BaseResultSetCollectionAdapter<E extends Object> extends AbstractResultSet {
	
	static final Log log = LogFactory.getLog(ResultSetCollectionAdapter.class);

	final String name;
	final ResultSetMetaData metadata;
	final List<Method> methods = new ArrayList<Method>();

	E currentElement;

	public BaseResultSetCollectionAdapter(String name, List<String> uniqueCols, Collection<E> list) throws IntrospectionException {
		this.name = name;
		
		E e = list.iterator().next();
		Class<E> clazz = (Class<E>) e.getClass();
		List<String> columnNames = new ArrayList<String>();
		
		BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
		PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
		if(uniqueCols!=null) {
			for(String col: uniqueCols) {
				for (PropertyDescriptor prop : propertyDescriptors) {
					if(col.equals(prop.getName())) {
						columnNames.add(col);
						methods.add(prop.getReadMethod());
					}
				}
			}
		}
		for (PropertyDescriptor prop : propertyDescriptors) {
			String pname = prop.getName();
			if("class".equals(pname)) { continue; }
			if(columnNames.contains(pname)) { continue; }
			//XXX: continue on transient, ... ??
			
			Method m = prop.getReadMethod();
			columnNames.add(pname);
			methods.add(m);
		}
		
		/*Method[] ms = clazz.getMethods();
		for(Method m: ms) {
			String mname = m.toString();
			boolean isGet = mname.startsWith("get");
			boolean isIs = mname.startsWith("is");
			if(!isGet && !isIs) { continue; }
			if(m.getParameterTypes().length>0) { continue; }
			String colName = null;
			if(isGet) {
				colName = mname.substring(3);
			}
			else {
				colName = mname.substring(2);
			}
			columnNames.add(colName);
			methods.add(m);
		}*/
		log.info("resultset:cols: "+columnNames);
		metadata = new RSMetaDataAdapter(null, name, columnNames);
	}

	@Override
	public String getString(int columnIndex) throws SQLException {
		String ret = null;
		try {
			Method m = methods.get(columnIndex-1);
			if(m==null) { log.warn("method is null: "+(columnIndex-1)); return null; }
			ret = String.valueOf(m.invoke(currentElement, null));
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
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
