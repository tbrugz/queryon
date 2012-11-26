package tbrugz.queryon;

import javax.servlet.ServletException;

import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;

public class SchemaModelUtils {
	
	static Relation getTable(SchemaModel model, RequestSpec reqspec, boolean searchViews) throws ServletException {
		String[] objectParts = reqspec.object.split("\\.");
		
		Relation table = null;
		if(objectParts.length>1) {
			table = (Table) DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getTables(), DBObjectType.TABLE, objectParts[0], objectParts[1]);
		}
		else {
			table = (Table) DBIdentifiable.getDBIdentifiableByTypeAndName(model.getTables(), DBObjectType.TABLE, objectParts[0]);
		}
		
		if(table == null) {
			if(searchViews) {
				table = getView(model, reqspec);
			}
			if(table == null) {
				throw new ServletException("Object "+reqspec.object+" not found");
			}
		}
		return table;
	}

	static View getView(SchemaModel model, RequestSpec reqspec) throws ServletException {
		String[] objectParts = reqspec.object.split("\\.");
		
		View view = null;
		if(objectParts.length>1) {
			view = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getViews(), DBObjectType.VIEW, objectParts[0], objectParts[1]);
		}
		else {
			view = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getViews(), DBObjectType.VIEW, objectParts[0]);
		}
		
		if(view == null) { throw new ServletException("Object "+reqspec.object+" not found"); }
		return view;
	}

	static ExecutableObject getExecutable(SchemaModel model, RequestSpec reqspec) throws ServletException {
		String[] objectParts = reqspec.object.split("\\.");
		
		ExecutableObject exec = null;
		if(objectParts.length==1) { //PROCEDURE
			exec = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getExecutables(), DBObjectType.PROCEDURE, objectParts[0]);
			if(exec==null) {
				exec = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getExecutables(), DBObjectType.FUNCTION, objectParts[0]);
			}
		}
		else if(objectParts.length==3) { //SCHEMA.PACKAGE.PROCEDURE
			exec = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getExecutables(), DBObjectType.PROCEDURE, objectParts[0], objectParts[2]);
			if(exec==null) {
				exec = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getExecutables(), DBObjectType.FUNCTION, objectParts[0], objectParts[2]);
			}
			if(exec!=null && !objectParts[1].equals(exec.getPackageName())) { exec = null; }
		}
		else if(objectParts.length==2) { //SCHEMA.PROCEDURE or PACKAGE.PROCEDURE
			exec = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getExecutables(), DBObjectType.PROCEDURE, objectParts[0], objectParts[1]);
			if(exec==null) {
				exec = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getExecutables(), DBObjectType.FUNCTION, objectParts[0], objectParts[1]);
			}
			if(exec==null) {
				exec = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getExecutables(), DBObjectType.PROCEDURE, objectParts[1]);
				if(exec!=null && !objectParts[0].equals(exec.getPackageName())) { exec = null; }
			}
			if(exec==null) {
				exec = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getExecutables(), DBObjectType.FUNCTION, objectParts[1]);
				if(exec!=null && !objectParts[0].equals(exec.getPackageName())) { exec = null; }
			}
		}
		
		if(exec == null) { throw new ServletException("Object "+reqspec.object+" not found"); }
		return exec;
	}
	
}
