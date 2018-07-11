package tbrugz.queryon.graphql;

import java.io.IOException;
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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.api.BaseApiServlet;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.graphql.GqlSchemaFactory.QonAction;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.util.IOUtil;

public class GraphQlQonServlet extends BaseApiServlet { // extends HttpServlet

	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(GraphQlQonServlet.class);
	
	static final String[] ACCEPTED_METHODS = {"GET", "POST"};
	
	//SchemaModel sm = null;
	//Map<String, QonAction> actionMap = null;
	
	@Override
	//protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
	protected void doService(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if(Arrays.binarySearch(ACCEPTED_METHODS, req.getMethod().toUpperCase())<0) {
			throw new BadRequestException("Method not accepted: "+req.getMethod());
		}
		log.info(">> pathInfo: "+req.getPathInfo()+" ; method: "+req.getMethod());
		
		/*String query = getGraphqlQuery(req);
		if(query==null || query.equals("")) {
			throw new BadRequestException("Query must not be null");
		}
		//{"query":"{ hn { topStories { id title url }}}","variables":"","operationName":null}
		//String variables = getGraphqlVariables(req);
		//String operationName = getGraphqlOperationName(req);
		//log.info(">> query: "+query);
		ExecutionInput.Builder execBuilder = ExecutionInput.newExecutionInput().query(query);
		ExecutionInput exec = execBuilder.build();*/
		ExecutionInput exec = getExecutionInput(req);
		
		String modelId = SchemaModelUtils.getModelId(req); //XXX: get modelId (also) from POST body (json)?
		SchemaModel sm = getSchemaModel(modelId, req);
		
		resp.setContentType(GqlMapBufferSyntax.MIME_TYPE);
		
		// XXX "cache" GqlSchemaFactory? application attribute... also: when to invalidate?
		GqlSchemaFactory gqls = new GqlSchemaFactory(sm);
		Map<String, QonAction> actionMap = gqls.amap;
		DataFetcher<?> df = getDataFetcher(sm, actionMap, req, resp);
		GraphQLSchema graphQLSchema = gqls.getSchema(df);
		GraphQL gql = GraphQL.newGraphQL(graphQLSchema).build();
		ExecutionResult executionResult = gql.execute(exec);
		
		// https://github.com/graphql-java/graphql-java/issues/649
		// https://github.com/graphql-java/graphql-java/blob/master/docs/execution.rst#serializing-results-to-json
		Map<String, Object> executionSpecResult = executionResult.toSpecification();
		Gson gson = new GsonBuilder()
				.create();
		resp.getWriter().write(gson.toJson(executionSpecResult));
		//writeResult(exec, executionResult, resp);
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
	ExecutionInput getExecutionInput(HttpServletRequest req) throws IOException {
		ExecutionInput.Builder execBuilder = ExecutionInput.newExecutionInput();
		if(req.getMethod().equals("GET")) {
			String query = req.getParameter("query");
			execBuilder.query(query);
		}
		else if(req.getMethod().equals("POST")) {
			//XXX test for Content-Type: application/json or application/graphql
			String httpBody = IOUtil.readFile(req.getReader()).trim();
			Gson gson = new Gson();
			Map<String, Object> map = (Map<String, Object>) gson.fromJson(httpBody, Map.class);
			
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
	
	DataFetcher<?> getDataFetcher(SchemaModel sm, Map<String, QonAction> actionMap, HttpServletRequest req, HttpServletResponse resp) {
		return new QonDataFetcher<>(sm, actionMap, this, req, resp);
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
	
}
