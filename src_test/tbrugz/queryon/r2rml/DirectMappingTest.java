package tbrugz.queryon.r2rml;

import java.io.IOException;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import tbrugz.queryon.http.TestSetup;
import tbrugz.sqldump.SQLDump;
import tbrugz.sqldump.sqlrun.SQLRun;

/**
 * see:
 * http://www.w3.org/TR/rdb-direct-mapping/
 * http://www.w3.org/TR/r2rml/
 */
public class DirectMappingTest {

	@BeforeClass
	public static void setup() throws IOException, ParserConfigurationException {
		TestSetup.setupWinstone();
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

	public static void setupH2() throws ClassNotFoundException, IOException, SQLException, NamingException {
		String[] params = {"-propfile=src_test/tbrugz/queryon/r2rml/sqlrun.properties"};
		SQLRun.main(params);
	}

	//@Test
	//public void nullTest() {}
	
	@Test
	public void dumpTest() throws Exception {
		String[] params = {
				"-propfile=src_test/tbrugz/queryon/r2rml/sqldump.properties",
				"-usesysprop=false"
		};
		SQLDump.main(params);
	}

}
