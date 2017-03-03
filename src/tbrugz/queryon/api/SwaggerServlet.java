package tbrugz.queryon.api;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.Gson;

import tbrugz.queryon.AbstractHttpServlet;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.QueryOn.ActionType;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.datadump.DumpSyntax;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBObject;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.util.StringUtils;

/*
 * Servlet mappings:
 * 
 * QueryOn: /q/*
 * QueryOnSchema: /qos/*
 * QueryOnInstant: /qoi/* (same api as QueryOn)
 * 
 * DiffServlet: /qdiff/*
 * DataDiffServlet: /datadiff/*
 * Diff2QServlet: /diff2q/*
 * DiffManyServlet: /diffmany/*
 */
public class SwaggerServlet extends AbstractHttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(SwaggerServlet.class);
	
	static final String PARAM_FILTERS = "filters";
	static final String PARAM_FILTERS_EXCEPT = "filters-except"; //XXX: add 'filters-except'
	
	List<String> syntaxes;

	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		log.info("request: path="+req.getPathInfo()+" ; query="+req.getQueryString());
		Gson gson = new Gson();

		// http://swagger.io/specification/
		Map<String, Object> swagger = new LinkedHashMap<String, Object>();
		swagger.put("swagger", "2.0");
		
		// http://swagger.io/specification/#infoObject
		Map<String, Object> info = new LinkedHashMap<String, Object>();
		info.put("title", "Queryon");
		info.put("version", "1.0");
		
		swagger.put("info", info);
		
		String scheme = req.getScheme();
		//String hostname = InetAddress.getLocalHost().getHostName().toLowerCase();
		String hostname = InetAddress.getLocalHost().getCanonicalHostName().toLowerCase();
		int port = req.getServerPort();
		String contextPath = getServletContext().getContextPath();
		//log.info("request: scheme="+scheme+" ; hostname="+hostname+" ; port="+port+" ; contextPath="+contextPath);
		
		String host = hostname;
		if( ("http".equals(scheme) && port==80) ||
			("https".equals(scheme) && port==443)) {
			// nothing to do yet
		}
		else {
			host += ":"+port;
		}
		
		swagger.put("host", host);
		swagger.put("basePath", contextPath + "/q");
		
		ServletContext context = req.getServletContext();
		//Properties prop = (Properties) context.getAttribute(QueryOn.ATTR_PROP);
		DumpSyntaxUtils dsutils = (DumpSyntaxUtils) context.getAttribute(QueryOn.ATTR_DUMP_SYNTAX_UTILS);

		//schemes: "http", "https", "ws", "wss"
		//consumes: A list of MIME types the APIs can consume
		//produces: A list of MIME types the APIs can produce - dumpsyntaxes
		Map<String, DumpSyntax> dss = dsutils.getSyntaxesByFormat();
		Set<String> produces = new TreeSet<String>();
		syntaxes = new ArrayList<String>();
		for(Entry<String, DumpSyntax> e: dss.entrySet()) {
			produces.add(e.getValue().getMimeType());
			syntaxes.add(e.getValue().getSyntaxId());
		}
		swagger.put("produces", produces);
		
		//RequestSpec reqspec = new RequestSpec(dsutils, req, prop, 0, false);
		String modelId = SchemaModelUtils.getModelId(req);
		SchemaModel model = SchemaModelUtils.getModel(req.getServletContext(), modelId);
		String defaultModelId = SchemaModelUtils.getDefaultModelId(context);

		//tags
		generateTags(swagger, model);
		
		//filters
		List<String> filters = null;
		String filtersParam = req.getParameter(PARAM_FILTERS);
		if(filtersParam!=null) {
			filters = new ArrayList<String>();
			filters.addAll(Arrays.asList(filtersParam.split(",")));
		}
		
		String urlAppend = "";
		if( !StringUtils.equalsNullsAllowed(modelId, defaultModelId)) {
			urlAppend += "?model="+modelId;
		}
		
		Map<String, Object> paths = new LinkedHashMap<String, Object>();
		
		//Table
		for(Table t: model.getTables()) {
			{
			Map<String, Object> operations = new LinkedHashMap<String, Object>();
			operations.put("get", createGetOper(t, filters));
			paths.put("/"+getQualifiedName(t)+".{syntax}"+urlAppend, operations);
			}
			
			{
			Map<String, Object> operations = new LinkedHashMap<String, Object>();
			// insert:POST
			operations.put("post", createUpdateOper(t, ActionType.INSERT));
			// update:PATCH (was PUT)
			operations.put("patch", createUpdateOper(t, ActionType.UPDATE));
			
			//XXX: delete:DELETE
			
			paths.put("/"+getQualifiedName(t)+urlAppend, operations);
			}
		}
		
		//View
		for(View v: model.getViews()) {
			Map<String, Object> operations = new LinkedHashMap<String, Object>();
			operations.put("get", createGetOper(v, filters));
			
			paths.put("/"+getQualifiedName(v)+".{syntax}"+urlAppend, operations);
		}
		
		//XXX Executable

		swagger.put("paths", paths);
		
		// definitions
		Map<String, Object> definitions = new LinkedHashMap<String, Object>();
		swagger.put("definitions", definitions);
		//Table
		for(Table t: model.getTables()) {
			definitions.put(getQualifiedName(t), createDefinition(t));
		}
		
		//XXX security
		// http://swagger.io/specification/#securityDefinitionsObject
		// http://swagger.io/specification/#securityRequirementObject
		// type: "basic", "apiKey" or "oauth2"
		
		resp.getWriter().write( gson.toJson(swagger) );
	}
	
	void generateTags(Map<String, Object> swagger, SchemaModel model) {
		Set<String> strTags = new TreeSet<String>();
		for(DBObject dbo: model.getTables()) {
			strTags.add(dbo.getSchemaName());
			/*if(strTags.contains(dbo.getSchemaName())) { continue; }
			Map<String, Object> tag = new LinkedHashMap<String, Object>();
			tag.put("name", dbo.getSchemaName());
			tags.add(tag);*/
		}
		for(DBObject dbo: model.getViews()) {
			strTags.add(dbo.getSchemaName());
		}
		//XXX executables
		
		strTags.remove("");
		List<Map<String, Object>> tags = new ArrayList<Map<String, Object>>();
		for(String st: strTags) {
			Map<String, Object> tag = new LinkedHashMap<String, Object>();
			tag.put("name", st);
			tags.add(tag);
		}
		swagger.put("tags", tags);
	}
	
	Map<String, Object> createGetOper(Relation t, List<String> filters) {
		Map<String, Object> oper = new LinkedHashMap<String, Object>();
		//oper.put("summary", "retrieve values from "+t.getQualifiedName());
		String fullName = getQualifiedName(t);
		String schema = t.getSchemaName();
		if(schema!=null && !schema.equals("")) {
			List<String> tags = new ArrayList<String>();
			tags.add(schema);
			oper.put("tags", tags);
		}
		oper.put("summary", "retrieve values from " + fullName );
		oper.put("description", "");
		oper.put("operationId", "get."+fullName);
		List<Map<String, Object>> parameters = new ArrayList<Map<String, Object>>();
		//syntaxes
		{
			Map<String, Object> pSyntax = new LinkedHashMap<String, Object>();
			pSyntax.put("name", "syntax");
			pSyntax.put("in", "path");
			pSyntax.put("description", "return data syntax");
			pSyntax.put("type", "string");
			//http://stackoverflow.com/questions/27603871/how-to-define-enum-in-swagger-io
			pSyntax.put("enum", syntaxes);
			pSyntax.put("required", true);
			parameters.add(pSyntax);
		}
		
		if(t instanceof View) {
			View v = (View) t;
			//XXXdone "p"+i - retrieve (query)
			Integer paramCount = v.getParameterCount();
			if(paramCount!=null) {
				for(int i=1;i<=paramCount;i++) {
					Map<String, Object> pNumbered = new LinkedHashMap<String, Object>();
					pNumbered.put("name", "p"+i);
					pNumbered.put("in", "query");
					pNumbered.put("description", "parameter number "+i);
					pNumbered.put("type", "string");
					pNumbered.put("required", true);
					parameters.add(pNumbered);
				}
			}
		}
		
		//List<Column> cols = t.getColumns();
		/*
		 * type	string	Required. The type of the parameter. Since the parameter is not located at the request body, 
		 * it is limited to simple types (that is, not an object). The value MUST be one of 
		 * "string", "number", "integer", "boolean", "array" or "file". 
		 * If type is "file", the consumes MUST be either "multipart/form-data", " application/x-www-form-urlencoded"
		 * or both and the parameter MUST be in "formData".
		 */
		List<String> colNames = t.getColumnNames();
		//List<String> colRemarks = t.getColumnRemarks();
		List<String> colTypes = t.getColumnTypes();

		// parameter: fields
		// http://stackoverflow.com/questions/36888626/defining-enum-for-array-in-swagger-2-0
		{
			Map<String, Object> pFields = new LinkedHashMap<String, Object>();
			pFields.put("name", "fields");
			pFields.put("in", "query");
			pFields.put("description", "fields (columns) to be returned");
			pFields.put("type", "array");
			
			Map<String, Object> pFieldsItems = new LinkedHashMap<String, Object>();
			pFieldsItems.put("type", "string");
			// fields separated by commas? https://github.com/swagger-api/swagger-ui/issues/713
			pFieldsItems.put("enum", colNames);
			pFields.put("items", pFieldsItems);
			pFields.put("collectionFormat", "csv");
			
			parameters.add(pFields);
		}
		
		// parameter: distinct
		{
			Map<String, Object> pDistinct = new LinkedHashMap<String, Object>();
			pDistinct.put("name", "distinct");
			pDistinct.put("in", "query");
			pDistinct.put("description", "return only distinct tuples/records");
			pDistinct.put("type", "boolean");
			parameters.add(pDistinct);
		}

		// parameter: order
		{
			Map<String, Object> pOrder = new LinkedHashMap<String, Object>();
			pOrder.put("name", "order");
			pOrder.put("in", "query");
			pOrder.put("description", "fields (columns) to order records by (append '-' to desc)");
			pOrder.put("type", "array");
			Map<String, Object> pFieldsItems = new LinkedHashMap<String, Object>();
			pFieldsItems.put("type", "string");
			// fields separated by commas, see: https://github.com/swagger-api/swagger-ui/issues/713
			List<String> colsOrder = new ArrayList<String>();
			for(String c: colNames) {
				colsOrder.add(c);
				colsOrder.add("-"+c);
			}
			pFieldsItems.put("enum", colsOrder);
			
			pOrder.put("items", pFieldsItems);
			pOrder.put("collectionFormat", "csv");
			parameters.add(pOrder);
		}

		// parameters: lostrategy?
		
		//XXX: filters: allowedColumns? CLOB, ...
		
		// parameter: filter: uniparam
		{
			List<String> fUni = Arrays.asList(RequestSpec.FILTERS_UNIPARAM);
			for(int i=0;i<colNames.size();i++) {
				String colName = colNames.get(i);
				//String remark = colRemarks.get(i);
				String type = getType(colTypes.get(i));
				if("file".equals(type)) {
					continue;
				}
				
				for(String f: fUni) {
					if(filters!=null && !filters.contains(f)) { continue; }
					Map<String, Object> param = new LinkedHashMap<String, Object>();
					param.put("name", f+":"+colName);
					param.put("in", "query");
					param.put("description", "filter '"+f+"' on field '"+colName+"'");
					/*if(remark!=null && !remark.equals("")) {
						param.put("description", remark);
					}*/
					param.put("type", type);
					
					//param.put("required", false);
					parameters.add(param);
				}
			}
		}

		// parameter: filter: multiparam
		{
			List<String> fMulti = Arrays.asList(RequestSpec.FILTERS_MULTIPARAM);
			List<String> fMultiStrOnly = Arrays.asList(RequestSpec.FILTERS_MULTIPARAM_STRONLY);
			for(int i=0;i<colNames.size();i++) {
				String colName = colNames.get(i);
				//String remark = colRemarks.get(i);
				String type = getType(colTypes.get(i));
				if("file".equals(type)) {
					continue;
				}
				
				for(String f: fMulti) {
					if(filters!=null && !filters.contains(f)) { continue; }
					Map<String, Object> param = new LinkedHashMap<String, Object>();
					param.put("name", f+":"+colName);
					param.put("in", "query");
					param.put("description", "filter '"+f+"' on field '"+colName+"'");
					/*if(remark!=null && !remark.equals("")) {
						param.put("description", remark);
					}*/
					param.put("type", "array");
					Map<String, Object> items = new LinkedHashMap<String, Object>();
					items.put("type", fMultiStrOnly.contains(f)?"string":type);
					param.put("items", items);
					param.put("collectionFormat", "multi");
					parameters.add(param);
				}
			}
		}

		// parameter: filter: bool
		{
			List<String> fBool = Arrays.asList(RequestSpec.FILTERS_BOOL);
			for(int i=0;i<colNames.size();i++) {
				String colName = colNames.get(i);
				String type = getType(colTypes.get(i));
				if("file".equals(type)) {
					continue;
				}
				
				for(String f: fBool) {
					if(filters!=null && !filters.contains(f)) { continue; }
					Map<String, Object> param = new LinkedHashMap<String, Object>();
					param.put("name", f+":"+colName);
					param.put("in", "query");
					param.put("description", "filter '"+f+"' on field '"+colName+"'");
					param.put("type", "boolean");
					
					//param.put("required", false);
					parameters.add(param);
				}
			}
		}
		
		//blob: valuefield, mimetype, mimefield, filename, filenamefield
		//updateparams: updatemax, updatemin, v:<xxx>
		//"p"+i -update (uk)
		//bodyparamname?

		// parameter: limit
		{
			Map<String, Object> pLimit = new LinkedHashMap<String, Object>();
			pLimit.put("name", "limit");
			pLimit.put("in", "query");
			pLimit.put("description", "limit number of entities to return");
			pLimit.put("type", "integer");
			pLimit.put("required", false);
			parameters.add(pLimit);
		}

		// parameter: offset
		{
			Map<String, Object> pOffset = new LinkedHashMap<String, Object>();
			pOffset.put("name", "offset");
			pOffset.put("in", "query");
			pOffset.put("description", "offset the returned entities by");
			pOffset.put("type", "integer");
			pOffset.put("required", false);
			parameters.add(pOffset);
		}
		
		//XXX add 'responses', possibly with schema (schema is json-only?)
		oper.put("parameters", parameters);
		return oper;
	}
	
	Map<String, Object> createUpdateOper(Relation r, ActionType action) {
		Map<String, Object> oper = new LinkedHashMap<String, Object>();
		String fullName = getQualifiedName(r);
		String schema = r.getSchemaName();
		if(schema!=null && !schema.equals("")) {
			List<String> tags = new ArrayList<String>();
			tags.add(schema);
			oper.put("tags", tags);
		}
		oper.put("summary", ( ActionType.INSERT.equals(action)?"insert values into ":"update values from " ) + fullName );
		oper.put("description", "");
		oper.put("operationId", action + "."+fullName);
		List<Map<String, Object>> parameters = new ArrayList<Map<String, Object>>();
		
		/*
		 * insert & update would be better to use formData - but in non-POST methods 'application/x-www-form-urlencoded' body is not processed
		 * see at org.apache.catalina.connector: Connector & Request
		 */
		String inParam = ActionType.INSERT.equals(action) ? "formData" : "query";
		
		Constraint cpk = SchemaModelUtils.getPK(r);
		//Constraint cpk = t.getPKConstraint();
		if(cpk!=null) {
			List<String> cols = cpk.getUniqueColumns();
			for(int i=1;i<=cols.size();i++) {
				Map<String, Object> pPk = new LinkedHashMap<String, Object>();
				String colName = cols.get(i-1);
				pPk.put("name", "p"+i);
				pPk.put("in", inParam);
				pPk.put("description", "parameter number "+i+" - value for field "+colName);
				String ctype = DBUtil.getColumnTypeFromColName(r, colName);
				String type = getType(ctype);
				//pPk.put("type", "string");
				pPk.put("type", type);
				parameters.add(pPk);
			}
		}
		
		List<String> colNames = r.getColumnNames();
		List<String> colTypes = r.getColumnTypes();
		
		for(int i=0;i<colNames.size();i++) {
			Map<String, Object> pValue = new LinkedHashMap<String, Object>();
			String colName = colNames.get(i);
			pValue.put("name", "v:"+colNames.get(i));
			pValue.put("in", inParam);
			pValue.put("description", "value for field "+colName); //+" of type "+colTypes.get(i));
			//String ctype = DBUtil.getColumnTypeFromColName(r, colName);
			String type = getType(colTypes.get(i));
			pValue.put("type", type);
			parameters.add(pValue);
		}
		
		//XXX reqspec.updatePartValues? maxUpdates/minUpdates?
		
		//XXX ActionType==update/PATCH: add "filterByXtraParams"
		
		oper.put("parameters", parameters);
		// responses: 201- created ; 500- error
		return oper;
	}
	
	Map<String, Object> createDefinition(Relation r) {
		//type, required, properties
		Map<String, Object> def = new LinkedHashMap<String, Object>();
		def.put("type", "object");
		if(r instanceof Table) {
			Table t = (Table) r;
			Constraint pk = t.getPKConstraint();
			if(pk!=null) {
				def.put("required", pk.getUniqueColumns());
			}
		}
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		List<String> colNames = r.getColumnNames();
		List<String> colTypes = r.getColumnTypes();
		List<String> colRemarks = r.getColumnRemarks();
		for(int i=0;i<colNames.size();i++) {
			Map<String, Object> col = new LinkedHashMap<String, Object>();
			String name = colNames.get(i);
			String type = getType(colTypes.get(i));
			String remarks = colRemarks.get(i);
			
			col.put("type", type);
			if(remarks!=null && !remarks.equals("")) {
				col.put("description", remarks);
			}
			properties.put(name, col);
		}
		def.put("properties", properties);
		return def;
	}
	
	String getQualifiedName(Relation t) {
		return ( t.getSchemaName()!=null && !"".equals(t.getSchemaName()) ? t.getSchemaName()+"." : "" )
				+ t.getName();
	}
	
	String getType(String colType) {
		String upper = colType.toUpperCase();
		boolean isInt = DBUtil.INT_COL_TYPES_LIST.contains(upper);
		if(isInt) {
			return "integer";
		}
		boolean isFloat = DBUtil.FLOAT_COL_TYPES_LIST.contains(upper);
		if(isFloat) {
			return "number";
		}
		boolean isBlob = DBUtil.BLOB_COL_TYPES_LIST.contains(upper);
		if(isBlob) {
			return "file";
		}
		//XXX: boolean col types...
		return "string";
	}

}
