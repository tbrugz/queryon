package tbrugz.queryon;

import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;

public class SchemaModelUtils {
	
	public static final String PARAM_MODEL = "model";
	
	static Relation getRelation(SchemaModel model, RequestSpec reqspec, boolean searchViews) throws ServletException {
		Relation relation = null;
		
		// search for view first
		if(searchViews) {
			relation = getView(model, reqspec);
			if(relation!=null) { return relation; }
		}
		
		String[] objectParts = reqspec.object.split("\\.");
		
		if(objectParts.length>1) {
			relation = (Table) DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getTables(), DBObjectType.TABLE, objectParts[0], objectParts[1]);
		}
		else {
			relation = (Table) DBIdentifiable.getDBIdentifiableByTypeAndName(model.getTables(), DBObjectType.TABLE, objectParts[0]);
		}
		
		return relation;
	}

	private static View getView(SchemaModel model, RequestSpec reqspec) throws ServletException {
		String[] objectParts = reqspec.object.split("\\.");
		
		View view = null;
		if(objectParts.length>1) {
			view = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getViews(), DBObjectType.VIEW, objectParts[0], objectParts[1]);
		}
		else {
			view = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getViews(), DBObjectType.VIEW, objectParts[0]);
		}
		
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
	
	//XXXXX: should this be in tbrugz.sqldump.dbmodel.DBIdentifiable ? no, it uses RequestSpec as parameter
	@SuppressWarnings("unchecked")
	public static <T extends DBIdentifiable> T getDBIdentifiableBySchemaAndName(SchemaModel model, RequestSpec reqspec) {
		try {
			return (T) getRelation(model, reqspec, true);
		} catch (ServletException e) {
			try {
				return (T) getExecutable(model, reqspec);
			} catch (ServletException e1) {
				return null;
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public static SchemaModel getModel(ServletContext context, String modelId) {
		Map<String, SchemaModel> models = (Map<String, SchemaModel>) context.getAttribute(QueryOn.ATTR_MODEL_MAP);
		if(models==null || models.size()==0) { return null; }
		if(modelId==null) {
			modelId = (String) context.getAttribute(QueryOn.ATTR_DEFAULT_MODEL);
		}
		return models.get(modelId);
	}
	
	@SuppressWarnings("unchecked")
	public static Map<String, SchemaModel> getModels(ServletContext context) {
		return (Map<String, SchemaModel>) context.getAttribute(QueryOn.ATTR_MODEL_MAP);
	}
	
	public static Set<String> getModelIds(ServletContext context) {
		return getModels(context).keySet();
	}
	
	public static String getModelId(HttpServletRequest req) {
		return getModelId(req, PARAM_MODEL);
	}
	
	public static String getModelId(HttpServletRequest req, String param) {
		String modelReq = req.getParameter(param);
		if(modelReq==null) {
			modelReq = (String) req.getSession().getServletContext().getAttribute(QueryOn.ATTR_DEFAULT_MODEL);
		}
		else {
			Set<String> models = getModelIds(req.getSession().getServletContext());
			if(!models.contains(modelReq)) {
				throw new BadRequestException( "Model id '"+modelReq+"' undefined"
						+(PARAM_MODEL.equals(param)?"":" [param="+param+"]") );
			}
		}
		return modelReq;
	}
}
