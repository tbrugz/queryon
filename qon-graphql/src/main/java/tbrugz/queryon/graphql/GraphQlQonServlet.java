package tbrugz.queryon.graphql;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.ResponseSpec;
import tbrugz.queryon.api.BaseApiServlet;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.graphql.GqlSchemaFactory.QonAction;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.util.IOUtil;

public class GraphQlQonServlet extends BaseApiServlet { // extends HttpServlet

	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(GraphQlQonServlet.class);
	
	static final String[] ACCEPTED_METHODS = {"GET", "POST"};
	
	public static final boolean allowAuthentication = false; // XXX: allow setting with properties
	
	@Override
	protected void doService(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if(Arrays.binarySearch(ACCEPTED_METHODS, req.getMethod().toUpperCase())<0) {
			throw new BadRequestException("Method not accepted: "+req.getMethod());
		}
		
		boolean requestSchema = req.getParameter("schema")!=null;
		ExecutionInput exec = getExecutionInput(req);
		
		if(exec.getQuery()==null && !requestSchema) {
			throw new BadRequestException("query must not be null");
		}

		log.info(">> GraphQlQonServlet: method: "+req.getMethod());
		//log.info(">> GqlQuery: "+exec.getQuery());
		
		String modelId = SchemaModelUtils.getModelId(req); //XXX: get modelId (also) from POST body (json)?
		SchemaModel sm = getSchemaModel(modelId, req);
		
		// XXX "cache" GqlSchemaFactory? application attribute... also: when to invalidate?
		GqlSchemaFactory gqls = new GqlSchemaFactory(sm);
		Map<String, QonAction> actionMap = gqls.amap;
		DataFetcher<?> df = getDataFetcher(sm, actionMap, gqls.colMap, req, resp);
		GraphQLSchema graphQLSchema = gqls.getSchema(df);
		if(requestSchema) {
			resp.setContentType(ResponseSpec.MIME_TYPE_TEXT_PLAIN);
			writeSchema(graphQLSchema, resp.getWriter());
			return;
		}
		resp.setContentType(GqlMapBufferSyntax.MIME_TYPE);
		GraphQL gql = GraphQL.newGraphQL(graphQLSchema).build();
		ExecutionResult executionResult = gql.execute(exec);
		
		// https://github.com/graphql-java/graphql-java/issues/649
		// https://github.com/graphql-java/graphql-java/blob/master/docs/execution.rst#serializing-results-to-json
		Map<String, Object> executionSpecResult = executionResult.toSpecification();
		Gson gson = new GsonBuilder().create();
		resp.getWriter().write(gson.toJson(executionSpecResult));
		//XXX: add xml-output option?
		//writeResult(exec, executionResult, resp);
	}
	
	void writeSchema(GraphQLSchema graphQLSchema, Writer writer) throws IOException {
		SchemaPrinter sp = new SchemaPrinter();
		String schemaStr = sp.print(graphQLSchema);
		writer.write(schemaStr);
	}
	
	@Deprecated
	void writeResult(ExecutionInput exec, ExecutionResult executionResult, HttpServletResponse resp) throws IOException {

		Gson gson = new GsonBuilder()
				.create();
		Map<String, Object> ret = new LinkedHashMap<>();
		boolean hasErrors = false;
		if(executionResult.getErrors() != null && executionResult.getErrors().size()>0) {
			log.warn("errors: "+executionResult.getErrors());
			ret.put("errors", executionResult.getErrors());
			log.warn("errors written");
			hasErrors = true;
		}
		/*if(executionResult.getData() instanceof String) {
			// parse JSON?
			resp.getWriter().write((String) executionResult.getData());
		}*/
		if(executionResult.getData() instanceof Object) {
			log.warn("hasData... "+executionResult.getData().getClass());
			if(hasErrors) {
				log.warn("hasErrors: variables: "+exec.getVariables());
			}
			
			if(hasErrors && exec.getVariables()==null) {
				log.warn("hasErrors & null variables... won't dump result");
			}
			else {
				Object data = executionResult.getData();
				//log.info("type: "+data.getClass()+" / "+data);
				//resp.getWriter().write(String.valueOf(data));
				//resp.getWriter().write("{\"data\":"+gson.toJson(data)+"}");
				ret.put("data", data);
			}
		}
		else if(executionResult.getData() != null) {
			log.warn("getData() not an Object & != null");
			//resp.getWriter().write(String.valueOf(executionResult.getData()));
		}
		
		if(ret.size()>0) {
			if(hasErrors) {
				log.warn("hasErrors: variables: "+exec.getVariables());
			}
			if(hasErrors && (exec.getVariables()==null || exec.getVariables().size()==0)) {
				//log.warn("hasErrors & null variables... won't dump result");
				log.info("hasErrors & null variables... ret: "+ret);
				log.info("hasErrors:: "+executionResult.getErrors().get(0).getClass());
				resp.getWriter().write(gson.toJson(executionResult.getErrors()));
				log.info("errors written...");
			}
			else {
				log.info("will write");
				//XXXxx if has errors, "toJson" may generate stackoverflow (null variables..., ?)
				resp.getWriter().write(gson.toJson(ret));
				log.info("has written");
			}
		}
		else {
			log.warn("null output...");
			resp.getWriter().write("null output");
		}

		//super.doService(req, resp);
	}
	
	// should move to GqlRequest ? maybe not...
	@SuppressWarnings("unchecked")
	ExecutionInput getExecutionInput(HttpServletRequest req) throws IOException {
		ExecutionInput.Builder execBuilder = ExecutionInput.newExecutionInput();
		if(req.getMethod().equals("GET")) {
			String query = req.getParameter("query");
			execBuilder.query(query);
		}
		else if(req.getMethod().equals("POST")) {
			//XXX test for Content-Type: application/json or application/graphql
			String httpBody = IOUtil.readFromReader(req.getReader()).trim();
			Gson gson = new Gson();
			try {
				Map<String, Object> map = (Map<String, Object>) gson.fromJson(httpBody, Map.class);
				if(map==null) {
					throw new BadRequestException("empty body? [method: "+req.getMethod()+"]");
				}
				
				String query = (String) map.get("query");
				execBuilder.query(query);
				Object oVar = (Object) map.get("variables");
				if(oVar instanceof Map<?, ?>) {
					Map<String, Object> variables = (Map<String, Object>) oVar;
					execBuilder.variables(variables);
				}
				String operationName = (String) map.get("operationName");
				execBuilder.operationName(operationName);
			}
			catch(JsonSyntaxException e) {
				throw new BadRequestException("malformed json in body [method: "+req.getMethod()+"]");
			}
		}
		else {
			throw new BadRequestException("GraphQl query missing [method: "+req.getMethod()+"]");
		}
		ExecutionInput exec = execBuilder.build();
		return exec;
	}
	
	/*
	String getGraphqlQuery(HttpServletRequest req) throws IOException {
		if(req.getMethod().equals("GET")) {
			return req.getParameter("query");
		}
		else if(req.getMethod().equals("POST")) {
			String httpBody = IOUtil.readFile(req.getReader()).trim();
			Gson gson = new Gson();
			Map<String, Object> map = (Map<String, Object>) gson.fromJson(httpBody, Map.class);
			String query = (String) map.get("query");
			return query;
		}
		throw new BadRequestException("GraphQl query missing [method: "+req.getMethod()+"]");
	}
	*/

	@Override
	protected GqlRequest getRequestSpec(HttpServletRequest req) throws ServletException, IOException {
		throw new RuntimeException("getRequestSpec(HttpServletRequest req) should not be used...");
		//return new GqlRequestSpec(dsutils, prop, req);
	}
	
	SchemaModel getSchemaModel(String modelId, HttpServletRequest req) {
		SchemaModel model = SchemaModelUtils.getModel(req.getServletContext(), modelId);
		if(model==null) {
			throw new InternalServerException("null model [modelId="+modelId+"]");
		}
		return model;
	}
	
	DataFetcher<?> getDataFetcher(SchemaModel sm, Map<String, QonAction> actionMap, Map<String, Map<String,String>> colMap, HttpServletRequest req, HttpServletResponse resp) {
		return new QonDataFetcher<>(sm, actionMap, colMap, this, req, resp);
	}
	
	protected Properties getProperties() {
		return prop;
	}
	
	@Override
	protected void doSelect(SchemaModel model, Relation relation, RequestSpec reqspec, Subject currentUser,
			HttpServletResponse resp, boolean validateQuery)
			throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		super.doSelect(model, relation, reqspec, currentUser, resp, validateQuery);
	}
	
	@Override
	protected void doInsert(Relation relation, RequestSpec reqspec, Subject currentUser, boolean isPermitted, HttpServletResponse resp)
			throws ClassNotFoundException, SQLException, NamingException, IOException {
		super.doInsert(relation, reqspec, currentUser, isPermitted, resp);
	}
	
	@Override
	protected void doUpdate(Relation relation, RequestSpec reqspec, Subject currentUser, boolean isPermitted, HttpServletResponse resp)
			throws ClassNotFoundException, SQLException, NamingException, IOException {
		super.doUpdate(relation, reqspec, currentUser, isPermitted, resp);
	}
	
	@Override
	protected void doDelete(Relation relation, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp)
			throws ClassNotFoundException, SQLException, NamingException, IOException, ServletException {
		super.doDelete(relation, reqspec, currentUser, resp);
	}
	
	@Override
	protected void doExecute(ExecutableObject eo, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp)
			throws ClassNotFoundException, SQLException, NamingException, IOException {
		super.doExecute(eo, reqspec, currentUser, resp);
	}
	
	@Override
	protected void writeUpdateCount(RequestSpec reqspec, HttpServletResponse resp, int count, String action) throws IOException {
		if(count!=1) {
			log.warn("update count != 1: "+count);
		}
		else {
			log.info(count+" "+(count>1?"rows":"row")+" "+action);
		}
		
		if(reqspec instanceof GqlRequest) {
			GqlRequest req = (GqlRequest) reqspec;
			req.updateCount = count;
		}
		else {
			log.warn("reqspec is not a GqlRequest: "+reqspec.getClass());
		}
	}

	@Override
	protected void writeExecuteOutput(RequestSpec reqspec, ExecutableObject eo, HttpServletResponse resp, String value) throws IOException {
		if(reqspec instanceof GqlRequest) {
			GqlRequest req = (GqlRequest) reqspec;
			req.executeOutput = value;
		}
		else {
			log.warn("reqspec is not a GqlRequest: "+reqspec.getClass());
		}
	}
	
	/*
	// copied from ODataServlet
	@Override
	protected void preprocessParameters(RequestSpec reqspec, Constraint pk) {
		Map<String, String> keymap = reqspec.keyValues;
		//log.debug("req: "+req+" keymap: "+keymap);
		if(keymap == null || keymap.size()==0 || keymap.size()==1 ||
				pk==null || pk.getUniqueColumns()==null) { return; }
		
		//List<Object> origPar = new ArrayList<Object>();
		//origPar.addAll(req.getParams());
		
		// ordering params by pk key cols order
		reqspec.getParams().clear();
		for(String col: pk.getUniqueColumns()) {
			String v = keymap.get(col);
			//log.debug("c: "+col+" ; v: "+v);
			reqspec.getParams().add(v);
		}
	}
	*/

	@Override
	public String getDefaultUrlMapping() {
		return "/graphql/*";
	}

}
