package tbrugz.queryon.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.ExecutableParameter;
import tbrugz.sqldump.dbmodel.ParametrizedDBObject;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.util.Utils;
import tbrugz.sqldump.dbmodel.Constraint.ConstraintType;

public class SchemaModelUtils {
	
	private static final Log log = LogFactory.getLog(SchemaModelUtils.class);
	
	public static final String ATTR_MODEL_MAP = "modelmap";
	public static final String ATTR_DEFAULT_MODEL = "defaultmodel";

	public static final String PARAM_MODEL = "model";
	
	public static class ByNameComparator implements Comparator<Relation> {
		@Override
		public int compare(Relation o1, Relation o2) {
			int compare = 0;
			compare = o1.getName().compareTo(o2.getName());
			return compare;
		}
	}
	
	public static Relation getRelation(SchemaModel model, RequestSpec reqspec, boolean searchViews) {
		return getRelation(model, reqspec.getObject(), searchViews);
	}
	
	public static Relation getRelation(SchemaModel model, String object, boolean searchViews) {
		//log.info("getRelation [provided '"+reqspec.object+"']");
		Relation relation = null;
		
		// search for view first
		if(searchViews) {
			relation = getView(model, object);
			if(relation!=null) { return relation; }
		}
		
		String[] objectParts = object.split("\\.");
		
		if(objectParts.length>2) {
			log.debug("getRelation: relation object must have 1 or 2 parts [provided "+objectParts.length+": '"+object+"']");
			//throw new BadRequestException("relation object must have 1 or 2 parts [provided "+objectParts.length+": '"+reqspec.object+"']");
			return null;
		}
		
		if(objectParts.length==2) {
			relation = (Table) DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getTables(), DBObjectType.TABLE, objectParts[0], objectParts[1]);
		}
		else if(objectParts.length==1) {
			relation = (Table) DBIdentifiable.getDBIdentifiableByTypeAndName(model.getTables(), DBObjectType.TABLE, objectParts[0]);
		}
		
		return relation;
	}

	/*private static View getView(SchemaModel model, RequestSpec reqspec) {
		return getView(model, reqspec.getObject());
	}*/
	
	private static View getView(SchemaModel model, String object) {
		String[] objectParts = object.split("\\.");
		
		View view = null;
		if(objectParts.length>2) {
			log.debug("getView: relation object must have 1 or 2 parts [provided "+objectParts.length+": '"+object+"']");
			//throw new BadRequestException("relation object must have 1 or 2 parts [provided "+objectParts.length+": '"+reqspec.object+"']");
			return null;
		}
		
		if(objectParts.length==2) {
			view = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getViews(), DBObjectType.VIEW, objectParts[0], objectParts[1]);
		}
		else if(objectParts.length==1) {
			view = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getViews(), DBObjectType.VIEW, objectParts[0]);
		}
		
		//log.debug("objectParts: "+Arrays.asList(objectParts)+" view: "+view);
		
		return view;
	}

	static ExecutableObject getExecutable(SchemaModel model, RequestSpec reqspec) {
		String[] objectParts = reqspec.getObject().split("\\.");
		
		ExecutableObject exec = null;
		if(objectParts.length==1) { //PROCEDURE
			exec = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getExecutables(), DBObjectType.PROCEDURE, objectParts[0]);
			if(exec==null) {
				exec = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getExecutables(), DBObjectType.FUNCTION, objectParts[0]);
			}
			//search for SCRIPT...
			if(exec==null) {
				exec = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getExecutables(), DBObjectType.SCRIPT, objectParts[0]);
			}
			if(exec==null) {
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
			//search for SCRIPT...
			if(exec==null) {
				exec = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getExecutables(), DBObjectType.SCRIPT, objectParts[0], objectParts[1]);
			}
			if(exec==null) {
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
	//XXX: allow ignore case
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
		Map<String, SchemaModel> models = (Map<String, SchemaModel>) context.getAttribute(ATTR_MODEL_MAP);
		if(models==null || models.size()==0) { return null; }
		if(modelId==null) {
			modelId = (String) context.getAttribute(ATTR_DEFAULT_MODEL);
		}
		/*if(models.get(modelId)==null) {
			modelId = QueryOn.DEFAULT_MODEL_KEY;
		}*/
		return models.get(modelId);
	}
	
	@SuppressWarnings("unchecked")
	public static Map<String, SchemaModel> getModels(ServletContext context) {
		return (Map<String, SchemaModel>) context.getAttribute(ATTR_MODEL_MAP);
	}

	public static void setModels(ServletContext context, Map<String, SchemaModel> models) {
		context.setAttribute(ATTR_MODEL_MAP, models);
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
			modelReq = (String) req.getServletContext().getAttribute(ATTR_DEFAULT_MODEL);
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
	
	public static String getValidatedModelId(HttpServletRequest req, final String modelId, boolean allowDefault) {
		String ret = modelId;
		if(modelId==null && allowDefault) {
			ret = (String) req.getServletContext().getAttribute(ATTR_DEFAULT_MODEL);
		}
		else {
			Set<String> models = getModelIds(req.getServletContext());
			if(!models.contains(modelId)) {
				throw new BadRequestException( "Model id '"+modelId+"' undefined"
						+(" [modelsIds: "+models+"]")
						);
			}
		}
		return ret;
	}
	
	public static String getDefaultModelId(ServletContext context) {
		return (String) context.getAttribute(ATTR_DEFAULT_MODEL);
	}
	
	public static void setDefaultModelId(ServletContext context, String modelId) {
		context.setAttribute(ATTR_DEFAULT_MODEL, modelId);
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
	
	public static int getNumberOfInParameters(List<ExecutableParameter> list) {
		if(list==null) { return 0; }
		int ret = 0;
		for(ExecutableParameter ep: list) {
			ret += isInParameter(ep) ? 1 : 0;
		}
		return ret;
	}

	public static int getNumberOfOutParameters(List<ExecutableParameter> list) {
		if(list==null) { return 0; }
		int ret = 0;
		for(ExecutableParameter ep: list) {
			ret += isOutParameter(ep) ? 1 : 0;
		}
		return ret;
	}
	
	public static boolean isInParameter(ExecutableParameter ep) {
		return (ep.getInout()==null || ep.getInout()==ExecutableParameter.INOUT.IN || ep.getInout()==ExecutableParameter.INOUT.INOUT);
	}
	
	public static boolean isOutParameter(ExecutableParameter ep) {
		if(ep.getInout()==null) { return false; }
		return (ep.getInout()==ExecutableParameter.INOUT.OUT || ep.getInout()==ExecutableParameter.INOUT.INOUT);
	}
	
	public static List<String> getUniqueNamedParameterNames(Query q) {
		List<String> pNames = q.getNamedParameterNames();
		if(pNames == null) { return null; }
		
		Set<String> uniqueNames = new LinkedHashSet<String>();
		uniqueNames.addAll(pNames);
		return Utils.newList(uniqueNames);
	}

	public static List<String> getUniqueNamedParameterTypes(Query q) {
		List<String> pTypes = q.getParameterTypes();
		List<String> pNames = q.getNamedParameterNames();
		Integer pCount = q.getParameterCount();
		if(pNames == null || pTypes==null || pCount==null || pCount == 0) { return null; }
		
		List<String> ret = new ArrayList<String>();
		Set<String> uniqueNames = new HashSet<String>();
		for(int i=0; i<pCount; i++) {
			String pname = pNames.get(i);
			if(!uniqueNames.contains(pname)) {
				ret.add(pTypes.get(i));
				uniqueNames.add(pname);
			}
		}
		return ret;
	}
	
	public static boolean hasParameters(Relation relation) {
		if(relation instanceof ParametrizedDBObject) {
			ParametrizedDBObject pobj = (ParametrizedDBObject) relation;
			return pobj.getParameterTypes()!=null && pobj.getParameterTypes().size() > 0;
		}
		return false;
	}

	public static SchemaModel mergeModels(SchemaModel sm, SchemaModel sm2) {
		if(sm==null) {
			return sm2;
		}
		if(sm2==null) {
			return sm;
		}

		sm.getTables().addAll(sm2.getTables());
		sm.getForeignKeys().addAll(sm2.getForeignKeys());
		sm.getIndexes().addAll(sm2.getIndexes());
		sm.getExecutables().addAll(sm2.getExecutables());
		sm.getSequences().addAll(sm2.getSequences());
		sm.getSynonyms().addAll(sm2.getSynonyms());
		sm.getTriggers().addAll(sm2.getTriggers());
		sm.getViews().addAll(sm2.getViews());
		
		return sm;
	}
	
}
