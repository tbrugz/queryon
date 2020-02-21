package tbrugz.queryon.api;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.Gson;

import tbrugz.queryon.AbstractHttpServlet;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.QueryOn.ActionType;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.ResponseSpec;
import tbrugz.queryon.SQL;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.datadump.DumpSyntax;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBObject;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.ExecutableParameter;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.util.StringUtils;
import tbrugz.sqldump.util.Utils;

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
	
	static final String DEFAULT_SYNTAX = "json";
	
	static final String PARAM_FILTERS = "filters";
	static final String PARAM_FILTERS_EXCEPT = "filters-except"; //XXX: add 'filters-except'
	
	static final String PROP_PREFIX = "queryon.api.swagger";
	static final String SUFFIX_FILTERS = ".filters";
	
	final boolean useCanonicalHost = false; //XXX: hostname: add property
	final boolean addHeadMethod = false; //XXX: head method: add property
	final boolean useNamedParameters = true;
	
	Set<String> syntaxes;
	String defaultSyntax;

	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		log.info("request: path="+req.getPathInfo()+" ; query="+req.getQueryString());
		Gson gson = new Gson();

		// http://swagger.io/specification/
		Map<String, Object> swagger = new LinkedHashMap<String, Object>();
		swagger.put("swagger", "2.0");
		
		// http://swagger.io/specification/#infoObject
		Map<String, Object> info = new LinkedHashMap<String, Object>();
		info.put("title", "Queryon"); // XXX add 'title' property
		info.put("version", "1.0");   // XXX add 'version' property - use scm commit id?
		
		swagger.put("info", info);
		
		String scheme = req.getScheme();
		
		String serverName = req.getServerName();
		//String localHostname = InetAddress.getLocalHost().getHostName().toLowerCase();
		String canonicalLocalHostname = InetAddress.getLocalHost().getCanonicalHostName().toLowerCase();
		
		String hostname = serverName;
		if(useCanonicalHost) {
			hostname = canonicalLocalHostname;
		}
		//log.info("serverName: "+serverName+" ; localHostname: "+localHostname+" ; canonicalLocalHostname: "+canonicalLocalHostname+" ; -> hostname: "+hostname);
		
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
		Properties prop = (Properties) context.getAttribute(QueryOn.ATTR_PROP);
		DumpSyntaxUtils dsutils = (DumpSyntaxUtils) context.getAttribute(QueryOn.ATTR_DUMP_SYNTAX_UTILS);

		//schemes: "http", "https", "ws", "wss"
		//consumes: A list of MIME types the APIs can consume
		//produces: A list of MIME types the APIs can produce - dumpsyntaxes
		Map<String, DumpSyntax> dss = dsutils.getSyntaxesByFormat();
		Set<String> produces = new TreeSet<String>();
		syntaxes = new TreeSet<String>();
		for(Entry<String, DumpSyntax> e: dss.entrySet()) {
			produces.add(e.getValue().getMimeType());
			syntaxes.add(e.getValue().getSyntaxId());
		}
		if(!syntaxes.contains(DEFAULT_SYNTAX)) {
			log.warn("default syntax '"+DEFAULT_SYNTAX+"' not present");
		}
		else {
			defaultSyntax = DEFAULT_SYNTAX;
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

		{
			List<String> allowedFilters = Utils.getStringListFromProp(prop, RequestSpec.PROP_FILTERS_ALLOWED, ",");
			if(allowedFilters!=null) {
				if(filters!=null) {
					filters.retainAll(allowedFilters);
				}
				else {
					filters = allowedFilters;
				}
			}
		}
		{
			List<String> swaggerFilters = Utils.getStringListFromProp(prop, PROP_PREFIX+SUFFIX_FILTERS, ",");
			if(swaggerFilters!=null) {
				if(filters!=null) {
					filters.retainAll(swaggerFilters);
				}
				else {
					filters = swaggerFilters;
				}
			}
		}
		
		String urlAppend = "";
		if( !StringUtils.equalsNullsAllowed(modelId, defaultModelId)) {
			urlAppend += "?model="+modelId;
		}
		
		Map<String, Object> paths = new LinkedHashMap<String, Object>();
		
		//XXX: only show table/view/executable that user has permission
		
		boolean allowDistinct = Utils.getPropBool(prop, RequestSpec.PROP_DISTINCT_ALLOW, true);
		
		//Table
		for(Table t: model.getTables()) {
			{
			Map<String, Object> operations = new LinkedHashMap<String, Object>();
			operations.put("get", createGetOper(t, filters, "get", allowDistinct));
			paths.put("/"+t.getQualifiedName()+".{syntax}"+urlAppend, operations);
			
			if(addHeadMethod) {
				operations.put("head", createGetOper(t, filters, "head", allowDistinct));
				paths.put("/"+t.getQualifiedName()+".{syntax}"+urlAppend, operations);
			}
			}
			
			{
			Map<String, Object> operations = new LinkedHashMap<String, Object>();
			// insert:POST
			operations.put("post", createUpdateOper(t, ActionType.INSERT));
			// update:PATCH (was PUT)
			operations.put("patch", createUpdateOper(t, ActionType.UPDATE));
			// delete:DELETE
			operations.put("delete", createUpdateOper(t, ActionType.DELETE));
			
			paths.put("/"+t.getQualifiedName()+urlAppend, operations);
			}
		}
		
		//View
		for(View v: model.getViews()) {
			Map<String, Object> operations = new LinkedHashMap<String, Object>();
			operations.put("get", createGetOper(v, filters, "get", allowDistinct));
			paths.put("/"+v.getQualifiedName()+".{syntax}"+urlAppend, operations);
			
			if(addHeadMethod) {
				operations.put("head", createGetOper(v, filters, "head", allowDistinct));
				paths.put("/"+v.getQualifiedName()+".{syntax}"+urlAppend, operations);
			}
		}
		
		//Executable
		for(ExecutableObject eo: model.getExecutables()) {
			Map<String, Object> operations = new LinkedHashMap<String, Object>();
			operations.put("post", createExecuteOper(eo));
			
			//paths.put("/"+getQualifiedName(eo)+urlAppend, operations);
			paths.put("/"+eo.getQualifiedName()+urlAppend, operations);
		}

		swagger.put("paths", paths);
		
		// definitions
		Map<String, Object> definitions = new LinkedHashMap<String, Object>();
		swagger.put("definitions", definitions);
		//Table
		for(Table t: model.getTables()) {
			definitions.put(t.getQualifiedName(), createDefinition(t));
		}
		//View
		for(View v: model.getViews()) {
			definitions.put(v.getQualifiedName(), createDefinition(v));
		}
		
		//XXX security
		// http://swagger.io/specification/#securityDefinitionsObject
		// http://swagger.io/specification/#securityRequirementObject
		// type: "basic", "apiKey" or "oauth2"
		
		resp.setContentType(ResponseSpec.MIME_TYPE_JSON);
		//resp.setCharacterEncoding(QueryOn.UTF8);
		
		resp.getWriter().write( gson.toJson(swagger) );
	}
	
	void generateTags(Map<String, Object> swagger, SchemaModel model) {
		Set<String> strTags = new TreeSet<String>();
		for(DBObject dbo: model.getTables()) {
			if(dbo.getSchemaName()!=null) {
				strTags.add(dbo.getSchemaName());
			}
			/*if(strTags.contains(dbo.getSchemaName())) { continue; }
			Map<String, Object> tag = new LinkedHashMap<String, Object>();
			tag.put("name", dbo.getSchemaName());
			tags.add(tag);*/
		}
		for(DBObject dbo: model.getViews()) {
			if(dbo.getSchemaName()!=null) {
				strTags.add(dbo.getSchemaName());
			}
		}
		for(DBObject dbo: model.getExecutables()) {
			if(dbo.getSchemaName()!=null) {
				strTags.add(dbo.getSchemaName());
			}
		}
		
		strTags.remove("");
		List<Map<String, Object>> tags = new ArrayList<Map<String, Object>>();
		for(String st: strTags) {
			Map<String, Object> tag = new LinkedHashMap<String, Object>();
			tag.put("name", st);
			tags.add(tag);
		}
		swagger.put("tags", tags);
	}
	
	Map<String, Object> createGetOper(Relation t, List<String> filters, String method, boolean allowDistinct) {
		Map<String, Object> oper = new LinkedHashMap<String, Object>();
		//oper.put("summary", "retrieve values from "+t.getQualifiedName());
		String fullName = t.getQualifiedName();
		String schema = t.getSchemaName();
		if(schema!=null && !schema.equals("")) {
			List<String> tags = new ArrayList<String>();
			tags.add(schema);
			oper.put("tags", tags);
		}
		oper.put("summary", "retrieve values from " + fullName );
		oper.put("description", t.getRemarks());
		oper.put("operationId", method+"."+fullName);
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
			//if(syntaxes.contains(DEFAULT_SYNTAX)) {
			if(defaultSyntax!=null) {
				pSyntax.put("default", defaultSyntax);
			}
			pSyntax.put("required", true);
			parameters.add(pSyntax);
		}
		
		//int paramCount = t.getParameterCount() !=null ? t.getParameterCount() : 0;
		if(t instanceof View) {
			if(useNamedParameters && t instanceof Query && ((Query)t).getNamedParameterNames()!=null) {
				Query q = (Query) t;
				List<String> namedParameters = SchemaModelUtils.getUniqueNamedParameterNames(q);
				for(int i=0;i <namedParameters.size() ;i++) {
					Map<String, Object> pNamed = new LinkedHashMap<String, Object>();
					pNamed.put("name", namedParameters.get(i));
					pNamed.put("in", "query");
					pNamed.put("description", "parameter "+namedParameters.get(i));
					pNamed.put("type", "string");
					pNamed.put("required", true); //XXX what if 'bind-null-on-missing-parameters'?
					parameters.add(pNamed);
				}
			}
			else {
				View v = (View) t;
				Integer paramCount = v.getParameterCount();
				if(paramCount!=null) {
					for(int i=1; i<=paramCount; i++) {
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
		if(colNames==null) { colNames = new ArrayList<String>(); }
		//List<String> colRemarks = t.getColumnRemarks();
		List<String> colTypes = t.getColumnTypes();

		// parameter: fields
		// http://stackoverflow.com/questions/36888626/defining-enum-for-array-in-swagger-2-0
		if(colNames.size()>0) {
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
		if(allowDistinct && t instanceof Query) {
			allowDistinct = SQL.allowEncapsulation(((Query) t).getQuery());
		}
		if(allowDistinct) {
			Map<String, Object> pDistinct = new LinkedHashMap<String, Object>();
			pDistinct.put("name", "distinct");
			pDistinct.put("in", "query");
			pDistinct.put("description", "return only distinct tuples/records");
			pDistinct.put("type", "boolean");
			parameters.add(pDistinct);
		}

		// parameter: order
		if(colNames.size()>0) {
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
		
		//XXX: filters: allowFilterOnColumn()? CLOB, ...
		
		// parameter: filter: uniparam
		{
			List<String> fUni = Arrays.asList(RequestSpec.FILTERS_UNIPARAM);
			for(int i=0;i<colNames.size();i++) {
				String colName = colNames.get(i);
				//String remark = colRemarks.get(i);
				String type = getType(colTypes.get(i));
				if(allowFilterOnType(type)) {
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
				if(allowFilterOnType(type)) {
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
				if(allowFilterOnType(type)) {
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
		
		//blob: valuefield, mimetype, mimetypefield, filename, filenamefield
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
		
		oper.put("parameters", parameters);
		
		/*
		 * XXXdone add 'responses', possibly with schema (schema is json-only?)
		 * XXX errors: 400 client error, 403 forbidden, 500 internal error
		 * ok: 200 ok
		 * insert: 201 created - text
		 * update: 200 ok - text
		 * delete: 200 ok - text
		 * delete: 404 not found
		 * execute: 200 ok - text
		 */
		// responses: 200
		{
			Map<String, Object> rOkSchema = new LinkedHashMap<String, Object>();
			rOkSchema.put("$ref", "#/definitions/" + fullName);
			Map<String, Object> rOk = new LinkedHashMap<String, Object>();
			rOk.put("description", "Ok");
			rOk.put("schema", rOkSchema);
			
			Map<Integer, Map<String, Object>> responses = new TreeMap<Integer, Map<String, Object>>();
			responses.put(200, rOk);
			oper.put("responses", responses);
		}
		
		return oper;
	}
	
	Map<String, Object> createUpdateOper(Relation r, ActionType action) {
		Map<String, Object> oper = new LinkedHashMap<String, Object>();
		String fullName = r.getQualifiedName();
		String schema = r.getSchemaName();
		if(schema!=null && !schema.equals("")) {
			List<String> tags = new ArrayList<String>();
			tags.add(schema);
			oper.put("tags", tags);
		}
		String summary = ActionType.INSERT.equals(action)?"insert values into ":
			ActionType.UPDATE.equals(action)?"update values from ":
			ActionType.DELETE.equals(action)?"delete values from ":
			"unknown operation with ";
		oper.put("summary", summary + fullName );
		oper.put("description", r.getRemarks());
		oper.put("operationId", action + "."+fullName);
		List<Map<String, Object>> parameters = new ArrayList<Map<String, Object>>();
		
		/*
		 * insert & update would be better to use formData - but in non-POST methods 'application/x-www-form-urlencoded' body is not processed
		 * see at org.apache.catalina.connector: Connector & Request
		 */
		String inParam = ActionType.INSERT.equals(action) ? "formData" : "query";
		
		Constraint cpk = SchemaModelUtils.getPK(r);
		//Constraint cpk = t.getPKConstraint();
		if(cpk!=null && (ActionType.UPDATE.equals(action) || ActionType.DELETE.equals(action)) ) {
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
		
		if(ActionType.INSERT.equals(action) || ActionType.UPDATE.equals(action)) {
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
		}
		
		//XXX reqspec.updatePartValues? maxUpdates/minUpdates?
		
		//XXX ActionType==update/PATCH | delete: add "filterByXtraParams"
		
		oper.put("parameters", parameters);
		
		// responses: 200, 201, 404
		{
			Map<Integer, Map<String, Object>> responses = new TreeMap<Integer, Map<String, Object>>();
			
			Integer okCode = ActionType.INSERT.equals(action)?201:
				ActionType.UPDATE.equals(action)?200:
				ActionType.DELETE.equals(action)?200:
				500; // unknown
			Map<String, Object> rOk = new LinkedHashMap<String, Object>();
			rOk.put("description", "Ok");
			responses.put(okCode, rOk);
			
			if(ActionType.DELETE.equals(action)) {
				Map<String, Object> rNotFound = new LinkedHashMap<String, Object>();
				rNotFound.put("description", "Not Found");
				responses.put(HttpServletResponse.SC_NOT_FOUND, rNotFound);
			}
			
			oper.put("responses", responses);
		}
		
		return oper;
	}
	
	Map<String, Object> createExecuteOper(ExecutableObject eo) {
		Map<String, Object> oper = new LinkedHashMap<String, Object>();
		String fullName = eo.getQualifiedName();
		String schema = eo.getSchemaName();
		if(schema!=null && !schema.equals("")) {
			List<String> tags = new ArrayList<String>();
			tags.add(schema);
			oper.put("tags", tags);
		}
		String summary = "execute "+eo.getDbObjectType().toString().toLowerCase()+" ";
		oper.put("summary", summary + fullName );
		oper.put("description", eo.getRemarks());
		oper.put("operationId", "execute."+fullName);
		List<Map<String, Object>> parameters = new ArrayList<Map<String, Object>>();
		
		List<ExecutableParameter> params = eo.getParams();
		int i=1;
		if(params!=null) {
		for(ExecutableParameter ep: params) {
			if(ep.getInout()!=null && ep.getInout()==ExecutableParameter.INOUT.OUT) { continue; }
			
			Map<String, Object> pPk = new LinkedHashMap<String, Object>();
			//XXX: Executable parameters: use position or counter? ignoring 'getPosition()'
			int position = i;
			/*int position = ep.getPosition();
			if(position<=0) {
				position = i;
			}*/
			pPk.put("name", "p"+position);
			pPk.put("in", "formData");
			pPk.put("description", "parameter #"+position+
				( ep.getName()!=null?", named "+ep.getName():"" )+
				( ep.getDataType()!=null?", with type "+ep.getDataType():"" )
				);
			String type = getType(ep.getDataType());
			//pPk.put("type", "string");
			pPk.put("type", type);
			parameters.add(pPk);
			i++;
		}
		}
		
		oper.put("parameters", parameters);
		
		// responses: 200
		{
			Map<String, Object> rOk = new LinkedHashMap<String, Object>();
			rOk.put("description", "Ok");
			
			Map<Integer, Map<String, Object>> responses = new TreeMap<Integer, Map<String, Object>>();
			responses.put(200, rOk);
			oper.put("responses", responses);
		}
		
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
		List<String> colNames = r.getColumnNames();
		List<String> colTypes = r.getColumnTypes();
		List<String> colRemarks = r.getColumnRemarks();
		if(colNames!=null) {
			Map<String, Object> properties = new LinkedHashMap<String, Object>();
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
		}
		return def;
	}
	
	/*String getQualifiedName(Relation t) {
		return ( t.getSchemaName()!=null && !"".equals(t.getSchemaName()) ? t.getSchemaName()+"." : "" )
				+ t.getName();
	}*/
	
	String getType(String colType) {
		if(colType==null) { return "string"; }
		String upper = colType.toUpperCase();
		
		boolean isInt = DBUtil.INT_COL_TYPES_LIST.contains(upper);
		if(isInt) {
			return "integer";
		}
		boolean isFloat = DBUtil.FLOAT_COL_TYPES_LIST.contains(upper);
		if(isFloat) {
			return "number";
		}
		boolean isBoolean = DBUtil.BOOLEAN_COL_TYPES_LIST.contains(upper);
		if(isBoolean) {
			return "boolean";
		}
		boolean isBlob = DBUtil.BLOB_COL_TYPES_LIST.contains(upper);
		if(isBlob) {
			return "file";
		}
		boolean isObject = DBUtil.OBJECT_COL_TYPES_LIST.contains(upper);
		if(isObject) {
			return "object";
		}
		//XXX: date?
		//log.info("unknown? "+upper);
		return "string";
	}
	
	boolean allowFilterOnType(String type) {
		return "file".equals(type) || "object".equals(type);
	}

}
