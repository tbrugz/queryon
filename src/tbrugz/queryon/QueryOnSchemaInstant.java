package tbrugz.queryon;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import javax.naming.NamingException;

import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.def.DBMSResources;

public class QueryOnSchemaInstant extends QueryOnSchema {

	private static final long serialVersionUID = 1L;

	void refreshModel(DBObjectType type, Properties prop, String modelId, SchemaModel model, String schemaName, String objectName) throws ClassNotFoundException, SQLException, NamingException {
		//XXX update table|fks?
		if(type==DBObjectType.TABLE) {
			Connection conn = DBUtil.initDBConn(prop, modelId);
			DBMSResources res = DBMSResources.instance();
			DBMSFeatures feat = res.databaseSpecificFeaturesClass();
			QueryOnInstant.grabTable(model, schemaName, objectName, conn.getMetaData(), feat);
			conn.close();
		}
	}
	
}
