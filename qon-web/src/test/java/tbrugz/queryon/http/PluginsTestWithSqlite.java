package tbrugz.queryon.http;

import static tbrugz.queryon.http.JettySetup.baseUrl;
import static tbrugz.queryon.http.JettySetup.qonUrl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.xml.parsers.ParserConfigurationException;

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

public class PluginsTestWithSqlite {

	public static final String basedir = "src/test/java";

	static final String graphqlUrl = qonUrl + "/graphql";
	
	@BeforeClass
	public static void setup() throws Exception {
		setupDatabase();
		JettySetup.setupServer("/WEB-INF/web-plugins-sqlite.xml");
	}
	
	@AfterClass
	public static void shutdown() throws Exception {
		JettySetup.shutdown();
		shutdownDatabase();
	}

	@Before
	public void before() throws ClassNotFoundException, IOException, SQLException, NamingException {
	}
	
	@After
	public void after() {
	}
	
	public static void setupDatabase() throws ClassNotFoundException, IOException, SQLException, NamingException {
		String[] params = {"-propfile="+basedir+"/tbrugz/queryon/http/sqlrun-sqlite.properties"};
		SQLRun.main(params);
	}

	public static void shutdownDatabase() throws IOException {
		Files.deleteIfExists(Path.of("./target/qon-sqlite.db"));
	}
	
	// -----

	@Test
	public void testAddAndQueryQonQueries() throws IOException, ParserConfigurationException, SAXException {
		int status = PluginsTest.addQonQuery("TEST", "TEST1", "select ID, NAME from EMP");
		Assert.assertEquals("Must be 201/Created", 201, status);

		String url = "/relation.json";
		String jsonStr = WinstoneAndH2HttpRequestTest.getContentFromUrl(baseUrl+url);
		
		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		obj = jobj.get("data");
		Assert.assertTrue("Should be a JSONArray", obj instanceof JSONArray);

		JSONArray jarr = (JSONArray) obj;
		Assert.assertTrue("Should have Query named TEST1", PluginsTest.containRowWithProperty(jarr, "name", "TEST1"));
	}

}
