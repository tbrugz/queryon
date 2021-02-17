package tbrugz.queryon.http;

import static tbrugz.queryon.http.JettySetup.qonUrl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.impl.SardineException;

import tbrugz.sqldump.sqlrun.SQLRun;
import tbrugz.sqldump.util.StringUtils;

public class WebDavWebTest {

	private static final Log log = LogFactory.getLog(WebDavWebTest.class);
	
	public static final String basedir = "src/test/java";

	//static String workDir = "work/test/";
	//static String utf8 = "UTF-8";
	static final String webdavCtx = "/webdav";
	static final String webdavUrl = qonUrl + webdavCtx;
	
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
	
	/*class PropFindMethod extends EntityEnclosingMethod {
		PropFindMethod(String uri) {
			super(uri);
		}

		@Override
		public String getName() {
			return "PROPFIND";
		}
	}*/
	
	class HttpPropFind extends HttpGet {
		public final static String METHOD_NAME = "PROPFIND";

		public HttpPropFind(final String uri) {
			super();
			setURI(URI.create(uri));
		}

		@Override
		public String getMethod() {
			return METHOD_NAME;
		}
	}

	void callPropFind(String url) throws HttpException, IOException {
		HttpClient client = HttpClients.createDefault();
		HttpPropFind pfm = new HttpPropFind(url);
		HttpResponse response1 = client.execute(pfm);
		int code = response1.getStatusLine().getStatusCode();
		log.info("code = "+code);
		String response = AbstractWebTest.getContent(response1);
		//String response = pfm.getResponseBodyAsString();
		log.info("response = "+response);
	}

	static String httpHeadContent(String url) throws IllegalStateException, IOException {
		return httpHeadContent(url, 200);
	}
	
	static String httpHeadContent(String url, int expectedStatus) throws IllegalStateException, IOException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpHead http = new HttpHead(url);
		HttpResponse response = httpclient.execute(http);
		Assert.assertEquals(expectedStatus, response.getStatusLine().getStatusCode());
		return WinstoneAndH2HttpRequestTest.getContent(response);
	}
	
	/*void callHead(String url) throws HttpException, IOException {
		HttpClient client = new HttpClient();
		int code = client.executeMethod(pfm);
		log.info("code = "+code);
		String response = pfm.getResponseBodyAsString();
		log.info("response = "+response);
	}*/
	
	void assertListCount(List<DavResource> resources, int expected, boolean allShouldBeCollection) {
		int count = 0;
		for (DavResource res : resources) {
			log.info(res+" ; name="+res.getName()+" , path="+res.getPath()+" , status="+res.getStatusCode()+" , directory="+res.isDirectory()+" , contentType="+res.getContentType()+", contentLength="+res.getContentLength());
			if(allShouldBeCollection) {
				Assert.assertTrue("path '"+res.getName()+"' should be directory", res.isDirectory());
			}
			count++;
		}
		Assert.assertEquals(expected, count);
	}

	void assertListIn(List<DavResource> resources, List<String> expectedValues) {
		for (DavResource res : resources) {
			log.info(res+" ; name="+res.getName()+" , path="+res.getPath()+" , status="+res.getStatusCode()+" , directory="+res.isDirectory()+" , contentType="+res.getContentType()+", contentLength="+res.getContentLength());
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
		assertListCount(resources, 5, true);
	}
	
	@Test
	public void sardineListEmp() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.EMP");
		assertListCount(resources, 6, true);
	}

	@Test
	public void sardineListEmpId1_FullKey() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.EMP" + "/1");
		assertListCount(resources, 6, false);
	}

	@Test
	public void sardineListEmpId1_Name() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.EMP" + "/1" + "/NAME"); //john
		/*for(DavResource r: resources) {
			System.out.println(r.getPath());
		}*/
		assertListCount(resources, 1, false);
		assertListIn(resources, Arrays.asList(new String[]{ webdavCtx + "/" + "PUBLIC.EMP" + "/1" + "/NAME" }));
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

	@Test @Ignore
	public void sardineLocalTest() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list("http://localhost:8080/qon-demo-minimal/webdav");
		assertListCount(resources, 4, true);
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
		assertListCount(resources, 3, true);
		assertListIn(resources, Arrays.asList(new String[]{webdavCtx + "/PAIR/", webdavCtx + "/PAIR" + "/1", webdavCtx + "/PAIR" + "/4"}));
	}

	@Test
	public void sardineListPairId1() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.PAIR" + "/" + "1");
		assertListCount(resources, 3, true);
	}

	@Test
	public void sardineListPairId4() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.PAIR" + "/" + "4");
		assertListCount(resources, 2, true);
	}

	@Test
	public void sardineListPairId4and5_FullKey() throws IOException {
		Sardine sardine = SardineFactory.begin();
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.PAIR" + "/" + "4" + "/" + "5");
		assertListCount(resources, 4, false);
		assertListIn(resources, Arrays.asList(new String[]{
				webdavCtx + "/PUBLIC.PAIR" + "/4" + "/5" + "/",
				webdavCtx + "/PUBLIC.PAIR" + "/4" + "/5" + "/ID1",
				webdavCtx + "/PUBLIC.PAIR" + "/4" + "/5" + "/ID2",
				webdavCtx + "/PUBLIC.PAIR" + "/4" + "/5" + "/REMARKS"}));
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
	public void callPropFindRoot() throws HttpException, IOException {
		callPropFind(webdavUrl);
	}

	@Test
	public void callPropFindDept() throws HttpException, IOException {
		callPropFind(webdavUrl+"/PUBLIC.DEPT");
	}

	@Test
	public void callPropFindDeptId1() throws HttpException, IOException {
		callPropFind(webdavUrl+"/PUBLIC.DEPT/1");
	}

	@Test
	public void sardineGetEmpId1_Name() throws IOException {
		Sardine sardine = SardineFactory.begin();
		InputStream is = sardine.get(webdavUrl + "/" + "PUBLIC.EMP" + "/1" + "/NAME"); //john
		String s = StringUtils.readInputStream(is, 8192);
		//log.info("s == "+s);
		Assert.assertEquals("john", s);
	}
	
	@Test
	public void httpHeadRoot() throws IllegalStateException, IOException {
		httpHeadContent(webdavUrl, 200);
		httpHeadContent(webdavUrl + "/", 200);
	}
	
	@Test
	public void httpPostRoot() throws IllegalStateException, IOException {
		WinstoneAndH2HttpRequestTest.httpPostContent(webdavUrl + "/", "", 404); // XXX method not allowed (405)?
	}
	
	@Test
	public void httpHeadObject() throws IllegalStateException, IOException {
		httpHeadContent(webdavUrl + "/" + "PUBLIC.EMP", 200);
		httpHeadContent(webdavUrl + "/" + "PUBLIC.EMP/", 200);
	}
	
	@Test
	public void httpHeadObjectError() throws IllegalStateException, IOException {
		httpHeadContent(webdavUrl + "/" + "PUBLIC.EMPZ", 404);
	}

	@Test
	public void httpPostObject() throws IllegalStateException, IOException {
		WinstoneAndH2HttpRequestTest.httpPostContent(webdavUrl + "/" + "PUBLIC.EMP/", "", 404); // XXX method not allowed (405)?
	}
	
	@Test
	public void httpHeadPkError() throws IllegalStateException, IOException {
		httpHeadContent(webdavUrl + "/" + "PUBLIC.EMP" + "/1", 200);
	}
	
	@Test
	@Ignore("implementation lacking")
	public void httpHeadPk() throws IllegalStateException, IOException {
		httpHeadContent(webdavUrl + "/" + "PUBLIC.EMP" + "/555", 404);
	}

	@Test
	public void httpHeadColumn() throws IllegalStateException, IOException {
		httpHeadContent(webdavUrl + "/" + "PUBLIC.EMP" + "/1" + "/NAME", 200);
	}

	@Test
	public void httpHeadColumnError() throws IllegalStateException, IOException {
		httpHeadContent(webdavUrl + "/" + "PUBLIC.EMP" + "/1" + "/NONAME", 404);
	}
	
	@Test
	public void sardinePutEmpId1_Name() throws IOException {
		Sardine sardine = SardineFactory.begin();
		String newValue = "paul";
		sardine.put(webdavUrl + "/" + "PUBLIC.EMP" + "/1" + "/NAME", newValue.getBytes());
		
		InputStream is = sardine.get(webdavUrl + "/" + "PUBLIC.EMP" + "/1" + "/NAME"); //john
		String s = StringUtils.readInputStream(is, 8192);
		//log.info("s == "+s);
		Assert.assertEquals("paul", s);
	}

	@Test
	public void sardinePutTask1_Attach() throws IOException {
		Sardine sardine = SardineFactory.begin();
		byte[] binaryData = new byte[] { 0,1,2,3 };
		sardine.put(webdavUrl + "/" + "TASK" + "/1" + "/ATTACH", binaryData);
		
		InputStream is = sardine.get(webdavUrl + "/" + "TASK" + "/1" + "/ATTACH");
		
		byte[] readBinaryData = new byte[binaryData.length];
		is.read(readBinaryData);
		boolean equals = Arrays.equals(binaryData, readBinaryData);
		log.info("equals? "+equals);
		Assert.assertTrue(equals);
	}

	@Test
	public void sardineDeleteEmpId1() throws IOException {
		Sardine sardine = SardineFactory.begin();
		
		List<DavResource> resources = sardine.list(webdavUrl + "/" + "PUBLIC.EMP");
		assertListCount(resources, 6, false);
		
		sardine.delete(webdavUrl + "/" + "PUBLIC.EMP" + "/3");

		List<DavResource> resources2 = sardine.list(webdavUrl + "/" + "PUBLIC.EMP");
		assertListCount(resources2, 5, false);
	}

	@Test
	public void sardineDeleteEmpId1_Salary() throws IOException {
		Sardine sardine = SardineFactory.begin();
		
		InputStream is = sardine.get(webdavUrl + "/" + "PUBLIC.EMP" + "/3" + "/SALARY");
		String s = StringUtils.readInputStream(is, 8192);
		Assert.assertEquals("1000", s);
		
		sardine.delete(webdavUrl + "/" + "PUBLIC.EMP" + "/3" + "/SALARY");

		InputStream is2 = sardine.get(webdavUrl + "/" + "PUBLIC.EMP" + "/3" + "/SALARY");
		String s2 = StringUtils.readInputStream(is2, 8192);
		Assert.assertEquals("", s2);
	}
	
}
