package tbrugz.queryon;

import java.util.List;

import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.NamedDBObject;

public class NamedTypedDBObject implements NamedDBObject {

	final DBObjectType type;
	final String schemaName;
	final String name;
	final String fullObjectName;
	
	public NamedTypedDBObject(DBObjectType type, String schemaName, String name, String fullObjectName) {
		this.type = type;
		this.schemaName = schemaName;
		this.name = name;
		this.fullObjectName = fullObjectName;
	}
	
	public static NamedTypedDBObject getObject(List<String> partz) {
		DBObjectType type = null;
		String objType = partz.get(0).toUpperCase();
		String fullObjectName = partz.get(1);
		String schemaName = null;
		String objectName = fullObjectName;
		
		if(objectName.contains(".")) {
			String[] onPartz = objectName.split("\\.");
			if(onPartz.length!=2) {
				throw new BadRequestException("Malformed object name: "+objectName);
			}
			schemaName = onPartz[0];
			objectName = onPartz[1];
		}
		try {
			type = DBObjectType.valueOf(objType);
		}
		catch(IllegalArgumentException e) {
			throw new BadRequestException("Unknown object type: "+objType);
		}
		
		return new NamedTypedDBObject(type, schemaName, objectName, fullObjectName);
	}
	
	@Override
	public String toString() {
		return "["+type+":"+fullObjectName+"]";
	}
	
	public DBObjectType getType() {
		return type;
	}
	public String getSchemaName() {
		return schemaName;
	}
	public String getName() {
		return name;
	}
	public String getFullObjectName() {
		return fullObjectName;
	}
	
	/*public void setType(DBObjectType type) {
		this.type = type;
	}
	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}
	public void setName(String name) {
		this.name = name;
	}*/
	
}
