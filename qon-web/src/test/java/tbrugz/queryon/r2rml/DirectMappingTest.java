package tbrugz.queryon.r2rml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.xml.parsers.ParserConfigurationException;

/*
import org.apache.any23.Any23;
import org.apache.any23.extractor.ExtractionException;
import org.apache.any23.source.DocumentSource;
import org.apache.any23.source.StringDocumentSource;
import org.apache.any23.writer.CountingTripleHandler;
import org.apache.any23.writer.TripleHandler;
import org.apache.any23.writer.TripleHandlerException;
import org.apache.any23.writer.TurtleWriter;
*/
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import tbrugz.queryon.http.AbstractWebTest;
import tbrugz.queryon.http.JettySetup;
import tbrugz.queryon.http.WinstoneAndH2HttpRequestTest;
import tbrugz.sqldump.SQLDump;
import tbrugz.sqldump.sqlrun.SQLRun;

import static tbrugz.queryon.http.JettySetup.*;

/**
 * see:
 * http://www.w3.org/TR/rdb-direct-mapping/
 * http://www.w3.org/2001/sw/wiki/Direct_Mapping
 * 
 * http://www.w3.org/TR/r2rml/
 * http://www.w3.org/2001/sw/wiki/RDB2RDF

 * HARDNESS?? any23 2.7 requires java 11 (java11)...
 */
/*
public class DirectMappingTest {

	public static final String basedir = "src/test/java";
	
	@BeforeClass
	public static void setup() throws Exception {
		//setupH2(); //XXX?
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
		String[] params = {"-propfile="+basedir+"/tbrugz/queryon/r2rml/sqlrun.properties"};
		//System.out.println("user.dir = "+System.getProperty("user.dir"));
		SQLRun.main(params);
	}

	//@Test
	//public void nullTest() {}
	
	//XXX: failonerror!
	@Test
	public void dumpTest() throws Exception {
		String[] params = {
				"-propfile="+basedir+"/tbrugz/queryon/r2rml/sqldump.properties",
				"-usesysprop=false"
		};
		SQLDump.main(params);
	}
	
	@SuppressWarnings("unused")
	//@Test
	//@Ignore("properties count is frequantly changinf")
	public void testGet_Turtle_Tables_withAny23() throws IOException, ParserConfigurationException, SAXException, ExtractionException, TripleHandlerException {
		HttpClient httpclient = AbstractWebTest.getHttpClient();
		HttpGet httpGet = new HttpGet(baseUrl+"/table.ttl");
		
		HttpResponse response1 = httpclient.execute(httpGet);
		String turtleStr = WinstoneAndH2HttpRequestTest.getContent(response1);
		DocumentSource source = new StringDocumentSource(turtleStr, baseUrl+"/table.ttl"); //"http://host.com/service");

		Any23 runner = new Any23();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TripleHandler handler = new TurtleWriter(out);
		CountingTripleHandler countingHandler = new CountingTripleHandler(false);
		//ReportingTripleHandler reportHandler = new ReportingTripleHandler(handler);
		
		try {
			runner.extract(source, handler);
			//runner.extract(source, reportHandler);
			runner.extract(source, countingHandler);
		} finally {
			handler.close();
			//reportHandler.close();
			countingHandler.close();
		}
		
		String n3 = out.toString("UTF-8");

		//System.out.println("before:\n"+turtleStr);
		//System.out.println("after:\n"+n3);
		//System.out.println("count: "+countingHandler.getCount());

		//System.out.println("report:\n"+reportHandler.printReport());
		
		//int propCount = 6;
		//Assert.assertEquals("Should have "+(3*propCount)+" triples (3 rows x "+propCount+" properties)", (3*propCount), countingHandler.getCount());
		
		httpGet.releaseConnection();
	}

}
*/
