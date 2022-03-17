package tbrugz.queryon.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.def.DBMSResources;

public class QOnModelUtils {

	//private static final Log log = LogFactory.getLog(QOnModelUtils.class);
	
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
		if(grants!=null) {
			for(Grant g: grants) {
				if(g!=null && g.getColumn()==null && roles.contains(g.getGrantee())) {
					return true;
				}
			}
		}
		return false;
	}
	
	public static boolean isModelMetadataSet(SchemaModel model) {
		return model.getMetadata()!=null && model.getMetadata().get("database-product")!=null;
	}
	
	public static void setModelMetadata(SchemaModel model, String id, Connection conn) throws SQLException {
		if(model==null) return;
		if(model.getMetadata()==null) {
			model.setMetadata(new TreeMap<String, String>());
		}
		model.getMetadata().put("database-product", conn.getMetaData().getDatabaseProductName());
		model.getMetadata().put("database-product-version", conn.getMetaData().getDatabaseProductVersion());
		model.getMetadata().put("database-major-version", String.valueOf(conn.getMetaData().getDatabaseMajorVersion()));
		model.getMetadata().put("database-minor-version", String.valueOf(conn.getMetaData().getDatabaseMinorVersion()));
		model.getMetadata().put("driver-name", conn.getMetaData().getDriverName());
		model.getMetadata().put("driver-version", conn.getMetaData().getDriverVersion());
		model.getMetadata().put("db-url", conn.getMetaData().getURL());
		model.getMetadata().put("db-user", conn.getMetaData().getUserName());
		//model.getMetadata().put("db-metadata-storesLowerCaseIdentifiers", ""+conn.getMetaData().storesLowerCaseIdentifiers());
		//model.getMetadata().put("db-metadata-storesUpperCaseIdentifiers", ""+conn.getMetaData().storesUpperCaseIdentifiers());
		model.getMetadata().put("model-sqldialect", model.getSqlDialect());
		
		// features
		{
			//DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(model.getSqlDialect());
			//model.getMetadata().put("db-features-from-dialect", feat.getClass().getName());
		}
		{
			DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(conn.getMetaData());
			model.getMetadata().put("db-features", feat.getClass().getName());
			DatabaseMetaData dbmd = feat.getMetadataDecorator(conn.getMetaData());
			model.getMetadata().put("db-metadata", dbmd.getClass().getName());
		}
		
		//log.info("metadata["+id+"]: "+model.getMetadata());
		
		//feat.getExecutableObjectTypes()
		//feat.getKnownObjectTypes()
	}

	public static void setModelExceptionMetadata(SchemaModel model, Throwable t) throws SQLException {
		if(model==null) return;
		if(model.getMetadata()==null) {
			model.setMetadata(new TreeMap<String, String>());
		}
		model.getMetadata().put("error", t.toString());
	}
	
	// https://stackoverflow.com/questions/1445233/is-it-possible-to-solve-the-a-generic-array-of-t-is-created-for-a-varargs-param
	@SafeVarargs
	public static List<Relation> joinRelations(List<? extends Relation>... ls) {
		List<Relation> ret = new ArrayList<Relation>();
		for(List<? extends Relation> l: ls) {
			ret.addAll(l);
		}
		return ret;
	}

}
