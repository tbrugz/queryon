package tbrugz.queryon.util;

import java.util.ArrayList;
import java.util.List;

import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.PrivilegeType;

public class QOnModelUtils {

	public static List<Grant> filterGrantsByPrivilegeType(List<Grant> grants, PrivilegeType priv) {
		if(grants==null) { return null; }
		List<Grant> ret = new ArrayList<Grant>();
		for(Grant g: grants) {
			if(g!=null && g.getPrivilege().equals(priv)) {
				ret.add(g);
			}
		}
		return ret;
	}
}
