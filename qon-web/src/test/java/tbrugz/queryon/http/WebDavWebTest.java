package tbrugz.queryon.http;

import static tbrugz.queryon.http.JettySetup.qonUrl;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import javax.naming.NamingException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.impl.SardineException;

import tbrugz.sqldump.sqlrun.SQLRun;

public class WebDavWebTest {

	private static final Log log = LogFactory.getLog(WebDavWebTest.class);
	
	public static final String basedir = "src/test/java";

	//static String workDir = "work/test/";
	//static String utf8 = "UTF-8";
	static final String webdavUrl = qonUrl + "/webdav";
	
	@BeforeClass
	public static void setup() throws Exception {
		setupH2();
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
		String[] params = {"-propfile="+basedir+"/tbrugz/queryon/http/sqlrun.properties"};
		SQLRun.main(params);
	}
	
	class PropFindMethod extends EntityEnclosingMethod {
		PropFindMethod(String uri) {
			super(uri);
		}

		@Override
		public String getName() {
			return "PROPFIND";
		}
	}

	void callPropFind(String url) throws HttpException, IOException {
		HttpClient client = new HttpClient();
		PropFindMethod pfm = new PropFindMethod(url);
		int code = client.executeMethod(pfm);
		log.info("code = "+code);
		String response = pfm.getResponseBodyAsString();
		log.info("response = "+response);
	}
	
	void assertListCount(List<DavResource> resources, int expected, boolean allShouldBeCollection) {
		int count = 0;
		for (DavResource res : resources) {
			log.info(res+" ; name="+res.getName()+" , path="+res.getPath()+" , status="+res.getStatusCode()+" , directory="+res.isDirectory()+" , contentType="+res.getContentType());
			if(allShouldBeCollection) {
				Assert.assertTrue("path '"+res.getName()+"' should be directory", res.isDirectory());
			}
			count++;
		}
		Assert.assertEquals(expected, count);
	}

	void assertListIn(List<DavResource> resources, List<String> expectedValues) {
		for (DavResource res : resources) {
			log.info(res+" ; name="+res.getName()+" , path="+res.getPath()+" , status="+res.getStatusCode()+" , directory="+res.isDirectory()+" , contentType="+res.getContentType());
			if(!expectedValues.contains(res.getPath())) {
				Assert.fail("list should not contain '"+res.getPath()+"' path");
			}
		}
	}
	
	// https://github.com/lookfirst/sardine
	@Test
	public void sardineListRoot() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl);
		assertListCount(resources, 4, true);
	}

	@Test
	public void sardineListEmp() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.EMP");
		assertListCount(resources, 5, true);
	}

	@Test
	public void sardineListEmpId1_FullKey() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.EMP" + "/1");
		assertListCount(resources, 5, false);
	}

	@Test
	public void sardineListEmpId1_Name() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.EMP" + "/1" + "/NAME"); //john
		assertListCount(resources, 1, false);
		assertListIn(resources, Arrays.asList(new String[]{"NAME"}));
	}

	@Test
	public void sardineListUnknownResource() throws IOException {
		Sardine sardine = SardineFactory.begin();
		try {
			sardine.list(webdavUrl + "/" + "XYZ");
			Assert.fail("Should have thrown an exception");
		}
		catch(SardineException e) {
			int status = e.getStatusCode();
			Assert.assertEquals(404, status);
		}
	}

	@Test
	public void sardineListEmpUnknownResource() throws IOException {
		Sardine sardine = SardineFactory.begin();
		try {
			List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.EMP" + "/101");
			log.info("resources = "+resources);
			Assert.fail("Should have thrown an exception");
		}
		catch(SardineException e) {
			int status = e.getStatusCode();
			Assert.assertEquals(404, status);
		}
	}
	
	@Test
	public void sardineListPair() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PAIR");
		assertListCount(resources, 2, true);
		assertListIn(resources, Arrays.asList(new String[]{"1","4"}));
	}

	@Test
	public void sardineListPairId1() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.PAIR" + "/" + "1");
		assertListCount(resources, 2, true);
	}

	@Test
	public void sardineListPairId4() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.PAIR" + "/" + "4");
		assertListCount(resources, 1, true);
	}

	@Test
	public void sardineListPairId4and5_FullKey() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.PAIR" + "/" + "4" + "/" + "5");
		assertListCount(resources, 3, false);
		assertListIn(resources, Arrays.asList(new String[]{"ID1","ID2","REMARKS"}));
	}
	
	@Test
	public void sardineListPairUnknownPartialKey() throws IOException {
		Sardine sardine = SardineFactory.begin();
		try {
			List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.PAIR" + "/" + "10");
			log.info("resources = "+resources);
			Assert.fail("Should have thrown an exception");
		}
		catch(SardineException e) {
			int status = e.getStatusCode();
			Assert.assertEquals(404, status);
		}
	}

	@Test
	public void sardineListPairId4UnknownResource() throws IOException {
		Sardine sardine = SardineFactory.begin();
		try {
			List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.PAIR" + "/4" + "/101");
			log.info("resources = "+resources);
			Assert.fail("Should have thrown an exception");
		}
		catch(SardineException e) {
			int status = e.getStatusCode();
			Assert.assertEquals(404, status);
		}
	}
	
	@Test
	public void callPropFindRoot() throws IOException {
		callPropFind(webdavUrl);
	}

	@Test
	public void callPropFindDept() throws IOException {
		callPropFind(webdavUrl+"/PUBLIC.DEPT");
	}

	@Test
	public void callPropFindDeptId1() throws IOException {
		callPropFind(webdavUrl+"/PUBLIC.DEPT/1");
	}
	
}
