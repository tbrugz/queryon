package tbrugz.queryon.util;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.List;

public class MiscUtils {

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

}