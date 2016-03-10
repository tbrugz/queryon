package tbrugz.queryon.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.SchemaModel;

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
	
	public static boolean hasPermissionOnColumn(List<Grant> grants, Set<String> roles, String column) {
		for(Grant g: grants) {
			if(g!=null && column.equals(g.getColumn()) && roles.contains(g.getGrantee())) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasPermissionWithoutColumn(List<Grant> grants, Set<String> roles) {
		for(Grant g: grants) {
			if(g!=null && g.getColumn()==null && roles.contains(g.getGrantee())) {
				return true;
			}
		}
		return false;
	}
	
	public static void setModelMetadata(SchemaModel model, Connection conn) throws SQLException {
		if(model==null) return;
		if(model.getMetadata()==null) {
			model.setMetadata(new TreeMap<String, String>());
		}
		model.getMetadata().put("database-product", conn.getMetaData().getDatabaseProductName());
		model.getMetadata().put("database-product-version", conn.getMetaData().getDatabaseProductVersion());
		model.getMetadata().put("database-major-version", String.valueOf(conn.getMetaData().getDatabaseMajorVersion()));
		model.getMetadata().put("database-minor-version", String.valueOf(conn.getMetaData().getDatabaseMinorVersion()));
		model.getMetadata().put("driver", conn.getMetaData().getDriverName());
		model.getMetadata().put("driver-version", conn.getMetaData().getDriverVersion());
		model.getMetadata().put("dburl", conn.getMetaData().getURL());
	}

	public static void setModelExceptionMetadata(SchemaModel model, Throwable t) throws SQLException {
		if(model==null) return;
		if(model.getMetadata()==null) {
			model.setMetadata(new TreeMap<String, String>());
		}
		model.getMetadata().put("error", t.toString());
	}

}
