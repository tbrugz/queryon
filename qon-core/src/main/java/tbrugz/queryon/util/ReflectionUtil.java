package tbrugz.queryon.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ReflectionUtil {

	static final Log log = LogFactory.getLog(ReflectionUtil.class);
	
	public static Object callProtectedMethodNoException(Object o, String methodName, Class<?>[] parameterTypes, Object... args) {
		try {
			return callProtectedMethod(o, methodName, parameterTypes, args);
		}
		catch(Exception e) {
			log.debug("callProtectedMethodNoException: "+e);
			//e.printStackTrace();
			return null;
		}
	}
	
	public static Object callProtectedMethod(Object o, String methodName, Class<?>[] parameterTypes, Object... args) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Method m = null;
		Class<?> c = o.getClass();
		for(; m==null && !c.equals(Object.class) ; c = c.getSuperclass()) {
			log.debug("find method "+methodName+" in class "+c.getCanonicalName());
			try {
				m = c.getDeclaredMethod(methodName, parameterTypes);
			}
			catch(NoSuchMethodException e) {
				log.debug("... NoSuchMethodException: "+e);
				return null;
			}
			catch(RuntimeException e) {
				log.debug("... RuntimeException: "+e);
				return null;
			}
		}
		return callProtectedMethod(o, m, args);
	}
	
	public static Object callProtectedMethod(Object o, Method m, Object... args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		if(!m.isAccessible()) {
			m.setAccessible(true);
		}
		return m.invoke(o, args);
	}
	
}
