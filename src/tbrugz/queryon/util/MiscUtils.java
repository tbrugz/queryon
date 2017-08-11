package tbrugz.queryon.util;

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
	
}
