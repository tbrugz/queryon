package tbrugz.queryon.http;

import java.io.IOException;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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

import static tbrugz.queryon.http.JettySetup.*;

public class JettyODataAndH2Test {

	@SuppressWarnings("unused")
	private static final Log log = LogFactory.getLog(JettyODataAndH2Test.class);
	
	public static final String basedir = "src/test/java";
	//public static final String webappdir = "src/main/webapp";
	//public static final String testResourcesDir = "src/test/resources";
	
	static DocumentBuilderFactory dbFactory;
	static DocumentBuilder dBuilder;
	//static String workDir = "work/test/";
	//static final String utf8 = "UTF-8";
	
	//static final String LF = "\r\n";
	
	static final String odataUrl = qonUrl + "/odata";
	
	//static final String currentUserUrl = "/qauth/currentUser";
	
	@BeforeClass
	public static void setup() throws Exception {
		setupH2();
		JettySetup.setupServer("/WEB-INF/web-odata.xml");
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
	
	@Test
	public void getDepts() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/PUBLIC.DEPT";
		
		String jsonStr = ODataWebTest.getContentFromUrl(url);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		/*JSONObject jobj = (JSONObject) obj;
		
		obj = jobj.get("@odata.context");
		Assert.assertTrue(obj.toString().endsWith("/$metadata#PUBLIC.DEPT"));
		
		obj = jobj.get("value");
		Assert.assertTrue("Should be a JSONArray", obj instanceof JSONArray);
		
		JSONArray jarr = (JSONArray) obj;
		Assert.assertEquals(3, jarr.size());*/
	}

}
