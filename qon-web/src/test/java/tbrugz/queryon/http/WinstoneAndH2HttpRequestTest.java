package tbrugz.queryon.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.naming.NamingException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import tbrugz.queryon.ResponseSpec;
import tbrugz.sqldump.sqlrun.SQLRun;
import tbrugz.sqldump.util.IOUtil;
import tbrugz.sqldump.util.Utils;

import static tbrugz.queryon.http.JettySetup.*;

public class WinstoneAndH2HttpRequestTest {

	private static final Log log = LogFactory.getLog(WinstoneAndH2HttpRequestTest.class);
	
	public static final String basedir = "src/test/java";
	public static final String webappdir = "src/main/webapp";
	//public static final String testResourcesDir = "src/test/resources";
	
	static DocumentBuilderFactory dbFactory;
	static DocumentBuilder dBuilder;
	//static String workDir = "work/test/";
	static final String utf8 = "UTF-8";
	
	static int relationsInModel = 3;
	
	static final String LF = "\r\n";
	
	@BeforeClass
	public static void setup() throws Exception {
		//TestSetup.setupWinstone();
		setupH2();
		JettySetup.setupServer();
		//System.out.println(">> user.dir: "+System.getProperty("user.dir"));
		
		dbFactory = DocumentBuilderFactory.newInstance();
		dBuilder = dbFactory.newDocumentBuilder();
	}
	
	public static void setupH2() throws ClassNotFoundException, IOException, SQLException, NamingException {
		String[] params = {"-propfile="+basedir+"/tbrugz/queryon/http/sqlrun.properties"};
		SQLRun.main(params);
	}
	
	@AfterClass
	public static void shutdown() throws Exception {
		JettySetup.shutdown();
		//TestSetup.shutdown();
	}

	@Before
	public void before() throws ClassNotFoundException, IOException, SQLException, NamingException {
		setupH2();
	}
	
	@After
	public void after() {
	}
	
	public static String getContent(HttpResponse response) throws IllegalStateException, IOException {
		HttpEntity entity = response.getEntity();
		InputStream instream = entity.getContent();
		return IOUtil.readFromReader(new InputStreamReader(instream));
	}

	public static String getContentFromUrl(String url) throws ClientProtocolException, IOException {
		return getContentFromUrl(url, 200);
	}
	
	public static String getContentFromUrl(String url, int expectedStatus) throws ClientProtocolException, IOException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(url);
		HttpResponse response1 = httpclient.execute(httpGet);
		String content = getContent(response1);
		Assert.assertEquals(expectedStatus, response1.getStatusLine().getStatusCode());
		//Assert.assertThat(response1.getStatusLine().getStatusCode(), Matchers.lessThanOrEqualTo(299));
		/*if(response1.getStatusLine().getStatusCode()>=400) {
			throw new RuntimeException(response1.getStatusLine().getStatusCode()+": "+content);
		}*/
		return content;
	}
	
	public static List<Node> getElementChildNodes(Node node) {
		List<Node> nodes = new ArrayList<Node>();
		for(int i=0;i<node.getChildNodes().getLength();i++) {
			Node n = node.getChildNodes().item(i);
			if(n instanceof Element) {
				nodes.add(n);
			}
		}
		return nodes;
	}

	/*
	 * see: http://hc.apache.org/httpcomponents-client-ga/quickstart.html
	 */
	@Test
	public void testGet01_OK() throws IOException {
		System.out.println("user.dir = "+System.getProperty("user.dir"));
		//URL url = new URL("http://localhost:8889/table");
		//String s = (String) url.getContent();
		//url.openConnection().
		
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/table");
		
		HttpResponse response1 = httpclient.execute(httpGet);
		
		try {
			System.out.println(response1.getStatusLine());
			Assert.assertEquals("Must be OK", 200, response1.getStatusLine().getStatusCode());
			HttpEntity entity1 = response1.getEntity();
			EntityUtils.consume(entity1);
		} finally {
			httpGet.releaseConnection();
		}

	}

	@Test
	public void testGet02_404() throws IOException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/fks");
		
		HttpResponse response1 = httpclient.execute(httpGet);
		try {
			Assert.assertEquals("Must be Not Found", 404, response1.getStatusLine().getStatusCode());
		} finally {
			httpGet.releaseConnection();
		}
	}
	
	@Test
	public void testGet_HTML_Tables() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/table");
		
		HttpResponse response1 = httpclient.execute(httpGet);
		
		try {
			HttpEntity entity1 = response1.getEntity();
			InputStream instream = entity1.getContent();
			Document doc = dBuilder.parse(instream);
			//NodeList nl = doc.getElementsByTagName("tr");
			Node tableNode = doc.getElementsByTagName("table").item(0);
			Assert.assertEquals("Should be 4 rows (3 data rows (db tables) + 1 header)", relationsInModel+1, getElementChildNodes(tableNode).size());
			/*for (int i = 0; i < nl.getLength(); i++) {
				Node nNode = nl.item(i);
				Element eElement = (Element) nNode;
			}*/
			
			EntityUtils.consume(entity1);
		} finally {
			httpGet.releaseConnection();
		}
	}

	@Test
	public void testGet_HTML_FKs() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/fk");
		
		HttpResponse response1 = httpclient.execute(httpGet);
		
		try {
			HttpEntity entity1 = response1.getEntity();
			Assert.assertEquals(200, response1.getStatusLine().getStatusCode());
			//System.out.println("content: "+getContent(response1));
			InputStream instream = entity1.getContent();
			Document doc = dBuilder.parse(instream);
			//NodeList nl = doc.getElementsByTagName("tr");
			Node tableNode = doc.getElementsByTagName("table").item(0);
			Assert.assertEquals("Should be 3 rows (2 data rows (FKs) + 1 header)", 3, getElementChildNodes(tableNode).size());
			
			EntityUtils.consume(entity1);
		} finally {
			httpGet.releaseConnection();
		}
	}

	@Test
	@Ignore("maybe later")
	public void testPost_Emp_400() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(baseUrl+"/EMP?v:ID=11");
		
		HttpResponse response1 = httpclient.execute(httpPost);
		
		Assert.assertEquals("Should be a Bad Request (NAME value not provided - is 'not null')", 400, response1.getStatusLine().getStatusCode());
		httpPost.releaseConnection();
	}

	@Test
	public void testPost_Emp_400b() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(baseUrl+"/EMP");
		
		HttpResponse response1 = httpclient.execute(httpPost);
		
		Assert.assertEquals("Must be a Bad Request (no columns provided)", 400, response1.getStatusLine().getStatusCode());
		httpPost.releaseConnection();
	}
	
	@Test
	public void testPost_Emp_Created() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(baseUrl+"/EMP?v:ID=11&v:NAME=sonya");
		
		HttpResponse response1 = httpclient.execute(httpPost);
		
		Assert.assertEquals("Must be Created (201)", 201, response1.getStatusLine().getStatusCode());
		httpPost.releaseConnection();
	}
	
	@Test
	public void testPost_Emp_Error() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(baseUrl+"/EMP?v:ID=1&v:NAME=sonya");
		
		HttpResponse response1 = httpclient.execute(httpPost);
		
		Assert.assertEquals("Must be Internal Error (PK violated)", 500, response1.getStatusLine().getStatusCode());
		httpPost.releaseConnection();
	}

	@Test
	public void testPost_Emp_KeyOnPath_Created() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(baseUrl+"/EMP/11?v:NAME=sonya");
		
		HttpResponse response1 = httpclient.execute(httpPost);
		
		Assert.assertEquals("Must be Created (201)", 201, response1.getStatusLine().getStatusCode());
		httpPost.releaseConnection();
	}
	
	/*
	 * TODO: HttpPut isn't working with winstone
	 * http://code.google.com/p/winstone/source/browse/trunk/winstone/src/main/java/net/winstone/core/listener/HttpListener.java
	 */
	@Test
	public void testPatch_Emp_OK() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		//HttpGet httpPut = new HttpGet(baseUrl+"/EMP?v:NAME=newname&feq:ID=1&method=PATCH");
		HttpPatch httpPut = new HttpPatch(baseUrl+"/EMP/1?v:NAME=newname");
		//HttpPut httpPut = new HttpPut(baseUrl+"/EMP/1?v:NAME=newname&method=PATCH");
		//HttpPut httpPut = new HttpPut(baseUrl+"/EMP/1");
		//HttpPost httpPut = new HttpPost(baseUrl+"/EMP/1");

		//List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		//nvps.add(new BasicNameValuePair("method", "PATCH"));
		//nvps.add(new BasicNameValuePair("v:NAME", "newname"));
		//httpPut.setEntity(new UrlEncodedFormEntity(nvps));
		
		//System.out.println("PATCH-uri: "+httpPut.getRequestLine());
		//System.out.println(EntityUtils.toString(new UrlEncodedFormEntity(nvps)));		
		
		HttpResponse response1 = httpclient.execute(httpPut);
		//System.out.println("content: "+getContent(response1));

		Assert.assertEquals("Must be OK (updated)", 200, response1.getStatusLine().getStatusCode());
		httpPut.releaseConnection();
	}

	@Test
	public void testPatchEmpKeyVals() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPatch httpPut = new HttpPatch(baseUrl+"/EMP?k:ID=1&v:NAME=newname");
		
		HttpResponse response1 = httpclient.execute(httpPut);
		//System.out.println("content: "+getContent(response1));

		Assert.assertEquals("Must be OK (updated)", 200, response1.getStatusLine().getStatusCode());
		httpPut.releaseConnection();
	}

	@Test
	public void testPatchPairKeyVals() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPatch httpPut = new HttpPatch(baseUrl+"/PAIR?k:ID2=3&k:ID1=1&v:REMARKS=another+text");
		
		HttpResponse response1 = httpclient.execute(httpPut);

		Assert.assertEquals("Must be OK (updated)", 200, response1.getStatusLine().getStatusCode());
		httpPut.releaseConnection();
	}
	
	@Test
	public void testPatch_Emp_Error() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpPut = new HttpGet(baseUrl+"/EMP/1?_method=PATCH");
		
		HttpResponse response1 = httpclient.execute(httpPut);
		
		Assert.assertEquals("Must be Bad Request (no update columns informed)", 400, response1.getStatusLine().getStatusCode());
		httpPut.releaseConnection();
	}

	@Test
	public void testPatchEmpKeyVals_ByPath() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPatch httpPut = new HttpPatch(baseUrl+"/EMP/1?v:NAME=newname");
		
		HttpResponse response1 = httpclient.execute(httpPut);
		//System.out.println("content: "+getContent(response1));
		Header hUpdate = response1.getFirstHeader(ResponseSpec.HEADER_UPDATECOUNT);
		//System.out.println("header "+ResponseSpec.HEADER_UPDATECOUNT+": "+hUpdate.getValue());
		
		Assert.assertEquals("Must be OK (updated)", 200, response1.getStatusLine().getStatusCode());
		Assert.assertEquals("Must be 1 updated row", "1", hUpdate.getValue());
		httpPut.releaseConnection();
	}

	@Test
	public void testPatchDeptForbidden() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPatch httpPut = new HttpPatch(baseUrl+"/DEPT?k:ID=1&v:PARENT_ID=2");
		
		HttpResponse response1 = httpclient.execute(httpPut);
		
		Assert.assertEquals("Must be Forbidden (updated)", 403, response1.getStatusLine().getStatusCode());
		httpPut.releaseConnection();
	}
	
	@Test
	public void testDelete_Emp_Ok() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();

		HttpDelete httpGet = new HttpDelete(baseUrl+"/EMP/5"); // ?_method=DELETE
		HttpResponse response1 = httpclient.execute(httpGet);
		//System.out.println("content: "+getContent(response1));
		Assert.assertEquals("Must be OK", 200, response1.getStatusLine().getStatusCode());
		httpGet.releaseConnection();

		HttpDelete httpDelete = new HttpDelete(baseUrl+"/EMP/4");
		HttpResponse response2 = httpclient.execute(httpDelete);
		//System.out.println("content: "+getContent(response2));
		Assert.assertEquals("Must be OK", 200, response2.getStatusLine().getStatusCode());
		httpDelete.releaseConnection();
	}

	@Test
	public void testDelete_Emp_404() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpDelete httpDelete = new HttpDelete(baseUrl+"/EMP/7");
		HttpResponse response2 = httpclient.execute(httpDelete);
		//System.out.println("content: "+getContent(response2));
		Assert.assertEquals("Must be Not Found (no rows deleted)?", 404, response2.getStatusLine().getStatusCode());
		httpDelete.releaseConnection();
	}

	@Test
	public void testDelete_Emp_3rows() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		//XXX: delete doesn't get parameters from querystring?
		HttpDelete httpDelete = new HttpDelete(baseUrl+"/EMP?feq:DEPARTMENT_ID=2&updatemax=5");
		HttpResponse response2 = httpclient.execute(httpDelete);
		String content = getContent(response2);
		//System.out.println("content: "+content);
		Assert.assertEquals("Must be OK", 200, response2.getStatusLine().getStatusCode());
		//XXX: response may change...
		int rows = Integer.parseInt(content.split(" ")[0]);
		Assert.assertEquals("Must have 3 rows deleted", 3, rows);
		httpDelete.releaseConnection();
	}

	@Test
	public void testGet_XML_Tables() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/table.xml");
		
		HttpResponse response1 = httpclient.execute(httpGet);
		//String content = getContent(response1);
		//System.out.println("content: "+content);
		HttpEntity entity1 = response1.getEntity();
		InputStream instream = entity1.getContent();
		Document doc = dBuilder.parse(instream);
		Node tableNode = doc.getElementsByTagName("table").item(0);
		//System.out.println("count == "+getElementChildNodes(tableNode).size());
		//NodeList nl = doc.getElementsByTagName("row");
		Assert.assertEquals("Should have "+relationsInModel+" (data) rows", relationsInModel, getElementChildNodes(tableNode).size());
		
		EntityUtils.consume(entity1);
		httpGet.releaseConnection();
	}

	@Test
	public void testGet_JSON_Tables() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/table.json");
		
		HttpResponse response1 = httpclient.execute(httpGet);
		String jsonStr = getContent(response1);

		Object obj = JSONValue.parse(jsonStr);
		//System.out.println("json: "+obj.getClass()+" // "+obj);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		obj = jobj.get("data");
		Assert.assertTrue("Should be a JSONArray", obj instanceof JSONArray);

		JSONArray jarr = (JSONArray) obj;
		Assert.assertEquals("Should have "+relationsInModel+" (data) rows", relationsInModel, jarr.size());
		
		httpGet.releaseConnection();
	}
	
	@SuppressWarnings("unused")
	@Test
	public void testGet_JGSON_Tables_PrettyPrint() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/table.json");
		HttpResponse response1 = httpclient.execute(httpGet);
		String jsonStr = getContent(response1);
		//System.out.print("json-original:\n"+jsonStr+"\n");
		
		Gson gsonpretty = new GsonBuilder().setPrettyPrinting().create();
		Object jsonObj = gsonpretty.fromJson(jsonStr, Object.class);
		String jsonOutput = gsonpretty.toJson(jsonObj);
		//System.out.print("json-pretty:\n"+jsonOutput+"\n");

		Gson gson = new Gson();
		jsonObj = gson.fromJson(jsonStr, Object.class);
		jsonOutput = gson.toJson(jsonObj);
		//System.out.print("json-compact:\n"+jsonOutput+"\n");
	}

	@Test
	public void testGet_CSV_Tables() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/table.csv");
		
		HttpResponse response1 = httpclient.execute(httpGet);
		HttpEntity entity1 = response1.getEntity();
		Reader in = new InputStreamReader(entity1.getContent());
		
		//int count = 0;
		CSVFormat format = CSVFormat.DEFAULT; // CSVFormat.newBuilder(CSVFormat.DEFAULT).withHeader().build();
		CSVParser parser = new CSVParser(in, format);
		System.out.println("headers: "+parser.getHeaderMap()); //is null
		Iterator<CSVRecord> it = parser.iterator();
		
		Assert.assertTrue("Must have 0ed (header) element", it.hasNext());
		CSVRecord record = it.next();
		String value = record.get(1);
		System.out.println("0ed record (name = '"+value+"'): "+record);
		Assert.assertEquals("0ed record' 1st col must be 'name'", "name", value);

		Assert.assertTrue("Must have 1st element", it.hasNext());
		record = it.next();
		value = record.get(1);
		System.out.println("1st record (1st col = '"+value+"'): "+record);
		Assert.assertEquals("1st record' 1st col name must be DEPT", "DEPT", value);
		
		Assert.assertTrue("Must have 2nd element", it.hasNext());
		record = it.next();
		value = record.get(1);
		System.out.println("2nd record (1st col = '"+value+"'): "+record);
		Assert.assertEquals("2st record' 1st col must be EMP", "EMP", value);
		
		Assert.assertTrue("Must have 3rd element", it.hasNext());
		it.next();
		Assert.assertFalse("Must not have 4th element", it.hasNext());
		
		EntityUtils.consume(entity1);
		httpGet.releaseConnection();
		parser.close();
	}

	/*for (CSVRecord record: format.parse(in)) {
		/*for (String field : record) {
			System.out.print("\"" + field + "\", ");
		}
		System.out.println();* /
		System.out.println("record: "+record);
		count++;
	}
	
	Assert.assertEquals("Should have 2 (data) rows", 2, count);*/

	static void basePostReturnCountTest(String url, int expectedReturnRows) throws IOException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpGet = new HttpPost(baseUrl+url);
		
		HttpResponse response1 = httpclient.execute(httpGet);
		Assert.assertEquals(200, response1.getStatusLine().getStatusCode());
		//String resp = getContent(response1); System.out.println(resp);
		
		HttpEntity entity1 = response1.getEntity();
		InputStream instream = entity1.getContent();
		Document doc = dBuilder.parse(instream);
		NodeList nl = doc.getElementsByTagName("row");
		
		int length = nl.getLength();
		//System.out.println("nrows: "+nl.getLength());
		EntityUtils.consume(entity1);
		httpGet.releaseConnection();
		Assert.assertEquals(expectedReturnRows, length);
	}
	
	static HttpResponse getHttpResponse(String url) throws IOException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+url);
		return httpclient.execute(httpGet);
	}
	
	static void baseReturnCountTest(String url, int expectedReturnRows) throws IOException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+url);
		
		HttpResponse response1 = httpclient.execute(httpGet);
		Assert.assertEquals(200, response1.getStatusLine().getStatusCode());
		//String resp = getContent(response1); System.out.println(resp);
		
		HttpEntity entity1 = response1.getEntity();
		InputStream instream = entity1.getContent();
		Document doc = dBuilder.parse(instream);
		NodeList nl = doc.getElementsByTagName("row");
		
		int length = nl.getLength();
		//System.out.println("nrows: "+nl.getLength());
		EntityUtils.consume(entity1);
		httpGet.releaseConnection();
		Assert.assertEquals(expectedReturnRows, length);
	}

	static void baseReturnCodeTest(String url, int expectedStatusCode) throws IOException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+url);
		
		HttpResponse response1 = httpclient.execute(httpGet);
		int code = response1.getStatusLine().getStatusCode();
		httpGet.releaseConnection();
		
		Assert.assertEquals(expectedStatusCode, code);
	}
	
	static Document getXmlDocument(String url) throws IOException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+url);
		
		HttpResponse response1 = httpclient.execute(httpGet);
		Assert.assertEquals(200, response1.getStatusLine().getStatusCode());
		//String resp = getContent(response1); System.out.println(resp);
		
		HttpEntity entity1 = response1.getEntity();
		InputStream instream = entity1.getContent();
		return dBuilder.parse(instream);
	}

	static Document getPostXmlDocument(String url) throws IOException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpGet = new HttpPost(baseUrl+url);
		
		HttpResponse response1 = httpclient.execute(httpGet);
		Assert.assertEquals(200, response1.getStatusLine().getStatusCode());
		//String resp = getContent(response1); System.out.println(resp);
		
		HttpEntity entity1 = response1.getEntity();
		InputStream instream = entity1.getContent();
		return dBuilder.parse(instream);
	}
	
	static String httpGetContent(String url) throws IllegalStateException, IOException {
		return httpGetContent(url, 200);
	}
	
	static String httpGetContent(String url, int expectedStatus) throws IllegalStateException, IOException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet http = new HttpGet(baseUrl+url);
		HttpResponse response = httpclient.execute(http);
		Assert.assertEquals(expectedStatus, response.getStatusLine().getStatusCode());
		return getContent(response);
	}
	
	static String httpPostContent(String url, String content) throws IllegalStateException, IOException {
		return httpPostContent(url, content, 200);
	}
	
	static String httpPostContent(String url, String content, int expectedStatus) throws IllegalStateException, IOException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost http = new HttpPost(baseUrl+url);
		http.setEntity(new StringEntity(content));
		HttpResponse response = httpclient.execute(http);
		String ret = getContent(response);
		//System.out.println(ret);
		Assert.assertEquals(expectedStatus, response.getStatusLine().getStatusCode());
		return ret;
	}

	static String httpPatchContent(String url, String content, int expectedStatus) throws IllegalStateException, IOException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPatch http = new HttpPatch(baseUrl+url);
		http.setEntity(new StringEntity(content));
		HttpResponse response = httpclient.execute(http);
		String ret = getContent(response);
		//System.out.println(ret);
		Assert.assertEquals(expectedStatus, response.getStatusLine().getStatusCode());
		return ret;
	}
	
	@Test
	public void testGetXmlEmp() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml", 5);
	}

	@Test
	public void testGetXmlEmpFilterEq() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml?feq:SUPERVISOR_ID=1", 2);
	}

	@Test
	public void testGetXmlEmpFilterNe() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml?fne:SUPERVISOR_ID=1", 3);
	}
	
	@Test
	public void testGetXmlEmpFilterGt() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml?fgt:SALARY=1200", 2);
	}
	
	@Test
	public void testGetXmlEmpFilterGe() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml?fge:SALARY=1200", 3);
	}
	
	@Test
	public void testGetXmlEmpFilterLt() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml?flt:SALARY=1000", 0);
	}
	
	@Test
	public void testGetXmlEmpFilterLe() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml?fle:SALARY=1000", 2);
	}
	
	@Test
	public void testGetXmlEmpFilterIn() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml?fin:SUPERVISOR_ID=1", 2);
	}

	@Test
	public void testGetXmlEmpFilterIn2() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml?fin:SALARY=1200&fin:SALARY=1000", 3);
	}

	@Test
	public void testGetXmlEmpFilterLike() throws IOException, ParserConfigurationException, SAXException {
		// '%25' == '%' in urls
		baseReturnCountTest("/EMP.xml?flk:NAME=j%25", 2);
	}

	@Test
	public void testGetXmlEmpFilterLike2() throws IOException, ParserConfigurationException, SAXException {
		// '%25' == '%' in urls
		baseReturnCountTest("/EMP.xml?flk:NAME=j%25&flk:NAME=%25e", 1);
	}
	
	@Test
	public void testGetXmlEmpFilterNotLike() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml?fnlk:NAME=%25a%25", 2);
	}
	
	@Test
	public void testGetXmlEmpFilterNotIn() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml?fnin:SALARY=1200", 4);
	}
	
	@Test
	public void testGetXmlEmpFilterNull() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml?fnull:NAME", 0);
	}

	@Test
	public void testGetXmlEmpFilterNotNull() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml?fnotnull:NAME", 5);
	}

	@Test
	public void testGetXmlEmpFilterNotIn2() throws IOException, ParserConfigurationException, SAXException {
		baseReturnCountTest("/EMP.xml?fnin:SALARY=1200&fnin:SALARY=1000", 2);
	}
	
	//----- limit-related tests

	String getSql30rows() throws UnsupportedEncodingException {
		List<String> select = new ArrayList<String>();
		for(int i=0;i<30;i++) {
			select.add("select "+i+" as n");
		}
		String sql = Utils.join(select, " union all\n");
		//System.out.println("sql: "+sql);
		return sql;
	}
	
	@Test
	public void testGetXmlSelectAny10() throws IOException, ParserConfigurationException, SAXException {
		// limited by "queryon.limit.default=10"
		String sql = getSql30rows();
		String sqlpar = URLEncoder.encode(sql, utf8);
		basePostReturnCountTest("/QueryAny.xml?name=test&sql="+sqlpar, 10);
	}

	@Test
	public void testGetXmlSelectAnyMethodSwitch() throws IOException, ParserConfigurationException, SAXException {
		String sql = getSql30rows();
		String sqlpar = URLEncoder.encode(sql, utf8);
		HttpResponse resp = getHttpResponse("/QueryAny.xml?_method=POSTname=test&sql="+sqlpar);
		Assert.assertEquals(400, resp.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetXmlSelectAny20() throws IOException, ParserConfigurationException, SAXException {
		// limited by "queryon.limit.max=20"
		String sql = getSql30rows();
		String sqlpar = URLEncoder.encode(sql, utf8);
		basePostReturnCountTest("/QueryAny.xml?name=test&sql="+sqlpar+"&limit=25", 20);
	}
	
	@Test
	public void testGetXmlSelectAnyLimitDefault() throws IOException, ParserConfigurationException, SAXException {
		// limited by "limit-default=5"
		String sql = getSql30rows() + "/* limit-default=5 */";
		String sqlpar = URLEncoder.encode(sql, utf8);
		basePostReturnCountTest("/QueryAny.xml?name=test&sql="+sqlpar, 5);
	}
	
	@Test
	public void testGetXmlSelectAnyLimitDefault9() throws IOException, ParserConfigurationException, SAXException {
		// limited by "&limit=9"
		String sql = getSql30rows() + "/* limit-default=5 */";
		String sqlpar = URLEncoder.encode(sql, utf8);
		basePostReturnCountTest("/QueryAny.xml?name=test&sql="+sqlpar+"&limit=9", 9);
	}
	
	@Test
	public void testGetXmlSelectAnyLimitMax() throws IOException, ParserConfigurationException, SAXException {
		String sql = getSql30rows() + "/* limit-max=5 */";
		String sqlpar = URLEncoder.encode(sql, utf8);
		basePostReturnCountTest("/QueryAny.xml?name=test&sql="+sqlpar, 5);
	}
	
	@Test
	public void testGetXmlSelectAnyLimitMax9() throws IOException, ParserConfigurationException, SAXException {
		String sql = getSql30rows() + "/* limit-max=5 */";
		String sqlpar = URLEncoder.encode(sql, utf8);
		basePostReturnCountTest("/QueryAny.xml?name=test&sql="+sqlpar+"&limit=9", 5);
	}
	
	@Test
	public void testGetXmlSelectAnyLimitMax20() throws IOException, ParserConfigurationException, SAXException {
		// limited by "queryon.limit.max=20"
		String sql = getSql30rows() + "/* limit-max=25 */";
		String sqlpar = URLEncoder.encode(sql, utf8);
		basePostReturnCountTest("/QueryAny.xml?name=test&sql="+sqlpar+"&limit=50", 20);
	}

	//----- limit-related tests - end

	@Test
	public void testQueryAnyWrongMethod() throws IOException, ParserConfigurationException, SAXException {
		String sql = "select * from emp";
		String sqlpar = URLEncoder.encode(sql, utf8);
		baseReturnCodeTest("/QueryAny.xml?name=test&sql="+sqlpar, 400);
	}

	@Test
	public void testExplainPlan() throws IOException, ParserConfigurationException, SAXException {
		String sql = "select * from emp";
		String sqlpar = URLEncoder.encode(sql, utf8);
		basePostReturnCountTest("/ExplainAny.xml?name=test&sql="+sqlpar, 1);
	}
	
	@Test
	public void testExplainPlanWithParam() throws IOException, ParserConfigurationException, SAXException {
		String sql = "select * from emp where id = ?";
		String sqlpar = URLEncoder.encode(sql, utf8);
		basePostReturnCountTest("/ExplainAny.xml?name=test&sql="+sqlpar+"&p1=1", 1);
	}
	
	@Test
	public void testGetHtmlTitleEmp() throws IOException, ParserConfigurationException, SAXException, TransformerException {
		Document doc = getXmlDocument("/EMP.html?fnin:SALARY=1200&fnin:SALARY=1000&title=true");
		//String str = getString(doc);
		//System.out.println(">>>\n"+str);
		NodeList nl = doc.getElementsByTagName("caption");
		Assert.assertEquals(1, countNodesWithParentTagName(nl, "table"));

		doc = getXmlDocument("/EMP.html?fnin:SALARY=1200&fnin:SALARY=1000");
		nl = doc.getElementsByTagName("caption");
		Assert.assertEquals(0, countNodesWithParentTagName(nl, "table"));
	}
	
	@Test
	public void testProcessorJaxbSer() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(qonUrl+"/processor/JAXBSchemaXMLSerializer");
		
		HttpResponse response1 = httpclient.execute(httpGet);
		////String resp = getContent(response1); System.out.println(resp);
		
		HttpEntity entity1 = response1.getEntity();
		InputStream instream = entity1.getContent();
		Document doc = dBuilder.parse(instream);
		NodeList nl = doc.getElementsByTagName("table");
		Assert.assertEquals(relationsInModel, countNodesWithParentTagName(nl, "schemaModel"));
	}

	@Test
	public void testDelete_Dept_Forbidden() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpDelete httpDelete = new HttpDelete(baseUrl+"/DEPT/1");
		HttpResponse response2 = httpclient.execute(httpDelete);
		//System.out.println("content: "+getContent(response2));
		Assert.assertEquals("Must be Forbidden (403)", 403, response2.getStatusLine().getStatusCode());
		httpDelete.releaseConnection();
	}

	@Test
	public void testPost_Dept_Forbidden() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(baseUrl+"/DEPT?v:ID=3&v:NAME=Accounting&PARENT_ID=0");
		
		HttpResponse response1 = httpclient.execute(httpPost);
		
		Assert.assertEquals(201, response1.getStatusLine().getStatusCode());
		httpPost.releaseConnection();
	}
	
	@Test
	public void testPatchDeptOk() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPatch httpPut = new HttpPatch(baseUrl+"/DEPT/1?v:NAME=Accounting");
		
		HttpResponse response1 = httpclient.execute(httpPut);
		//System.out.println("content: "+getContent(response1));

		Assert.assertEquals(200, response1.getStatusLine().getStatusCode());
		httpPut.releaseConnection();
	}

	@Test
	public void testExecute() throws IOException, ParserConfigurationException, SAXException, TransformerException {
		String content = httpGetContent("/IS_PRIME?p1=3");
		Assert.assertEquals("true", content);
	}

	@Test
	public void testExecuteWithBodyParamIndex() throws IOException, ParserConfigurationException, SAXException, TransformerException {
		String content = httpPostContent("/IS_PRIME?bodyparamindex=1", "3", 200);
		Assert.assertEquals("true", content);
	}

	@Test
	public void testExecuteWithBodyParamIndexError() throws IOException, ParserConfigurationException, SAXException, TransformerException {
		String content = httpPostContent("/IS_PRIME?bodyparamindex=2", "3", 400);
	}
	
	@Test
	public void testGetRowsetSer() throws IOException, ClassNotFoundException, SQLException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpPut = new HttpGet(baseUrl+"/EMP.rowset.ser?fin:SALARY=2000"); //2 rows
		
		HttpResponse response1 = httpclient.execute(httpPut);
		HttpEntity entity1 = response1.getEntity();
		InputStream instream = entity1.getContent();
		
		Assert.assertEquals(200, response1.getStatusLine().getStatusCode());
		
		ObjectInputStream ois = new ObjectInputStream(instream);
		Object o = ois.readObject();

		Assert.assertTrue("Must be instance of ResultSet", o instanceof ResultSet);
		
		ResultSet rs = (ResultSet) o;
		Assert.assertTrue(rs.next());
		Assert.assertEquals("john", rs.getString(2));
		Assert.assertTrue(rs.next());
		Assert.assertEquals(2, rs.getInt(1));
		Assert.assertFalse(rs.next());
		rs.close();
		
		httpPut.releaseConnection();
	}

	@Test
	public void testGetJsonValues() throws IOException, ParserConfigurationException, SAXException {
		String sql = "select emp.*, cast(salary as char) as salary_char from emp where id = 1";
		String sqlpar = URLEncoder.encode(sql, utf8);

		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpGet = new HttpPost(baseUrl+"/QueryAny.json?name=emp&sql="+sqlpar+"&table-as-data-element=true");
		
		HttpResponse response1 = httpclient.execute(httpGet);
		String jsonStr = getContent(response1);
		//System.out.println("json:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		obj = jobj.get("emp");
		Assert.assertTrue("Should be a JSONArray", obj instanceof JSONArray);

		JSONArray jarr = (JSONArray) obj;
		Assert.assertEquals("Should have 2 (data) rows", 1, jarr.size());
		
		Object row = jarr.get(0);
		Assert.assertTrue("Should be a JSONObject", row instanceof JSONObject);

		JSONObject jRow = (JSONObject) row;
		Object salary = jRow.get("SALARY");
		Object salaryChar = jRow.get("SALARY_CHAR");

		//System.out.println("salary: "+salary+" / "+salary.getClass());
		Assert.assertTrue("Salary should be a Number", salary instanceof Number);
		Assert.assertTrue("SalaryChar should be a String", salaryChar instanceof String);
		
		httpGet.releaseConnection();
	}

	@Test
	public void testGetJsonValuesAsData() throws IOException, ParserConfigurationException, SAXException {
		String sql = "select emp.*, cast(salary as char) as salary_char from emp where id = 1";
		String sqlpar = URLEncoder.encode(sql, utf8);

		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpGet = new HttpPost(baseUrl+"/QueryAny.json?name=emp&sql="+sqlpar);
		
		HttpResponse response1 = httpclient.execute(httpGet);
		String jsonStr = getContent(response1);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		obj = jobj.get("data");
		Assert.assertTrue("Should be a JSONArray", obj instanceof JSONArray);
		
		httpGet.releaseConnection();
	}
	
	@Test
	public void getHtmlxValues() throws Exception {
		String sql = "select id, name, 'black' as name_CLASS from emp";
		String sqlpar = URLEncoder.encode(sql, utf8);

		String url = "/QueryAny.htmlx?name=emp&sql="+sqlpar;
		//String html = httpGetContent(url);
		//System.out.println(html);
		
		Document doc = getPostXmlDocument(url);
		NodeList nl = doc.getElementsByTagName("tr");
		Assert.assertEquals("Should be 6 rows (5 data rows (db tables) + 1 header)", 6, nl.getLength());
		
		for(int i=0;i<nl.getLength();i++) {
			Node n = nl.item(i);
			String tag = i==0?"th":"td";
			if(n instanceof Element) {
				Element e = (Element) n;
				NodeList nl2 = e.getElementsByTagName(tag);
				Assert.assertEquals("Should be 2 cols '"+tag+"' (*_CLASS cols are attributes)", 2, nl2.getLength());
			}
			else {
				Assert.fail("all nodes should be elements");
			}
		}
	}
	
	@Test
	public void testGetAliasesOk() throws ClientProtocolException, IOException {
		String content = getContentFromUrl(baseUrl+"/EMP.csv?aliases=C1,C2,C3,c4,c5");
		String columns = content.substring(0, content.indexOf("\r\n")); //CSVDataDump.DELIM_RECORD_DEFAULT));
		Assert.assertEquals("C1,C2,C3,c4,c5", columns);
	}

	/*@Test(expected=RuntimeException.class)
	public void testGetAliasesCountError() throws ClientProtocolException, IOException {
		getContentFromUrl(baseUrl+"/EMP.csv?aliases=C1,C2");
	}*/
	
	@Test
	public void testGetAliasesCountError() throws Exception {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/EMP.csv?aliases=C1,C2");
		HttpResponse response = httpclient.execute(httpGet);
		//System.out.println("content: "+getContent(response));
		Assert.assertEquals("Must be BadRequest", 400, response.getStatusLine().getStatusCode());
		httpGet.releaseConnection();
	}
	
	@Test
	public void testGetQueryFieldsAndAliases() throws Exception {
		String url = "/QUERY.EMP_Q1.csv?fields=ID,NAME&aliases=C1,c2";
		
		String content = getContentFromUrl(baseUrl+url);
		String columns = content.substring(0, content.indexOf("\r\n"));
		Assert.assertEquals("C1,c2", columns);
	}
	
	@Test
	public void testGetQueryAliases() throws Exception {
		String url = "/QUERY.EMP_Q1.csv?aliases=C1,C2";
		
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+url);
		HttpResponse response = httpclient.execute(httpGet);
		//System.out.println("content: "+getContent(response));
		Assert.assertEquals("Must be BadRequest", 400, response.getStatusLine().getStatusCode());
		httpGet.releaseConnection();
	}
	
	@Test
	@Ignore("QueryAny does not allow fields (yet)")
	public void testGetQueryAnyAliases() throws Exception {
		String sql = "select id, name, 'black' as name_CLASS from emp";
		String sqlpar = URLEncoder.encode(sql, utf8);
		String url = "/QueryAny.csv?_method=POST&name=emp&sql="+sqlpar+"&fields=ID,NAME,NAME+CLASS&aliases=C1,C2,C3";
		
		String content = getContentFromUrl(baseUrl+url);
		String columns = content.substring(0, content.indexOf("\r\n"));
		Assert.assertEquals("C1,C2,C3", columns);
	}
	
	//--------------------------- QueryOnSchema Tests -------------------------------
	
	static int getReturnCodeQosInstant(String query) throws ClientProtocolException, IOException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(qonSchemaInstantBaseUrl+query);
		
		HttpResponse response1 = httpclient.execute(httpGet);
		//String resp = getContent(response1);
		return response1.getStatusLine().getStatusCode();
	}

	static int getReturnCodeQoSchema(String query) throws ClientProtocolException, IOException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(qonSchemaBaseUrl+query);
		
		HttpResponse response1 = httpclient.execute(httpGet);
		//String resp = getContent(response1);
		return response1.getStatusLine().getStatusCode();
	}
	
	static int countNodesWithParentTagName(NodeList nl, String parentTag) {
		int count = 0;
		for (int j = 0; j < nl.getLength(); j++) {
			Element fileElement = (Element) nl.item(j);
			if (fileElement.getParentNode().getNodeName().equals(parentTag)) {
				count++;
			}
		}
		return count;
	}
	
	// http://stackoverflow.com/questions/5456680/xml-document-to-string
	static String getString(Document doc) throws TransformerException {
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		StringWriter writer = new StringWriter();
		transformer.transform(new DOMSource(doc), new StreamResult(writer));
		return writer.getBuffer().toString().replaceAll("\n|\r", "");
	}
	
	@Test
	public void testQosGetTableDept() throws IOException, ParserConfigurationException, SAXException {
		Assert.assertEquals(200, getReturnCodeQosInstant("/table/PUBLIC.DEPT"));
	}

	@Test
	public void testQosGetTableXxxError() throws IOException, ParserConfigurationException, SAXException {
		Assert.assertEquals(404, getReturnCodeQosInstant("/TABLE/PUBLIC.XXX"));
	}
	
	@Test
	public void testQosGetFkEmpdept() throws IOException, ParserConfigurationException, SAXException {
		Assert.assertEquals(200, getReturnCodeQoSchema("/FK/PUBLIC.EMP_DEPT_FK"));
	}
	
	@Test
	public void testQosGetFkEmpdeptError() throws IOException, ParserConfigurationException, SAXException {
		Assert.assertEquals(404, getReturnCodeQosInstant("/FK/PUBLIC.EMP_XXX_FK"));
	}
	
	/*@Test
	public void swaggerJsonSimple() throws IOException, ParserConfigurationException, SAXException {
		String jsonStr = getContentFromUrl(qonUrl+"/swagger");
		JSONValue.parse(jsonStr);
	}*/
	
	@Test
	public void testGetCountFromCsv() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/PUBLIC.EMP.csv?count=true&header=false");
		
		HttpResponse response1 = httpclient.execute(httpGet);
		String cntnt = getContent(response1);
		int count = Integer.parseInt(cntnt.substring(0, cntnt.length()-2));

		Assert.assertEquals("Should have count==5", 5, count);
		
		httpGet.releaseConnection();
	}

	@Test
	public void testGetCountDistinctFromCsv() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/PUBLIC.EMP.csv?count=true&header=false&distinct=true&fields=SUPERVISOR_ID");
		
		HttpResponse response1 = httpclient.execute(httpGet);
		String cntnt = getContent(response1);
		httpGet.releaseConnection();
		int count = Integer.parseInt(cntnt.substring(0, cntnt.length()-2));

		Assert.assertEquals("Should have count==2", 2, count);
	}

	@Test
	public void swaggerGsonParse() throws IOException, ParserConfigurationException, SAXException {
		String jsonStr = getContentFromUrl(qonUrl+"/swagger");
		JsonParser parser = new JsonParser();
		//System.out.println(jsonStr);
		JsonElement json = parser.parse(jsonStr);
		Assert.assertTrue(json.isJsonObject());
		
		JsonObject jsonObject = json.getAsJsonObject();
		JsonElement swagger = jsonObject.get("swagger");
		Assert.assertEquals("2.0", swagger.getAsString());

		JsonObject paths = jsonObject.get("paths").getAsJsonObject();
		/*for(Entry<String, JsonElement> e: paths.entrySet()) {
			String key = e.getKey();
			JsonObject operation = e.getValue().getAsJsonObject();
			System.out.println(key+" >>> "+operation);
		}*/
		Set<Entry<String, JsonElement>> set = paths.entrySet();
		Iterator<Entry<String, JsonElement>> it = set.iterator();
		
		Entry<String, JsonElement> e = it.next();
		String key = e.getKey();
		//JsonObject operation = e.getValue().getAsJsonObject();
		Assert.assertEquals("/PUBLIC.DEPT.{syntax}", key);
		Assert.assertTrue(e.getValue().getAsJsonObject().get("get")!=null);
		
		e = it.next();
		key = e.getKey();
		//operation = e.getValue().getAsJsonObject();
		Assert.assertEquals("/PUBLIC.DEPT", key);
		Assert.assertTrue(e.getValue().getAsJsonObject().get("post")!=null);

		e = it.next();
		key = e.getKey();
		Assert.assertEquals("/PUBLIC.EMP.{syntax}", key);
		Assert.assertTrue(e.getValue().getAsJsonObject().get("get")!=null);

		e = it.next();
		key = e.getKey();
		Assert.assertEquals("/PUBLIC.EMP", key);
		Assert.assertTrue(e.getValue().getAsJsonObject().get("post")!=null);
	}

	@SuppressWarnings("unused")
	@Test
	public void swaggerGsonCall() throws IOException, ParserConfigurationException, SAXException {
		String jsonStr = getContentFromUrl(qonUrl+"/swagger");
		JsonParser parser = new JsonParser();
		//System.out.println(jsonStr);
		JsonElement json = parser.parse(jsonStr);
		JsonObject jsonObject = json.getAsJsonObject();
		JsonObject paths = jsonObject.get("paths").getAsJsonObject();
		for(Entry<String, JsonElement> e: paths.entrySet()) {
			String key = e.getKey();
			JsonObject operation = e.getValue().getAsJsonObject();
			String path = key.replaceFirst("\\{syntax\\}", "json");
			String queryString = "";
			//System.out.println(key+" >>> "+operation);
			if(operation.get("get")==null) {
				// testing get (retrieve) operations only
				continue;
			}
			if(path.indexOf("NAMED_PARAMS_1")>=0) {
				queryString = "?par1=1&par2=2";
			}
			if(path.indexOf("QUERY_WITH_PARAMS_NULL_BIND_ARRAY")>=0) {
				queryString = "?par2=2";
			}
			if(path.indexOf("QUERY_WITH_POSITIONAL_PARAMS")>=0) {
				queryString = "?p1=1&p2=2&p3=3";
			}
			String url = "http://"+jsonObject.get("host").getAsString()+jsonObject.get("basePath").getAsString()+path+queryString;
			//System.out.println("swaggerGsonCall: "+url);
			JsonElement resp = parser.parse(getContentFromUrl(url));
			//String respStr = getContentFromUrl(url);
			//System.out.println("swaggerGsonCall: resp = "+resp);
		}
	}

	@Test
	public void testGetGroupBy() throws Exception {
		String url = "/PUBLIC.EMP.csv?groupby=SUPERVISOR_ID";
		
		String content = getContentFromUrl(baseUrl+url);
		System.out.println(content);
		Assert.assertEquals("SUPERVISOR_ID" + LF + 
				"1" + LF +
				"2" + LF,
				content);
	}
	
	@Test
	public void testGetGroupByWithOrder() throws Exception {
		String url = "/PUBLIC.EMP.csv?groupby=SUPERVISOR_ID&order=-SUPERVISOR_ID";
		
		String content = getContentFromUrl(baseUrl+url);
		System.out.println(content);
		Assert.assertEquals("SUPERVISOR_ID" + LF + 
				"2" + LF +
				"1" + LF,
				content);
	}

	@Test
	public void testGetGroupByWithOrderAndAliases() throws Exception {
		String url = "/PUBLIC.EMP.csv?groupby=SUPERVISOR_ID&order=-SUPERVISOR_ID&aliases=supervisor";
		
		String content = getContentFromUrl(baseUrl+url);
		System.out.println(content);
		Assert.assertEquals("supervisor" + LF + 
				"2" + LF +
				"1" + LF,
				content);
	}
	
	@Test
	public void testGetGroupByWithAggregate() throws Exception {
		String url = "/PUBLIC.EMP.csv?groupby=SUPERVISOR_ID&order=-SUPERVISOR_ID&agg:SALARY=sum";
		
		String content = getContentFromUrl(baseUrl+url);
		System.out.println(content);
		Assert.assertEquals("SUPERVISOR_ID,sum_SALARY" + LF + 
				"2,4200" + LF +
				"1,3000" + LF,
				content);
	}

	@Test
	public void testGetGroupByWithAggregate4() throws Exception {
		String url = "/PUBLIC.EMP.csv?groupby=SUPERVISOR_ID&order=-SUPERVISOR_ID&agg:SALARY=sum&agg:SALARY=max&agg:SALARY=min&agg:SALARY=count";
		
		String content = getContentFromUrl(baseUrl+url);
		System.out.println(content);
		Assert.assertEquals("SUPERVISOR_ID,sum_SALARY,max_SALARY,min_SALARY,count_SALARY" + LF + 
				"2,4200,2000,1000,3" + LF +
				"1,3000,2000,1000,2" + LF,
				content);
	}
	
	@Test
	public void testGetGroupByWithAggregatePivot() throws Exception {
		String url = "/PUBLIC.EMP.csv?onrows=SUPERVISOR_ID&groupbydims=true&agg:SALARY=sum&agg:SALARY=count";
		
		String content = getContentFromUrl(baseUrl+url);
		System.out.println(content);
		Assert.assertEquals("SUPERVISOR_ID,sum_SALARY,count_SALARY" + LF + 
				"1,3000,2" + LF +
				"2,4200,3" + LF,
				content);
	}

	@Test
	public void testGetQueryWithNamedParameters() throws Exception {
		String url = "/QUERY.NAMED_PARAMS_1.csv?par1=1&par2=2";
		
		String content = getContentFromUrl(baseUrl+url);
		System.out.println(content);
		Assert.assertEquals("C1" + LF + 
				"1" + LF +
				"2" + LF +
				"1" + LF,
				content);
		
	}

	@Test
	public void testGetQueryWithNamedParametersUsingPositionals() throws Exception {
		String url = "/QUERY.NAMED_PARAMS_1.csv?p1=1&p2=2&p3=3";
		
		String content = getContentFromUrl(baseUrl+url);
		System.out.println(content);
		Assert.assertEquals("C1" + LF + 
				"1" + LF +
				"2" + LF +
				"3" + LF,
				content);
	}

	@Test
	public void testGetQueryWithNamedParametersUsingMixed() throws Exception {
		String url = "/QUERY.NAMED_PARAMS_1.csv?p1=1&par2=2";
		getContentFromUrl(baseUrl+url, 400);
	}
	

	@Test
	public void testGetQueryWithNamedParametersUsingMixed2() throws Exception {
		String url = "/QUERY.NAMED_PARAMS_1.csv?par1=1&p2=2";
		getContentFromUrl(baseUrl+url, 400);
	}

	@Test
	public void testGetQueryWithNamedParametersMissingParam() throws Exception {
		String url = "/QUERY.NAMED_PARAMS_1.csv?par1=1";
		getContentFromUrl(baseUrl+url, 400);
	}
	
	@Test
	public void testGetQueryWithNamedParametersNullBind1() throws Exception {
		String url = "/QUERY.QUERY_WITH_PARAMS_NULL_BIND.csv?par1=1";
		getContentFromUrl(baseUrl+url);
	}

	@Test
	public void testGetQueryWithNamedParametersNullBind2() throws Exception {
		String url = "/QUERY.QUERY_WITH_PARAMS_NULL_BIND.csv?p1=1";
		getContentFromUrl(baseUrl+url);
	}

	@Test
	public void testGetQueryWithNamedParametersNullBind3() throws Exception {
		String url = "/QUERY.QUERY_WITH_PARAMS_NULL_BIND.csv";
		getContentFromUrl(baseUrl+url);
	}
	
	@Test
	public void testGetQueryWithNamedParametersNullBindError() throws Exception {
		String url = "/QUERY.QUERY_WITH_PARAMS_NULL_BIND.csv?par1=1&p2=2";
		getContentFromUrl(baseUrl+url, 400);
	}

	/*
		QUERY_WITH_PARAMS_NULL_BIND_ARRAY
		named-parameters=par1,par2,par1
		bind-null-on-missing-parameters=true,false,true
	*/
	
	@Test
	public void testGetQueryWithNamedParametersNullBindArray() throws Exception {
		String url = "/QUERY.QUERY_WITH_PARAMS_NULL_BIND_ARRAY.csv?p1=1&p2=2&p3=1";
		getContentFromUrl(baseUrl+url);
	}

	@Test
	public void testGetQueryWithNamedParametersNullBindArray2() throws Exception {
		String url = "/QUERY.QUERY_WITH_PARAMS_NULL_BIND_ARRAY.csv";
		getContentFromUrl(baseUrl+url, 400);
	}

	@Test
	public void testGetQueryWithNamedParametersNullBindArray3() throws Exception {
		String url = "/QUERY.QUERY_WITH_PARAMS_NULL_BIND_ARRAY.csv?par2=2";
		getContentFromUrl(baseUrl+url);
	}
	
	static HttpResponse getGetResponse(HttpContext httpContext, HttpClient httpClient, String url) throws ClientProtocolException, IOException {
		HttpGet httpCall = new HttpGet(url);
		return httpClient.execute(httpCall, httpContext);
	}
	
	static void setCookies(HttpRequestBase httpCall, CookieStore cookies) {
		if(cookies!=null && !cookies.getCookies().isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for(Cookie c: cookies.getCookies()) {
				if(sb.length()>0) {
					sb.append("; ");
				}
				sb.append(c.getName()+"="+c.getValue());
				//httpCall.setHeader("Cookie", c.getName()+"="+c.getValue());
			}
			httpCall.setHeader("Cookie", sb.toString());
		}
	}

	static HttpResponse getGetResponse(HttpClient httpClient, String url, CookieStore cookies) throws ClientProtocolException, IOException {
		HttpGet httpCall = new HttpGet(url);
		//CookieStore cs = (CookieStore)httpContext.getAttribute(HttpClientContext.COOKIE_STORE);
		//cs.getCookies()
		setCookies(httpCall, cookies);
		log.debug("getGetResponse: request-headers: "+Arrays.asList(httpCall.getAllHeaders()) );
		//httpCall.setHeader("Cookie", "JSESSIONID=1234");
		return httpClient.execute(httpCall);
	}
	
	static HttpResponse jspLogin(HttpClient httpClient, CookieStore cookies, String username, String password) throws ClientProtocolException, IOException {
		HttpPost httpPost = new HttpPost(qonUrl+"/auth/login.jsp");
		setCookies(httpPost, cookies);
		ArrayList<NameValuePair> postParameters;
		postParameters = new ArrayList<NameValuePair>();
		postParameters.add(new BasicNameValuePair("username", username));
		postParameters.add(new BasicNameValuePair("password", password));
		httpPost.setEntity(new UrlEncodedFormEntity(postParameters, "UTF-8"));
		return httpClient.execute(httpPost);
	}

	static HttpResponse jspLogout(HttpClient httpClient, CookieStore cookies) throws ClientProtocolException, IOException {
		HttpPost httpPost = new HttpPost(qonUrl+"/auth/logout.jsp");
		setCookies(httpPost, cookies);
		return httpClient.execute(httpPost);
	}
	
	@Test
	public void testLoginOk() throws Exception {
		// https://stackoverflow.com/a/6273665/616413
		HttpClient httpClient = new DefaultHttpClient();
		//CookieStore cookieStore = new BasicCookieStore();
		//HttpContext httpContext = new BasicHttpContext();
		//httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

		{
			HttpResponse response1 = jspLogin(httpClient, null, "jdoe", "jdoepw");
			Assert.assertEquals(200, response1.getStatusLine().getStatusCode());
			// https://stackoverflow.com/a/16211729/616413
			EntityUtils.consumeQuietly(response1.getEntity());
		}
	}

	@Test
	public void testLoginErr() throws Exception {
		HttpClient httpClient = new DefaultHttpClient();

		{
			HttpResponse response2 = jspLogin(httpClient, null, "jdoe", "jdoez");
			Assert.assertTrue(response2.getStatusLine().getStatusCode() >= 400);
			EntityUtils.consumeQuietly(response2.getEntity());
		}
	}
	
	@Test
	public void testLoginLogout() throws Exception {
		// https://issues.apache.org/jira/browse/SHIRO-613 !!!
		JsonParser parser = new JsonParser();
		// https://stackoverflow.com/a/6273665/616413
		//HttpClient httpClient = HttpClients.custom().build();
		CookieStore cookieStore = new BasicCookieStore();
		HttpClient httpClient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();
		//HttpContext httpContext = new BasicHttpContext();
		//httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

		// ...
		
		//String loginpage = getContentFromUrl(qonUrl+"/auth/login.jsp");
		//System.out.println(loginpage);
		
		// ...

		/*
		BasicClientCookie cookie = new BasicClientCookie("ZZ", "1234");
		cookie.setDomain("localhost");
		cookie.setPath("/");
		cookieStore.addCookie(cookie);
		*/
		
		//System.out.println("\ncookieStore0:\n"+cookieStore);
		
		{
			// XXX logout needed in the beginning? interaction between tests...
			HttpResponse response3 = jspLogout(httpClient, cookieStore);
			Assert.assertEquals(200, response3.getStatusLine().getStatusCode());
			EntityUtils.consumeQuietly(response3.getEntity());
		}
		
		//testLoginOk();
		
		{
			HttpResponse jsonResp = getGetResponse(httpClient, qonUrl+"/auth/info.jsp", cookieStore);
			String jsonStr = getContent(jsonResp);
			//System.out.println("response-headers:\n"+Arrays.asList(jsonResp.getAllHeaders()));
			//System.out.print("json:\n"+jsonStr+"\n");
			JsonElement json = parser.parse(jsonStr);
			Assert.assertEquals(false, json.getAsJsonObject().get("authenticated").getAsBoolean());
			Assert.assertEquals("anonymous", json.getAsJsonObject().get("username").getAsString());
			EntityUtils.consumeQuietly(jsonResp.getEntity());
		}
		
		//Thread.sleep(1000);
		//System.out.println("\ncookieStore1:\n"+cookieStore);
		
		{
			HttpResponse response1 = jspLogin(httpClient, cookieStore, "jdoe", "jdoepw");
			Assert.assertEquals(200, response1.getStatusLine().getStatusCode());
			// https://stackoverflow.com/a/16211729/616413
			EntityUtils.consumeQuietly(response1.getEntity());
		}

		//Thread.sleep(1000);
		//System.out.println("\ncookieStore2:\n"+cookieStore);
		
		{
			HttpResponse jsonResp = getGetResponse(httpClient, qonUrl+"/auth/info.jsp", cookieStore);
			//System.out.println("response-headers:\n"+Arrays.asList(jsonResp.getAllHeaders()));
			String jsonStr = getContent(jsonResp);
			//System.out.print("json:\n"+jsonStr+"\n");
			JsonElement json = parser.parse(jsonStr);
			Assert.assertEquals(true, json.getAsJsonObject().get("authenticated").getAsBoolean());
			Assert.assertEquals("jdoe", json.getAsJsonObject().get("username").getAsString());
			EntityUtils.consumeQuietly(jsonResp.getEntity());
		}
		
		//Thread.sleep(1000);
		//System.out.println("\ncookieStore3:\n"+cookieStore);
		
		{
			HttpResponse response2 = jspLogin(httpClient, cookieStore, "jdoe", "jdoez");
			Assert.assertTrue(response2.getStatusLine().getStatusCode() >= 400);
			EntityUtils.consumeQuietly(response2.getEntity());
		}
		
		//Thread.sleep(1000);
		//System.out.println("\ncookieStore4:\n"+cookieStore);
		
		{
			HttpResponse response3 = jspLogout(httpClient, cookieStore);
			Assert.assertEquals(200, response3.getStatusLine().getStatusCode());
			EntityUtils.consumeQuietly(response3.getEntity());
		}
		
		{
			HttpResponse jsonResp = getGetResponse(httpClient, qonUrl+"/auth/info.jsp", cookieStore);
			String jsonStr = getContent(jsonResp);
			//System.out.print("json:\n"+jsonStr+"\n");
			JsonElement json = parser.parse(jsonStr);
			Assert.assertEquals(false, json.getAsJsonObject().get("authenticated").getAsBoolean());
			Assert.assertEquals("anonymous", json.getAsJsonObject().get("username").getAsString());
			EntityUtils.consumeQuietly(jsonResp.getEntity());
		}
		
	}

	// not recommended
	/*
	@Test
	public void testGetQueryWithBodyParamIndex() throws Exception {
		String url = "/QUERY.NAMED_PARAMS_1.csv?_method=GET&bodyparamindex=1&p2=2&p3=3"; // p1=1&p2=2&p3=3
		
		String content = httpPostContent(url, "1");
		System.out.println(content);
		Assert.assertEquals("C1" + LF + 
				"1" + LF +
				"2" + LF +
				"3" + LF,
				content);
	}
	*/
	
	@Test
	public void testPatchWithBodyParamName() throws IllegalStateException, IOException {
		httpPatchContent("/EMP/1?bodyparamname=NAME", "newname", 200);
		//httpPostContent("/EMP/1?bodyparamname=NAME&_method=PATCH", "newname", 200);
	}

	@Test
	public void testPatchWithBodyParamNameError() throws IllegalStateException, IOException {
		httpPatchContent("/EMP/1", "", 400);
	}
	
	@Test
	public void testPostWithBodyParamName() throws IllegalStateException, IOException {
		httpPostContent("/EMP?v:ID=11&bodyparamname=NAME", "sonya", 201);
	}
	
	@Test
	public void testPostWithBodyParamNameError() throws IllegalStateException, IOException {
		httpPostContent("/EMP?v:ID=11", "", 500); //XXX 500?
	}
	
	@Test
	public void testPostWithBodyParamNameError2() throws IllegalStateException, IOException {
		httpPostContent("/EMP?v:ID=11&bodyparamname=NAMEZ", "sonya", 400); //400
	}
	
	@Test
	public void testExecFileUpload() throws IllegalStateException, IOException {
		HttpPost post = new HttpPost(baseUrl + "/IS_PRIME");
		//FileBody fileBody = new FileBody(file, ContentType.DEFAULT_BINARY);
		StringBody stringBody1 = new StringBody("3", ContentType.APPLICATION_OCTET_STREAM);
		// 
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.STRICT);
		//builder.addPart("upfile", fileBody);
		builder.addPart("p1", stringBody1);
		HttpEntity entity = builder.build();
		//
		post.setEntity(entity);
		HttpClient client = HttpClientBuilder.create().build();
		HttpResponse response = client.execute(post);
		
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());
		String content = getContent(response);
		Assert.assertEquals("true", content);
	}

	@Test
	public void testUpdateWithFileUpload() throws IllegalStateException, IOException {
		HttpPatch request = new HttpPatch(baseUrl + "/EMP/1");
		StringBody stringBody1 = new StringBody("newname", ContentType.APPLICATION_OCTET_STREAM);
		// 
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.STRICT);
		builder.addPart("v:NAME", stringBody1);
		HttpEntity entity = builder.build();
		//
		request.setEntity(entity);
		HttpClient client = HttpClientBuilder.create().build();
		HttpResponse response = client.execute(request);
		
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());
		String content = getContent(response);
		Assert.assertEquals("1 row updated", content);
	}
	
	@Test
	public void testUpdateWithJson() throws IllegalStateException, IOException {
		HttpPatch request = new HttpPatch(baseUrl + "/EMP/1");
		request.setEntity(new StringEntity("{\"NAME\":\"newname\"}") {});
		request.setHeader("Content-Type", "application/json");
		HttpClient client = HttpClientBuilder.create().build();
		HttpResponse response = client.execute(request);
		
		Assert.assertEquals(200, response.getStatusLine().getStatusCode());
		String content = getContent(response);
		Assert.assertEquals("1 row updated", content);
	}

	@Test
	public void testInsertWithJson() throws IllegalStateException, IOException {
		HttpPost request = new HttpPost(baseUrl + "/EMP");
		request.setEntity(new StringEntity("{\"ID\": 10, \"NAME\":\"newname\"}") {});
		request.setHeader("Content-Type", "application/json");
		HttpClient client = HttpClientBuilder.create().build();
		HttpResponse response = client.execute(request);
		
		Assert.assertEquals(201, response.getStatusLine().getStatusCode());
		String content = getContent(response);
		Assert.assertEquals("1 row inserted", content);
	}

	@Test
	public void testValidateAny() throws IOException, ParserConfigurationException, SAXException {
		String sql = "select id, name from emp";
		String sqlpar = URLEncoder.encode(sql, utf8);
		String ret = httpPostContent("/ValidateAny.xml?name=test&sql="+sqlpar, "");
	}
	
	@Test
	public void testValidateAnyError() throws IOException, ParserConfigurationException, SAXException {
		String sql = "select id, namez from emp";
		String sqlpar = URLEncoder.encode(sql, utf8);
		String ret = httpPostContent("/ValidateAny.xml?name=test&sql="+sqlpar, "", 400);
	}
	
	@Test
	public void testSqlAny() throws IOException, ParserConfigurationException, SAXException {
		String sql = "insert into emp (id, name) values (10, 'newname')";
		String sqlpar = URLEncoder.encode(sql, utf8);
		String ret = httpPostContent("/SqlAny.xml?name=test&sql="+sqlpar, "");
	}

	@Test
	public void testSqlAnyError() throws IOException, ParserConfigurationException, SAXException {
		String sql = "insert into empz (id, name) values (10, 'newname')";
		String sqlpar = URLEncoder.encode(sql, utf8);
		String ret = httpPostContent("/SqlAny.xml?name=test&sql="+sqlpar, "", 400);
	}
	
}
