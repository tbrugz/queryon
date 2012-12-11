package tbrugz.queryon.http;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.naming.NamingException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import tbrugz.sqldump.sqlrun.SQLRun;
import winstone.Launcher;

public class WinstoneAndH2HttpRequestTest {
	
	final static int port = 8889;
	final static String baseUrl = "http://localhost:"+port;

	static DocumentBuilderFactory dbFactory;
	static DocumentBuilder dBuilder;
	
	static Launcher winstone = null;
	
	@BeforeClass
	public static void setupWinstone() throws IOException, ParserConfigurationException {
		Map<String, String> args = new HashMap<String, String>();
		args.put("webroot", "bin/tbrugz/queryon/http"); // or any other command line args, eg port
		args.put("httpPort", ""+port);
		args.put("ajp13Port", "-1");
		Launcher.initLogger(args);
		winstone = new Launcher(args); // spawns threads, so your application doesn't block
		
		dbFactory = DocumentBuilderFactory.newInstance();
		dBuilder = dbFactory.newDocumentBuilder();
	}
	
	@BeforeClass
	public static void setupH2() throws ClassNotFoundException, IOException, SQLException, NamingException {
		String[] params = {"-propfile=src_test/tbrugz/queryon/http/sqlrun.properties"};
		SQLRun.main(params);
	}
	
	@AfterClass
	public static void shutdown() {
		winstone.shutdown(); 
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

	@Test
	public void testPost_Emp_400() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		HttpPost httpPost = new HttpPost(baseUrl+"/EMP?v:ID=11");
		
		HttpResponse response1 = httpclient.execute(httpPost);
		
		Assert.assertEquals("Must be a Bad Request (NAME value not provided)", 400, response1.getStatusLine().getStatusCode());
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
	public void testPut_Emp_OK() throws IOException, ParserConfigurationException, SAXException {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		//HttpGet httpPut = new HttpGet(baseUrl+"/EMP?v:NAME=newname&feq:ID=1&method=PUT");
		HttpGet httpPut = new HttpGet(baseUrl+"/EMP/1?v:NAME=newname&method=PUT");

		/*List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("v:NAME", "newname"));
		httpPut.setEntity(new UrlEncodedFormEntity(nvps));*/
		
		HttpResponse response1 = httpclient.execute(httpPut);
		
		Assert.assertEquals("Must be OK (updated)", 200, response1.getStatusLine().getStatusCode());
		httpPut.releaseConnection();
	}
}
