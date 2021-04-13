package tbrugz.queryon.api;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import tbrugz.queryon.auth.AuthActions;
import tbrugz.queryon.auth.UserInfo;
import tbrugz.queryon.exception.InternalServerException;

public class BeanActions {

	public static final String QUERY_CURRENTUSER = "currentUser";
	
	public static final String[] beanQueries = { QUERY_CURRENTUSER };
	// useful for metadata (wsdl, ...) generation...
	public static final Class<?>[] beanQueriesParamBeans = { null };
	public static final Class<?>[] beanQueriesReturnBeans = { UserInfo.class };
	
	/*
	// for dynamic beans...
	public final class Action {
		String query;
		Class<?> paramBean;
		Class<?> returnBean;
	}
	*/
	
	final Properties prop;
	
	public BeanActions(Properties prop) {
		this.prop = prop;
	}
	
	public List<String> getBeanQueries() {
		return Arrays.asList(beanQueries);
	}
	
	public Object getBeanValue(String beanQuery, HttpServletRequest request) {
		AuthActions beanActions = new AuthActions(prop);
		if(beanQuery.equals(QUERY_CURRENTUSER)) {
			return beanActions.getCurrentUser(request);
		}
		
		throw new InternalServerException("unknown bean query: "+beanQuery);
	}
	
	public static Set<Class<?>> getAllBeanClasses() {
		Set<Class<?>> uniqueBeanTypes = new LinkedHashSet<Class<?>>();
		for(Class<?> c: BeanActions.beanQueriesParamBeans) {
			if(c!=null) { uniqueBeanTypes.add(c); }
		}
		for(Class<?> c: BeanActions.beanQueriesReturnBeans) {
			if(c!=null) { uniqueBeanTypes.add(c); }
		}
		return uniqueBeanTypes;
		//uniqueBeanTypes.addAll(Arrays.asList());
		//uniqueBeanTypes.addAll(Arrays.asList(BeanActions.beanQueriesReturnBeans));
	}
	
	public static List<PropertyDescriptor> getPropertyDescriptors(Class<?> beanClass) throws IntrospectionException {
		BeanInfo beanInfo = Introspector.getBeanInfo(beanClass);
		PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
		List<PropertyDescriptor> pds = new ArrayList<PropertyDescriptor>();
		for(PropertyDescriptor pd: propertyDescriptors) {
			String name = pd.getName();
			if(!"class".equals(name)) {
				pds.add(pd);
			}
		}
		return pds;
	}
	
}
