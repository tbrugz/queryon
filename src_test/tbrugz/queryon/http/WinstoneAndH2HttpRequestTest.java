package tbrugz.queryon.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.SQLException;
import java.util.Iterator;

import javax.naming.NamingException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
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
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import tbrugz.sqldump.sqlrun.SQLRun;
import tbrugz.sqldump.util.IOUtil;
import static tbrugz.queryon.http.TestSetup.baseUrl;

public class WinstoneAndH2HttpRequestTest {
	
	static DocumentBuilderFactory dbFactory;
	static DocumentBuilder dBuilder;
	
	@BeforeClass
	public static void setup() throws IOException, ParserConfigurationException {
		TestSetup.setupWinstone();
		
		dbFactory = DocumentBuilderFactory.newInstance();
		dBuilder = dbFactory.newDocumentBuilder();
	}
	
	public static void setupH2() throws ClassNotFoundException, IOException, SQLException, NamingException {
		String[] params = {"-propfile=src_test/tbrugz/queryon/http/sqlrun.properties"};
		SQLRun.main(params);
	}
	
	@AfterClass
	public static void shutdown() {
		TestSetup.shutdown();
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
		return IOUtil.readFile(new InputStreamReader(instream));
	}

	/*
	 * see: http://hc.apache.org/httpcomponents-client-ga/quickstart.html
	 */
	@Test
	public void testGet01_OK() throws IOException {
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
			NodeList nl = doc.getElementsByTagName("tr");
			Assert.assertEquals("Should be 3 rows (2 data rows (db tables) + 1 header)", 3, nl.getLength());
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
			InputStream instream = entity1.getContent();
			Document doc = dBuilder.parse(instream);
			NodeList nl = doc.getElementsByTagName("tr");
			Assert.assertEquals("Should be 3 rows (2 data rows (FKs) + 1 header)", 3, nl.getLength());
			
			EntityUtils.consume(entity1);
		} finally {
			httpGet.releaseConnection();
		}
	}

	@Test @Ignore("maybe later")
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

	/*
	 * TODO: HttpPut isn't working with winstone
	 * http://code.google.com/p/winstone/source/browse/trunk/winstone/src/main/java/net/winstone/core/listener/HttpListener.java
	 */
	@Test
	public void testPut_Emp_OK() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		//HttpGet httpPut = new HttpGet(baseUrl+"/EMP?v:NAME=newname&feq:ID=1&method=PUT");
		HttpGet httpPut = new HttpGet(baseUrl+"/EMP/1?v:NAME=newname&method=PUT");
		//HttpPut httpPut = new HttpPut(baseUrl+"/EMP/1?v:NAME=newname&method=PUT");
		//HttpPut httpPut = new HttpPut(baseUrl+"/EMP/1");
		//HttpPost httpPut = new HttpPost(baseUrl+"/EMP/1");

		//List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		//nvps.add(new BasicNameValuePair("method", "PUT"));
		//nvps.add(new BasicNameValuePair("v:NAME", "newname"));
		//httpPut.setEntity(new UrlEncodedFormEntity(nvps));
		
		System.out.println("PUT-uri: "+httpPut.getRequestLine());
		//System.out.println(EntityUtils.toString(new UrlEncodedFormEntity(nvps)));		
		
		HttpResponse response1 = httpclient.execute(httpPut);
		System.out.println("content: "+getContent(response1));

		Assert.assertEquals("Must be OK (updated)", 200, response1.getStatusLine().getStatusCode());
		httpPut.releaseConnection();
	}

	@Test
	public void testPut_Emp_Error() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpPut = new HttpGet(baseUrl+"/EMP/1?method=PUT");
		
		HttpResponse response1 = httpclient.execute(httpPut);
		
		Assert.assertEquals("Must be Bad Request (no update columns informed)", 400, response1.getStatusLine().getStatusCode());
		httpPut.releaseConnection();
	}

	@Test
	public void testDelete_Emp_Ok() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();

		HttpGet httpGet = new HttpGet(baseUrl+"/EMP/5?method=DELETE");
		HttpResponse response1 = httpclient.execute(httpGet);
		System.out.println("content: "+getContent(response1));
		Assert.assertEquals("Must be OK", 200, response1.getStatusLine().getStatusCode());
		httpGet.releaseConnection();

		HttpDelete httpDelete = new HttpDelete(baseUrl+"/EMP/4");
		HttpResponse response2 = httpclient.execute(httpDelete);
		System.out.println("content: "+getContent(response2));
		Assert.assertEquals("Must be OK", 200, response2.getStatusLine().getStatusCode());
		httpDelete.releaseConnection();
	}

	@Test
	public void testDelete_Emp_404() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpDelete httpDelete = new HttpDelete(baseUrl+"/EMP/7");
		HttpResponse response2 = httpclient.execute(httpDelete);
		System.out.println("content: "+getContent(response2));
		Assert.assertEquals("Must be Not Found (no rows deleted)?", 404, response2.getStatusLine().getStatusCode());
		httpDelete.releaseConnection();
	}

	@Test
	public void testDelete_Emp_3rows() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		//XXX: delete doesn't get parameters from querystring?
		HttpGet httpDelete = new HttpGet(baseUrl+"/EMP?fe:DEPARTMENT_ID=2&method=DELETE");
		HttpResponse response2 = httpclient.execute(httpDelete);
		String content = getContent(response2);
		System.out.println("content: "+content);
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
		HttpEntity entity1 = response1.getEntity();
		InputStream instream = entity1.getContent();
		Document doc = dBuilder.parse(instream);
		NodeList nl = doc.getElementsByTagName("row");
		Assert.assertEquals("Should have 2 (data) rows", 2, nl.getLength());
		
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
		obj = jobj.get("table");
		Assert.assertTrue("Should be a JSONArray", obj instanceof JSONArray);

		JSONArray jarr = (JSONArray) obj;
		Assert.assertEquals("Should have 2 (data) rows", 2, jarr.size());
		
		httpGet.releaseConnection();
	}
	
	@Test
	public void testGet_JGSON_Tables_PrettyPrint() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/table.json");
		HttpResponse response1 = httpclient.execute(httpGet);
		String jsonStr = getContent(response1);
		System.out.print("json-original:\n"+jsonStr+"\n");
		
		Gson gsonpretty = new GsonBuilder().setPrettyPrinting().create();
		Object jsonObj = gsonpretty.fromJson(jsonStr, Object.class);
		String jsonOutput = gsonpretty.toJson(jsonObj);
		System.out.print("json-pretty:\n"+jsonOutput+"\n");

		Gson gson = new Gson();
		jsonObj = gson.fromJson(jsonStr, Object.class);
		jsonOutput = gson.toJson(jsonObj);
		System.out.print("json-compact:\n"+jsonOutput+"\n");
	}

	@Test
	public void testGet_CSV_Tables() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/table.csv");
		
		HttpResponse response1 = httpclient.execute(httpGet);
		HttpEntity entity1 = response1.getEntity();
		Reader in = new InputStreamReader(entity1.getContent());
		
		//int count = 0;
		CSVFormat format = CSVFormat.newBuilder(CSVFormat.DEFAULT).withHeader().build();
		CSVParser parser = new CSVParser(in, format);
		System.out.println("headers: "+parser.getHeaderMap());
		Iterator<CSVRecord> it = parser.iterator();
		
		Assert.assertTrue("Must have 1st element", it.hasNext());
		CSVRecord record = it.next();
		System.out.println("1st record (name = '"+record.get("name")+"'): "+record);
		Assert.assertEquals("1st record name must be DEPT", "DEPT", record.get("name"));
		
		Assert.assertTrue("Must have 2nd element", it.hasNext());
		record = it.next();
		System.out.println("2nd record (name = '"+record.get("name")+"'): "+record);
		Assert.assertEquals("1st record name must be DEPT", "EMP", record.get("name"));
		
		Assert.assertFalse("Must not have have 3rd element", it.hasNext());
		/*for (CSVRecord record: format.parse(in)) {
			/*for (String field : record) {
				System.out.print("\"" + field + "\", ");
			}
			System.out.println();* /
			System.out.println("record: "+record);
			count++;
		}
		
		Assert.assertEquals("Should have 2 (data) rows", 2, count);*/
		
		EntityUtils.consume(entity1);
		httpGet.releaseConnection();
	}

}
