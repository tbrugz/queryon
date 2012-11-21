package tbrugz.queryon.resultset;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ResultSetCollectionAdapter<E extends Object> extends AbstractResultSet {
	
	static final Log log = LogFactory.getLog(ResultSetCollectionAdapter.class);

	final String name;
	final ResultSetMetaData metadata;
	final List<Method> methods = new ArrayList<Method>();
	final Iterator<E> iterator;
	
	E currentElement;
	//int position = 0;

	public ResultSetCollectionAdapter(String name, Collection<E> list) throws IntrospectionException {
		this.name = name;
		
		E e = list.iterator().next();
		Class<E> clazz = (Class<E>) e.getClass();
		List<String> columnNames = new ArrayList<String>();
		
		BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
		PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
		for (PropertyDescriptor prop : propertyDescriptors) {
			Method m = prop.getReadMethod();
			String pname = prop.getName();
			if("class".equals(pname)) { continue; }
			//XXX: continue on transient, ... ??
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
		iterator = list.iterator();
	}

	@Override
	public boolean next() throws SQLException {
		if(iterator.hasNext()) {
			currentElement = iterator.next();
			return true;
		}
		return false;
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
