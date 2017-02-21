package tbrugz.queryon.util;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.RequestSpec;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.dbmodel.Constraint.ConstraintType;

public class SchemaModelUtils {
	
	private static final Log log = LogFactory.getLog(SchemaModelUtils.class);
	
	public static final String PARAM_MODEL = "model";
	
	public static Relation getRelation(SchemaModel model, RequestSpec reqspec, boolean searchViews) {
		//log.info("getRelation [provided '"+reqspec.object+"']");
		Relation relation = null;
		
		// search for view first
		if(searchViews) {
			relation = getView(model, reqspec);
			if(relation!=null) { return relation; }
		}
		
		String[] objectParts = reqspec.object.split("\\.");
		
		if(objectParts.length>2) {
			log.debug("getRelation: relation object must have 1 or 2 parts [provided "+objectParts.length+": '"+reqspec.object+"']");
			//throw new BadRequestException("relation object must have 1 or 2 parts [provided "+objectParts.length+": '"+reqspec.object+"']");
			return null;
		}
		
		if(objectParts.length==2) {
			relation = (Table) DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getTables(), DBObjectType.TABLE, objectParts[0], objectParts[1]);
		}
		else {
			relation = (Table) DBIdentifiable.getDBIdentifiableByTypeAndName(model.getTables(), DBObjectType.TABLE, objectParts[0]);
		}
		
		return relation;
	}

	private static View getView(SchemaModel model, RequestSpec reqspec) {
		String[] objectParts = reqspec.object.split("\\.");
		
		View view = null;
		if(objectParts.length>2) {
			log.debug("getView: relation object must have 1 or 2 parts [provided "+objectParts.length+": '"+reqspec.object+"']");
			//throw new BadRequestException("relation object must have 1 or 2 parts [provided "+objectParts.length+": '"+reqspec.object+"']");
			return null;
		}
		
		if(objectParts.length==2) {
			view = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getViews(), DBObjectType.VIEW, objectParts[0], objectParts[1]);
		}
		else {
			view = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getViews(), DBObjectType.VIEW, objectParts[0]);
		}
		
		return view;
	}

	static ExecutableObject getExecutable(SchemaModel model, RequestSpec reqspec) {
		String[] objectParts = reqspec.object.split("\\.");
		
		ExecutableObject exec = null;
		if(objectParts.length==1) { //PROCEDURE
			exec = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getExecutables(), DBObjectType.PROCEDURE, objectParts[0]);
			if(exec==null) {
				exec = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getExecutables(), DBObjectType.FUNCTION, objectParts[0]);
			}
			if(exec==null) { //search for SCRIPT...
				exec = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getExecutables(), DBObjectType.EXECUTABLE, objectParts[0]);
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
			if(exec==null) { //search for SCRIPT...
				exec = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getExecutables(), DBObjectType.EXECUTABLE, objectParts[0], objectParts[1]);
			}
		}
		else {
			//log.warn("executable object must have 1, 2 or 3 parts [provided "+objectParts.length+": '"+reqspec.object+"']");
		}
		
		//if(exec == null) { throw new NotFoundException("Object "+reqspec.object+" not found"); }
		return exec;
	}
	
	//XXXXX: should this be in tbrugz.sqldump.dbmodel.DBIdentifiable ? no, it uses RequestSpec as parameter
	@SuppressWarnings("unchecked")
	public static <T extends DBIdentifiable> T getDBIdentifiableBySchemaAndName(SchemaModel model, RequestSpec reqspec) {
		//try {
			Relation r = getRelation(model, reqspec, true);
			if(r!=null) { return (T) r; }
			return (T) getExecutable(model, reqspec);
		/*} catch (ServletException e) {
			log.warn("getDBIdentifiableBySchemaAndName: "+e);
			try {
				return (T) getExecutable(model, reqspec);
			} catch (ServletException e1) {
				return null;
			}
		}*/
	}
	
	@SuppressWarnings("unchecked")
	public static SchemaModel getModel(ServletContext context, String modelId) {
		Map<String, SchemaModel> models = (Map<String, SchemaModel>) context.getAttribute(QueryOn.ATTR_MODEL_MAP);
		if(models==null || models.size()==0) { return null; }
		if(modelId==null) {
			modelId = (String) context.getAttribute(QueryOn.ATTR_DEFAULT_MODEL);
		}
		/*if(models.get(modelId)==null) {
			modelId = QueryOn.DEFAULT_MODEL_KEY;
		}*/
		return models.get(modelId);
	}
	
	@SuppressWarnings("unchecked")
	public static Map<String, SchemaModel> getModels(ServletContext context) {
		return (Map<String, SchemaModel>) context.getAttribute(QueryOn.ATTR_MODEL_MAP);
	}
	
	public static Set<String> getModelIds(ServletContext context) {
		Map<String, SchemaModel> models = getModels(context);
		if(models==null) { return null; }
		return models.keySet();
	}
	
	public static String getModelId(HttpServletRequest req) {
		return getModelId(req, PARAM_MODEL);
	}
	
	static String getModelId(HttpServletRequest req, String param) {
		return getModelId(req, param, true);
	}
	
	public static String getModelId(HttpServletRequest req, String param, boolean allowDefault) {
		String modelReq = req.getParameter(param);
		if(modelReq==null && allowDefault) {
			modelReq = (String) req.getServletContext().getAttribute(QueryOn.ATTR_DEFAULT_MODEL);
		}
		else {
			Set<String> models = getModelIds(req.getServletContext());
			if(!models.contains(modelReq)) {
				throw new BadRequestException( "Model id '"+modelReq+"' undefined"
						+(PARAM_MODEL.equals(param)?"":" [param="+param+"]")
						+(" [modelsIds: "+models+"]")
						);
			}
		}
		return modelReq;
	}
	
	public static String getDefaultModelId(ServletContext context) {
		return (String) context.getAttribute(QueryOn.ATTR_DEFAULT_MODEL);
	}
	
	public static Constraint getPK(Relation relation) {
		Constraint pk = null;
		List<Constraint> conss = relation.getConstraints();
		if(conss!=null) {
			Constraint uk = null;
			for(Constraint c: conss) {
				if(c.getType()==ConstraintType.PK) { pk = c; break; }
				if(c.getType()==ConstraintType.UNIQUE && uk == null) { uk = c; }
			}
			if(pk == null && uk != null) {
				pk = uk;
			}
		}
		return pk;
	}
	
}
