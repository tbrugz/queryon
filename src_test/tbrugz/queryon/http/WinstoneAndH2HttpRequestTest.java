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
import java.util.Iterator;
import java.util.List;

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
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
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
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import tbrugz.sqldump.sqlrun.SQLRun;
import tbrugz.sqldump.util.IOUtil;
import tbrugz.sqldump.util.Utils;

import static tbrugz.queryon.http.JettySetup.*;

public class WinstoneAndH2HttpRequestTest {
	
	static DocumentBuilderFactory dbFactory;
	static DocumentBuilder dBuilder;
	static String workDir = "work/test/";
	static String utf8 = "UTF-8";
	
	@BeforeClass
	public static void setup() throws Exception {
		//TestSetup.setupWinstone();
		JettySetup.setupServer();
		
		dbFactory = DocumentBuilderFactory.newInstance();
		dBuilder = dbFactory.newDocumentBuilder();
	}
	
	public static void setupH2() throws ClassNotFoundException, IOException, SQLException, NamingException {
		String[] params = {"-propfile=src_test/tbrugz/queryon/http/sqlrun.properties"};
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
		return IOUtil.readFile(new InputStreamReader(instream));
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
			Assert.assertEquals(200, response1.getStatusLine().getStatusCode());
			//System.out.println("content: "+getContent(response1));
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
		HttpGet httpPut = new HttpGet(baseUrl+"/EMP/1?v:NAME=newname&_method=PUT");
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
		HttpGet httpPut = new HttpGet(baseUrl+"/EMP/1?_method=PUT");
		
		HttpResponse response1 = httpclient.execute(httpPut);
		
		Assert.assertEquals("Must be Bad Request (no update columns informed)", 400, response1.getStatusLine().getStatusCode());
		httpPut.releaseConnection();
	}

	@Test
	public void testDelete_Emp_Ok() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();

		HttpGet httpGet = new HttpGet(baseUrl+"/EMP/5?_method=DELETE");
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
		HttpGet httpDelete = new HttpGet(baseUrl+"/EMP?feq:DEPARTMENT_ID=2&_method=DELETE&updatemax=5");
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
		Assert.assertEquals("2st record' 1st col must be DEPT", "EMP", value);
		
		Assert.assertFalse("Must not have have 3rd element", it.hasNext());
		
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

	static void baseReturnCountTest(String url, int expectedReturnRows) throws IOException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+url);
		
		HttpResponse response1 = httpclient.execute(httpGet);
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
		//String resp = getContent(response1); System.out.println(resp);
		
		HttpEntity entity1 = response1.getEntity();
		InputStream instream = entity1.getContent();
		return dBuilder.parse(instream);
	}
	
	static String httpGetContent(String url) throws IllegalStateException, IOException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet http = new HttpGet(baseUrl+url);
		HttpResponse response = httpclient.execute(http);
		return getContent(response);
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
		System.out.println("sql: "+sql);
		return sql;
	}
	
	@Test
	public void testGetXmlSelectAny10() throws IOException, ParserConfigurationException, SAXException {
		// limited by "queryon.limit.default=10"
		String sql = getSql30rows();
		String sqlpar = URLEncoder.encode(sql, utf8);
		baseReturnCountTest("/QueryAny.xml?_method=POST&name=test&sql="+sqlpar, 10);
	}
	
	@Test
	public void testGetXmlSelectAny20() throws IOException, ParserConfigurationException, SAXException {
		// limited by "queryon.limit.max=20"
		String sql = getSql30rows();
		String sqlpar = URLEncoder.encode(sql, utf8);
		baseReturnCountTest("/QueryAny.xml?_method=POST&name=test&sql="+sqlpar+"&limit=25", 20);
	}
	
	@Test
	public void testGetXmlSelectAnyLimitDefault() throws IOException, ParserConfigurationException, SAXException {
		// limited by "limit-default=5"
		String sql = getSql30rows() + "/* limit-default=5 */";
		String sqlpar = URLEncoder.encode(sql, utf8);
		baseReturnCountTest("/QueryAny.xml?_method=POST&name=test&sql="+sqlpar, 5);
	}
	
	@Test
	public void testGetXmlSelectAnyLimitDefault9() throws IOException, ParserConfigurationException, SAXException {
		// limited by "&limit=9"
		String sql = getSql30rows() + "/* limit-default=5 */";
		String sqlpar = URLEncoder.encode(sql, utf8);
		baseReturnCountTest("/QueryAny.xml?_method=POST&name=test&sql="+sqlpar+"&limit=9", 9);
	}
	
	@Test
	public void testGetXmlSelectAnyLimitMax() throws IOException, ParserConfigurationException, SAXException {
		String sql = getSql30rows() + "/* limit-max=5 */";
		String sqlpar = URLEncoder.encode(sql, utf8);
		baseReturnCountTest("/QueryAny.xml?_method=POST&name=test&sql="+sqlpar, 5);
	}
	
	@Test
	public void testGetXmlSelectAnyLimitMax9() throws IOException, ParserConfigurationException, SAXException {
		String sql = getSql30rows() + "/* limit-max=5 */";
		String sqlpar = URLEncoder.encode(sql, utf8);
		baseReturnCountTest("/QueryAny.xml?_method=POST&name=test&sql="+sqlpar+"&limit=9", 5);
	}
	
	@Test
	public void testGetXmlSelectAnyLimitMax20() throws IOException, ParserConfigurationException, SAXException {
		// limited by "queryon.limit.max=20"
		String sql = getSql30rows() + "/* limit-max=25 */";
		String sqlpar = URLEncoder.encode(sql, utf8);
		baseReturnCountTest("/QueryAny.xml?_method=POST&name=test&sql="+sqlpar+"&limit=50", 20);
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
		baseReturnCountTest("/ExplainAny.xml?_method=POST&name=test&sql="+sqlpar, 1);
	}
	
	@Test
	public void testExplainPlanWithParam() throws IOException, ParserConfigurationException, SAXException {
		String sql = "select * from emp where id = ?";
		String sqlpar = URLEncoder.encode(sql, utf8);
		baseReturnCountTest("/ExplainAny.xml?_method=POST&name=test&sql="+sqlpar+"&p1=1", 1);
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
		Assert.assertEquals(2, countNodesWithParentTagName(nl, "schemaModel"));
	}

	@Test @Ignore
	public void testDelete_Dept_Forbidden() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpDelete httpDelete = new HttpDelete(baseUrl+"/DEPT/1");
		HttpResponse response2 = httpclient.execute(httpDelete);
		System.out.println("content: "+getContent(response2));
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
	public void testPut_Dept_Forbidden() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpGet httpPut = new HttpGet(baseUrl+"/DEPT/1?v:NAME=Accounting&_method=PUT");
		
		HttpResponse response1 = httpclient.execute(httpPut);
		System.out.println("content: "+getContent(response1));

		Assert.assertEquals(200, response1.getStatusLine().getStatusCode());
		httpPut.releaseConnection();
	}

	@Test
	public void testExecute() throws IOException, ParserConfigurationException, SAXException, TransformerException {
		String content = httpGetContent("/IS_PRIME?p1=3");
		Assert.assertEquals("true", content);
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
}
