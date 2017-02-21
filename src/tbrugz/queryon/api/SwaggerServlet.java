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
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.datadump.DumpSyntax;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.util.StringUtils;

public class SwaggerServlet extends AbstractHttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(SwaggerServlet.class);
	
	List<String> syntaxes;

	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		log.info("request: query="+req.getQueryString());
		Gson gson = new Gson();

		// http://swagger.io/specification/
		Map<String, Object> swagger = new LinkedHashMap<String, Object>();
		swagger.put("swagger", "2.0");
		
		// http://swagger.io/specification/#infoObject
		Map<String, Object> info = new LinkedHashMap<String, Object>();
		info.put("title", "Queryon");
		info.put("version", "1.0");
		
		swagger.put("info", info);
		
		String host = InetAddress.getLocalHost().getHostName().toLowerCase();
		String contextPath = getServletContext().getContextPath();
		
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
		
		String urlAppend = "";
		if( !StringUtils.equalsNullsAllowed(modelId, defaultModelId)) {
			urlAppend += "?model="+modelId;
		}
		
		Map<String, Object> paths = new LinkedHashMap<String, Object>();
		
		//Table
		for(Table t: model.getTables()) {
			Map<String, Object> operations = new LinkedHashMap<String, Object>();
			operations.put("get", createGetOper(t));
			
			//XXX: add insert/update/delete
			
			paths.put("/"+getQualifiedName(t)+".{syntax}"+urlAppend, operations);
		}
		
		//View
		for(View v: model.getViews()) {
			Map<String, Object> operations = new LinkedHashMap<String, Object>();
			operations.put("get", createGetOper(v));
			
			paths.put("/"+getQualifiedName(v)+".{syntax}"+urlAppend, operations);
		}
		
		//XXX Executable

		swagger.put("paths", paths);
		
		resp.getWriter().write( gson.toJson(swagger) );
	}
	
	Map<String, Object> createGetOper(Relation t) {
		Map<String, Object> oper = new LinkedHashMap<String, Object>();
		//oper.put("summary", "retrieve values from "+t.getQualifiedName());
		String fullName = getQualifiedName(t);
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
		List<String> colRemarks = t.getColumnRemarks();
		List<String> colTypes = t.getColumnTypes();
		
		//XXX: filters: allowedColumns? CLOB, ...
		
		List<String> fUni = Arrays.asList(RequestSpec.FILTERS_UNIPARAM);
		for(int i=0;i<colNames.size();i++) {
			String colName = colNames.get(i);
			String remark = colRemarks.get(i);
			String type = getType(colTypes.get(i));
			if("file".equals(type)) {
				continue;
			}
			
			for(String f: fUni) {
				Map<String, Object> param = new LinkedHashMap<String, Object>();
				param.put("name", f+":"+colName);
				param.put("in", "query");
				if(remark!=null && !remark.equals("")) {
					param.put("description", remark);
				}
				param.put("type", type);
				
				//param.put("required", false);
				parameters.add(param);
			}
		}
		//XXX List<String> fMulti = Arrays.asList(RequestSpec.FILTERS_MULTIPARAM);
		//XXX List<String> fBool = Arrays.asList(RequestSpec.FILTERS_BOOL);
		
		//XXX parameters: fields, distinct, order, (? lostrategy)
		//XXXdone "p"+i - retrieve (query)
		
		//blob: valuefield, mimetype, mimefield, filename, filenamefield
		//updateparams: updatemax, updatemin, v:<xxx>
		//"p"+i -update (uk)
		//bodyparamname?

		{
			Map<String, Object> pLimit = new LinkedHashMap<String, Object>();
			pLimit.put("name", "limit");
			pLimit.put("in", "query");
			pLimit.put("description", "limit number of entities to return");
			pLimit.put("type", "integer");
			pLimit.put("required", false);
			parameters.add(pLimit);
		}

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
		return oper;
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
