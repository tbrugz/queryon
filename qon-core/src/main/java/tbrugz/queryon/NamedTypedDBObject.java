package tbrugz.queryon;

import java.util.ArrayList;
import java.util.List;

import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.TypedDBObject;

public class NamedTypedDBObject implements TypedDBObject {

	final DBObjectType type;
	final String schemaName;
	final String name;
	final String fullObjectName;
	final String mimetype;
	
	public NamedTypedDBObject(DBObjectType type, String schemaName, String name, String fullObjectName, String mimetype) {
		this.type = type;
		this.schemaName = schemaName;
		this.name = name;
		this.fullObjectName = fullObjectName;
		this.mimetype = mimetype;
	}
	
	public static NamedTypedDBObject getObject(List<String> partz) {
		DBObjectType type = null;
		String objType = partz.get(0).toUpperCase();
		String fullObjectName = partz.get(1);
		String schemaName = null;
		String objectName = fullObjectName;
		String mimetype = null;
		
		if(objectName.contains(".")) {
			String[] onPartz = objectName.split("\\.");
			if(onPartz.length<2 || onPartz.length>3) {
				throw new BadRequestException("Malformed object name: "+objectName);
			}
			schemaName = onPartz[0];
			objectName = onPartz[1];
			if(onPartz.length==3) {
				mimetype = onPartz[2];
			}
		}
		try {
			type = DBObjectType.valueOf(objType);
		}
		catch(IllegalArgumentException e) {
			throw new BadRequestException("Unknown object type: "+objType);
		}
		
		return new NamedTypedDBObject(type, schemaName, objectName, fullObjectName, mimetype);
	}

	public static List<NamedTypedDBObject> getObjectList(List<String> partz) {
		DBObjectType type = null;
		String objType = partz.get(0).toUpperCase();
		String originalObjectName = partz.get(1);
		String schemaName = null;
		//String objectName = fullObjectName;
		String mimetype = null;
		
		try {
			type = DBObjectType.valueOf(objType);
		}
		catch(IllegalArgumentException e) {
			throw new BadRequestException("Unknown object type: "+objType);
		}
		
		List<NamedTypedDBObject> ret = new ArrayList<NamedTypedDBObject>();
		String[] objectnames = originalObjectName.split(",");
		
		for(String obj: objectnames) {
			if(obj.contains(".")) {
				String[] onPartz = obj.split("\\.");
				if(onPartz.length<2 || onPartz.length>3) {
					throw new BadRequestException("Malformed object name: "+obj);
				}
				schemaName = onPartz[0];
				obj = onPartz[1];
				if(onPartz.length==3) {
					mimetype = onPartz[2];
				}
			}
			ret.add(new NamedTypedDBObject(type, schemaName, obj, (schemaName!=null?schemaName+".":"")+obj, mimetype));
		}
		
		return ret;
	}
	
	@Override
	public String toString() {
		return "["+type+":"+fullObjectName+(mimetype!=null?" ; mime="+mimetype:"")+"]";
	}
	
	@Override
	public DBObjectType getDbObjectType() {
		return type;
	}
	
	@Override
	public DBObjectType getDBObjectType() {
		return getDbObjectType();
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
	
	public String getMimeType() {
		return mimetype;
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
