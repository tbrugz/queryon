package tbrugz.queryon.http;

import static tbrugz.queryon.http.JettySetup.qonUrl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
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
import tbrugz.sqldump.util.IOUtil;

public class ODataWebTest {

	static String workDir = "work/test/";
	static String utf8 = "UTF-8";
	static String odataUrl = qonUrl + "/odata";
	
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
		String[] params = {"-propfile=src_test/tbrugz/queryon/http/sqlrun.properties"};
		SQLRun.main(params);
	}

	//---------------------
	
	public static String getContent(HttpResponse response) throws IllegalStateException, IOException {
		HttpEntity entity = response.getEntity();
		if(entity==null) { return ""; }
		InputStream instream = entity.getContent();
		return IOUtil.readFile(new InputStreamReader(instream));
	}
	
	public static String getContentFromUrl(String url) throws ClientProtocolException, IOException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(url);
		HttpResponse response1 = httpclient.execute(httpGet);
		String content = getContent(response1);
		if(response1.getStatusLine().getStatusCode()>=400) {
			throw new RuntimeException(response1.getStatusLine().getStatusCode()+": "+content);
		}
		return content;
	}
	
	//---------------------
	
	@Test
	public void getRelations() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/relation";
		
		String jsonStr = getContentFromUrl(url);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		
		obj = jobj.get("@odata.context");
		Assert.assertTrue(obj.toString().endsWith("/$metadata#relation"));
		
		obj = jobj.get("value");
		Assert.assertTrue("Should be a JSONArray", obj instanceof JSONArray);
	}

	@Test
	public void getDepts() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/PUBLIC.DEPT";
		
		String jsonStr = getContentFromUrl(url);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		
		obj = jobj.get("@odata.context");
		Assert.assertTrue(obj.toString().endsWith("/$metadata#PUBLIC.DEPT"));
		
		obj = jobj.get("value");
		Assert.assertTrue("Should be a JSONArray", obj instanceof JSONArray);
		
		JSONArray jarr = (JSONArray) obj;
		Assert.assertEquals(3, jarr.size());
	}

	@Test
	public void getDeptsWithFilter() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/PUBLIC.DEPT?$filter=NAME+eq+'HR'";
		
		String jsonStr = getContentFromUrl(url);
		System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		
		obj = jobj.get("@odata.context");
		Assert.assertTrue(obj.toString().endsWith("/$metadata#PUBLIC.DEPT"));
		
		obj = jobj.get("value");
		Assert.assertTrue("Should be a JSONArray", obj instanceof JSONArray);
		
		JSONArray jarr = (JSONArray) obj;
		Assert.assertEquals(1, jarr.size());
	}
	
	@Test
	public void getDeptByKey() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/PUBLIC.DEPT(2)";
		
		String jsonStr = getContentFromUrl(url);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		
		obj = jobj.get("@odata.context");
		Assert.assertTrue(obj.toString().endsWith("/$metadata#PUBLIC.DEPT"));
		
		Object prop = jobj.get("ID");
		Assert.assertEquals(2L, prop);
		prop = jobj.get("NAME");
		Assert.assertEquals("Engineering", prop);
		prop = jobj.get("PARENT_ID");
		Assert.assertEquals(0L, prop);
	}

	@Test
	public void getPairByKey() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/PUBLIC.PAIR(ID1=1,ID2=3)";
		
		String jsonStr = getContentFromUrl(url);
		System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		
		obj = jobj.get("@odata.context");
		Assert.assertTrue(obj.toString().endsWith("/$metadata#PUBLIC.PAIR"));
		
		Object prop = jobj.get("ID1");
		Assert.assertEquals(1L, prop);
		prop = jobj.get("ID2");
		Assert.assertEquals(3L, prop);
		prop = jobj.get("REMARKS");
		Assert.assertEquals("some text", prop);
	}

	@Test
	public void getPairByKeyAnotherOrder() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/PUBLIC.PAIR(ID2=3,ID1=1)";
		
		String jsonStr = getContentFromUrl(url);
		System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		
		obj = jobj.get("@odata.context");
		Assert.assertTrue(obj.toString().endsWith("/$metadata#PUBLIC.PAIR"));
		
		Object prop = jobj.get("ID1");
		Assert.assertEquals(1L, prop);
		prop = jobj.get("ID2");
		Assert.assertEquals(3L, prop);
		prop = jobj.get("REMARKS");
		Assert.assertEquals("some text", prop);
	}
	
	@Test
	public void createEmp() throws Exception {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(odataUrl+"/EMP");
		String json = "{\"ID\": 10, \"NAME\": \"Bill\", \"SUPERVISOR_ID\": 1, \"DEPARTMENT_ID\": 1, \"SALARY\": 4000}";
		httpPost.setEntity(new StringEntity(json));
		
		HttpResponse response1 = httpclient.execute(httpPost);
		
		Assert.assertEquals(201, response1.getStatusLine().getStatusCode());
		httpPost.releaseConnection();
	}
	
	@Test
	public void updateEmp() throws Exception {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPatch httpPatch = new HttpPatch(odataUrl+"/EMP(5)");
		String json = "{\"SALARY\": 2500}";
		httpPatch.setEntity(new StringEntity(json));
		
		HttpResponse response1 = httpclient.execute(httpPatch);
		
		Assert.assertEquals(204, response1.getStatusLine().getStatusCode());
		String content = getContent(response1);
		Assert.assertEquals("", content);

		Header header = response1.getFirstHeader("X-UpdateCount");
		Assert.assertEquals("1", header.getValue());

		/*Header[] headers = response1.getAllHeaders();
		for(Header h: headers) {
			System.out.println(h.getName()+" / "+h.getValue());
		}*/
		
		httpPatch.releaseConnection();
	}

	@Test
	public void deleteEmp() throws Exception {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpDelete httpDel = new HttpDelete(odataUrl+"/EMP(3)");
		
		HttpResponse response1 = httpclient.execute(httpDel);
		
		Assert.assertEquals(204, response1.getStatusLine().getStatusCode());
		String content = getContent(response1);
		Assert.assertEquals("", content);

		Header header = response1.getFirstHeader("X-UpdateCount");
		Assert.assertEquals("1", header.getValue());

		/*Header[] headers = response1.getAllHeaders();
		for(Header h: headers) {
			System.out.println(h.getName()+" / "+h.getValue());
		}*/
		
		httpDel.releaseConnection();
	}
	
}
