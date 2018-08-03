package tbrugz.queryon.http;

import static tbrugz.queryon.http.JettySetup.qonUrl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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

import tbrugz.sqldump.sqlrun.SQLRun;
import tbrugz.sqldump.util.IOUtil;
import tbrugz.sqldump.util.Utils;

public class GraphQlWebTest {

	public static final String basedir = "src/test/java";

	static final String graphqlUrl = qonUrl + "/graphql";
	
	@BeforeClass
	public static void setup() throws Exception {
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
	
	public static String getContent(String query, String variables, String operationName) throws ClientProtocolException, IOException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpReq = new HttpPost(graphqlUrl);
		List<String> qparts = new ArrayList<String>();
		if(query!=null) {
			qparts.add("\"query\": \""+query+"\"");
		}
		if(variables!=null) {
			qparts.add("\"variables\": \""+variables+"\"");
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
	
	//---------------------
	
	@Test
	public void getMetadata() throws IOException, ParserConfigurationException, SAXException {
		String query = "{ __schema { types { name } } }";
		String jsonStr = getContent(query);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		//JSONObject jobj = (JSONObject) obj;
	}

	@Test
	public void getEmps() throws IOException, ParserConfigurationException, SAXException {
		String query = "{ list_EMP { ID NAME SUPERVISOR_ID DEPARTMENT_ID SALARY }";
		String jsonStr = getContent(query, null, null);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
	}

}
