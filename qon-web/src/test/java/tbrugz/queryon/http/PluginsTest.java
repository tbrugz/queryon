package tbrugz.queryon.http;

import static tbrugz.queryon.http.JettySetup.baseUrl;
import static tbrugz.queryon.http.JettySetup.qonUrl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

import javax.naming.NamingException;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONArray;
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

public class PluginsTest {

	private static final Log log = LogFactory.getLog(PluginsTest.class);
	
	public static final String basedir = "src/test/java";

	static final String graphqlUrl = qonUrl + "/graphql";
	
	@BeforeClass
	public static void setup() throws Exception {
		setupDatabase();
		JettySetup.setupServer("/WEB-INF/web-plugins.xml");
	}
	
	@AfterClass
	public static void shutdown() throws Exception {
		JettySetup.shutdown();
	}

	@Before
	public void before() throws ClassNotFoundException, IOException, SQLException, NamingException {
	}
	
	@After
	public void after() {
	}
	
	public static void setupDatabase() throws ClassNotFoundException, IOException, SQLException, NamingException {
		String[] params = {"-propfile="+basedir+"/tbrugz/queryon/http/sqlrun.properties"};
		SQLRun.main(params);
	}

	public static int addQonQuery(String schema, String name, String query) throws IOException, ParserConfigurationException, SAXException {
		CloseableHttpClient httpclient = WinstoneAndH2HttpRequestTest.getHttpClient();
		HttpPost httpPost = new HttpPost(baseUrl+"/QUERYON.QON_QUERIES");
		
		List<NameValuePair> formParams = List.of(
			new BasicNameValuePair("v:SCHEMA_NAME", schema),
			new BasicNameValuePair("v:NAME", name),
			new BasicNameValuePair("v:QUERY", query)
		);
		httpPost.setEntity(new UrlEncodedFormEntity(formParams, StandardCharsets.UTF_8));
		
		HttpResponse response1 = httpclient.execute(httpPost);
		
		//Assert.assertEquals("Must be 201/Created", 201, response1.getStatusLine().getStatusCode());
		int ret = response1.getStatusLine().getStatusCode();
		httpPost.releaseConnection();
		return ret;
	}
	
	public static boolean containRowWithProperty(JSONArray jarr, String property, String value) {
		for(Object row: jarr) {
			Assert.assertTrue("Should be a JSONObject", row instanceof JSONObject);
			JSONObject jo = (JSONObject) row;
			log.info("row: "+jo.get(property) );
			if(value.equals(jo.get(property))) {
				return true;
			}
		}
		return false;
	}
	
	// -----

	@Test
	public void testAddAndQueryQonQueries() throws IOException, ParserConfigurationException, SAXException {
		int status = addQonQuery("TEST", "TEST1", "select ID, NAME from EMP");
		Assert.assertEquals("Must be 201/Created", 201, status);

		String url = "/relation.json";
		String jsonStr = WinstoneAndH2HttpRequestTest.getContentFromUrl(baseUrl+url);
		
		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		obj = jobj.get("data");
		Assert.assertTrue("Should be a JSONArray", obj instanceof JSONArray);

		JSONArray jarr = (JSONArray) obj;
		Assert.assertTrue("Should have Query named TEST1", containRowWithProperty(jarr, "name", "TEST1"));
	}

}
