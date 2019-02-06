package tbrugz.queryon.http;

import static tbrugz.queryon.http.JettySetup.qonUrl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.NamingException;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import graphql.language.Document;
import graphql.parser.Parser;
import tbrugz.sqldump.sqlrun.SQLRun;
import tbrugz.sqldump.util.IOUtil;
import tbrugz.sqldump.util.Utils;

public class GraphQlWebTest {

	public static final String basedir = "src/test/java";

	static final String graphqlUrl = qonUrl + "/graphql";
	
	@BeforeClass
	public static void setup() throws Exception {
		setupH2();
		JettySetup.setupServer();
	}
	
	@AfterClass
	public static void shutdown() throws Exception {
		JettySetup.shutdown();
	}

	@Before
	public void before() throws ClassNotFoundException, IOException, SQLException, NamingException {
		setupH2();
	}
	
	@After
	public void after() {
	}
	
	public static void setupH2() throws ClassNotFoundException, IOException, SQLException, NamingException {
		String[] params = {"-propfile="+basedir+"/tbrugz/queryon/http/sqlrun.properties"};
		SQLRun.main(params);
	}

	//---------------------
	
	public static String getContent(HttpResponse response) throws IllegalStateException, IOException {
		HttpEntity entity = response.getEntity();
		if(entity==null) { return ""; }
		InputStream instream = entity.getContent();
		return IOUtil.readFile(new InputStreamReader(instream));
	}
	
	public static String getContent(String query) throws ClientProtocolException, IOException {
		return getContent(query, null, null);
	}
	
	static String escapeJson(String str) {
		return str.replaceAll(Pattern.quote("\""), Matcher.quoteReplacement("\\\""));
	}
	
	public static String getContent(String query, String variables, String operationName) throws ClientProtocolException, IOException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpReq = new HttpPost(graphqlUrl);
		List<String> qparts = new ArrayList<String>();
		if(query!=null) {
			qparts.add("\"query\": \""+escapeJson(query)+"\"");
		}
		if(variables!=null) {
			qparts.add("\"variables\": "+variables+"");
		}
		if(operationName!=null) {
			qparts.add("\"operationName\": \""+operationName+"\"");
		}
		String body = "{ "+Utils.join(qparts, ",")+" }";
		//System.out.println("body: "+body);
		httpReq.setEntity(new StringEntity(body));
		HttpResponse response1 = httpclient.execute(httpReq);
		String content = getContent(response1);
		httpReq.releaseConnection();
		if(response1.getStatusLine().getStatusCode()>=400) {
			//System.out.println("content:: "+content);
			throw new RuntimeException(response1.getStatusLine().getStatusCode()+": "+content);
		}
		return content;
	}
	
	static void assertGraphqlOk(String jsonStr) {
		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		JSONObject o = (JSONObject) obj;
		Assert.assertTrue("'data' should be a JSONObject", o.get("data") instanceof JSONObject);
		Assert.assertNull("'errors' should be null", o.get("errors"));
	}
	
	static void assertGraphqlErrors(String jsonStr) {
		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		JSONObject o = (JSONObject) obj;
		Assert.assertNotNull("'errors' should NOT be null", o.get("errors"));
	}
	
	//---------------------
	
	@Test
	public void getMetadata() throws IOException, ParserConfigurationException, SAXException {
		String query = "{ __schema { types { name } } }";
		String jsonStr = getContent(query);
		//System.out.println("content:\n"+jsonStr);

		assertGraphqlOk(jsonStr);
	}

	@Test
	public void getEmps() throws IOException, ParserConfigurationException, SAXException {
		String query = "{ list_EMP { ID NAME SUPERVISOR_ID DEPARTMENT_ID SALARY }}";
		String jsonStr = getContent(query, null, null);
		//System.out.println("content:\n"+jsonStr);

		assertGraphqlOk(jsonStr);
	}

	@Test
	public void getEmpsAndDepts() throws IOException, ParserConfigurationException, SAXException {
		String query = "{ e1: list_EMP { ID NAME SUPERVISOR_ID DEPARTMENT_ID SALARY }"+
				"d1: list_DEPT {NAME} }";
		String jsonStr = getContent(query, null, null);
		//System.out.println("content:\n"+jsonStr);

		assertGraphqlOk(jsonStr);
	}
	
	@Test
	public void getEmpsWithFilter() throws IOException, ParserConfigurationException, SAXException {
		String query = "{ list_EMP { ID NAME SUPERVISOR_ID(feq: 1) DEPARTMENT_ID SALARY }}";
		String jsonStr = getContent(query, null, null);
		//System.out.println("content:\n"+jsonStr);

		assertGraphqlOk(jsonStr);
	}
	
	@Test
	public void getEmpsWithVariable() throws IOException, ParserConfigurationException, SAXException {
		String query = "query($supId: Int) { list_EMP { ID NAME SUPERVISOR_ID(feq: $supId) DEPARTMENT_ID SALARY }}";
		String variables = "{ \"supId\": 1 }";
		String jsonStr = getContent(query, variables, null);
		//System.out.println("content:\n"+jsonStr);

		assertGraphqlOk(jsonStr);
	}

	@Test
	public void getEmpsOrDeptsWithOperationName() throws IOException, ParserConfigurationException, SAXException {
		String query = "query e1 { list_EMP { ID NAME SUPERVISOR_ID DEPARTMENT_ID SALARY } }\n"+
				"query d1 { list_DEPT { NAME } }";
		String jsonStr = getContent(query, null, "e1");
		//System.out.println("content:\n"+jsonStr);

		assertGraphqlOk(jsonStr);
	}
	
	@Test
	public void executeIsPrime() throws ClientProtocolException, IOException {
		String query = "mutation { execute_IS_PRIME(p1: 181) { returnValue } }";
		String jsonStr = getContent(query, null, null);
		//System.out.println("content:\n"+jsonStr);

		assertGraphqlOk(jsonStr);
	}

	@Test
	public void executeIsPrimeError() throws ClientProtocolException, IOException {
		String query = "mutation { execute_IS_PRIME(p1: \"asdasd\") { returnValue } }";
		String jsonStr = getContent(query, null, null);
		//System.out.println("content:\n"+jsonStr);

		assertGraphqlErrors(jsonStr);
	}
	
	@SuppressWarnings("unused")
	@Test
	public void requestSchema() throws ClientProtocolException, IOException {
		String str = ODataWebTest.getContentFromUrl(graphqlUrl+"?schema=true");
		//System.out.println("schema: "+str);
		Parser parser = new Parser();
		Document doc = parser.parseDocument(str);
		//System.out.println("doc.getChildren().size(): "+doc.getChildren().size());
	}
	
	@Test
	public void getNamedParams1WithPositionals() throws IOException, ParserConfigurationException, SAXException {
		//String query = "{ list_NAMED_PARAMS_1(p1: \"1\", p2: \"2\", p3: \"3\") { C1 }}";
		String query = "{ list_NAMED_PARAMS_1(par1: \"1\", par2: \"2\") { C1 }}";
		String jsonStr = getContent(query, null, null);
		//System.out.println("content:\n"+jsonStr);
		assertGraphqlOk(jsonStr);
	}
	
	@Test
	public void getQueryWithNamedParams() throws IOException, ParserConfigurationException, SAXException {
		String query = "{ list_QUERY_WITH_PARAMS_NULL_BIND(par1: \"1\", par2: \"2\") { C1 }}";
		String jsonStr = getContent(query, null, null);
		//System.out.println("content:\n"+jsonStr);
		assertGraphqlOk(jsonStr);
	}
	
}
