package tbrugz.queryon.util;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import tbrugz.sqldump.util.ParametrizedProperties;

public class MiscUtils {

	/*
	public static boolean endsWithAny(String value, Collection<String> coll) {
		for(String s: coll) {
			if(value.endsWith(s)) { return true; }
		}
		return false;
	}
	*/

	static final Pattern PATTERN_MULTISLASH = Pattern.compile("\\/+", Pattern.CASE_INSENSITIVE);
	
	public static boolean containsIgnoreCase(Collection<String> coll, String value) {
		if(coll.contains(value) || coll.contains(value.toUpperCase()) || coll.contains(value.toLowerCase())) {
			return true;
		}
		return false;
	}

	public static int indexOfIgnoreCase(List<String> list, String value) {
		int idx = list.indexOf(value);
		if(idx<0) { idx = list.indexOf(value.toUpperCase()); }
		if(idx<0) { idx = list.indexOf(value.toLowerCase()); }
		return idx;
	}
	
	public static String toIntArrayAsString(String s) {
		StringBuilder sb = new StringBuilder();
		char[] cs = s.toCharArray();
		for(int i=0;i<cs.length;i++) {
			char c = cs[i];
			if(i>0) { sb.append(", "); }
			sb.append((int)c);
		}
		return sb.toString();
	}
	
	public static String latin1ToUtf8(String str) throws UnsupportedEncodingException {
		return new String(str.getBytes("ISO-8859-1"), "UTF-8");
	}
	
	public static Properties mergeProperties(Properties... props) {
		Properties merged = new ParametrizedProperties();
		for(Properties prop: props) {
			merged.putAll(prop);
		}
		return merged;
	}
	
	public static boolean isNullOrEmpty(String str) {
		return str==null || str.isEmpty();
	}
	
	public static List<String> toStringList(List<Object> objs) {
		List<String> ret = new ArrayList<String>();
		for(Object o: objs) {
			ret.add(String.valueOf(o));
		}
		return ret;
	}
	
	public static <T> T getLowerAlso(Map<String, T> map, String key) {
		T val = map.get(key);
		if(val==null) {
			val = map.get(key.toLowerCase());
		}
		return val;
	}
	
	public static boolean isInt(String s) {
		try {
			Integer.parseInt(s);
			return true;
		}
		catch(NumberFormatException e) {
			return false;
		}
	}

	public static Integer getInt(String value) {
		if (value!=null) {
			return Integer.parseInt(value);
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T[] expandArray(T[] arr, int len, Class<T> clazz) {
		if(arr==null || arr.length==0) { return null; }
		if(arr.length==len) { return arr; }
		if(arr.length!=1) {
			throw new IllegalArgumentException("can't expand array "+Arrays.toString(arr)+" to length "+len);
		}
		
		T[] ret = (T[]) Array.newInstance(clazz, len);
		for(int i=0;i<len;i++) {
			ret[i] = arr[0];
		}
		return ret;
	}

	public static boolean[] expandBooleanArray(boolean[] arr, int len) {
		if(arr==null || arr.length==0) { return null; }
		if(arr.length==len) { return arr; }
		if(len==0) { return new boolean[] {}; }
		if(arr.length!=1) {
			throw new IllegalArgumentException("can't expand array "+Arrays.toString(arr)+" to length "+len);
		}
		
		boolean[] ret = (boolean[]) Array.newInstance(Boolean.TYPE, len);
		for(int i=0;i<len;i++) {
			ret[i] = arr[0];
		}
		return ret;
	}

	public static String joinBooleanArray(boolean[] arr, String separtor) {
		if(arr==null || arr.length==0) { return null; }
		
		StringBuilder sb = new StringBuilder();
		for(int i=0;i<arr.length;i++) {
			if(i>0) { sb.append(separtor); }
			sb.append(arr[i]);
		}
		return sb.toString();
	}
	
	/*
	public static Object getFieldValue(Object o, String field) {
		try {
			Field f = o.getClass().getDeclaredField(field);
			f.setAccessible(true);
			return f.get(o);
		}
		catch(Exception e) {
			log.warn(e);
			return null;
		}
	}
	
	public static Object invokeMethod(Object o, String methodName) {
		try {
			Method method = o.getClass().getDeclaredMethod(methodName);
			method.setAccessible(true);
			return method.invoke(o);
		}
		catch(Exception e) {
			log.warn(e);
			return null;
		}
	}
	*/
	
	public static final String initCaps(String s) {
		if(s==null) { return null; }
		if(s.length()<2) { return s.toUpperCase(); }
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}
	
	public static int countChars(String s, char c) {
		int count = 0;
		for(int i=0;i<s.length();i++) {
			if(s.charAt(i)==c) { count++; }
		}
		return count;
	}
	
	public static String removeMultiSlash(String s) {
		return PATTERN_MULTISLASH.matcher(s).replaceAll("/");
	}
	
	public static DocumentBuilderFactory getDocumentBuilderFactory() throws ParserConfigurationException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		// https://rules.sonarsource.com/java/RSPEC-2755
		// https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#java
		//docFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		//docFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

		// https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#java
		String[] featuresToDisable = {
			// Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-general-entities
			// Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-general-entities
			// JDK7+ - http://xml.org/sax/features/external-general-entities
			//This feature has to be used together with the following one, otherwise it will not protect you from XXE for sure
			"http://xml.org/sax/features/external-general-entities",
			
			// Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
			// Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
			// JDK7+ - http://xml.org/sax/features/external-parameter-entities
			//This feature has to be used together with the previous one, otherwise it will not protect you from XXE for sure
			"http://xml.org/sax/features/external-parameter-entities",
			
			// Disable external DTDs as well
			"http://apache.org/xml/features/nonvalidating/load-external-dtd"
		};
		for (String feature : featuresToDisable) {
			//try {
				docFactory.setFeature(feature, false); 
			//} catch (ParserConfigurationException e) {
				// This should catch a failed setFeature feature
				//log.info("ParserConfigurationException was thrown. The feature '" + feature+ "' is probably not supported by your XML processor.");
			//}
		}
		
		docFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		//docFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		docFactory.setXIncludeAware(false);
		return docFactory;
	}
	
	public static String repeatString(String s, int n, String delim) {
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<n; i++) {
			if(i>=0) { sb.append(delim); }
			sb.append(s);
		}
		return sb.toString();
	}

}
