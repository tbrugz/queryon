package tbrugz.queryon.http;

import static tbrugz.queryon.http.JettySetup.qonUrl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.List;

import javax.naming.NamingException;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.edm.xml.XMLMetadata;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmEntityContainer;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmSchema;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityContainer;
import org.apache.olingo.commons.api.edm.provider.CsdlEntitySet;
import org.apache.olingo.commons.api.edm.provider.CsdlSchema;
import org.apache.olingo.commons.api.format.ContentType;
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
import tbrugz.sqldump.util.IOUtil;

public class ODataWebTest {

	private static final Log log = LogFactory.getLog(ODataWebTest.class);
	
	public static final String basedir = "src/test/java";

	//static String workDir = "work/test/";
	//static String utf8 = "UTF-8";
	static final String odataUrl = qonUrl + "/odata";
	
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

	//---------------------
	
	public static String getContent(HttpResponse response) throws IllegalStateException, IOException {
		HttpEntity entity = response.getEntity();
		if(entity==null) { return ""; }
		InputStream instream = entity.getContent();
		return IOUtil.readFromReader(new InputStreamReader(instream));
	}
	
	public static String getContentFromUrl(String url) throws ClientProtocolException, IOException {
		HttpClient httpclient = AbstractWebTest.getHttpClient();
		HttpGet httpGet = new HttpGet(url);
		HttpResponse response1 = httpclient.execute(httpGet);
		String content = getContent(response1);
		if(response1.getStatusLine().getStatusCode()>=400) {
			//System.out.println("content:: "+content);
			throw new RuntimeException(response1.getStatusLine().getStatusCode()+": "+content);
		}
		return content;
	}
	
	//---------------------
	
	@Test
	public void getServiceDocument() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/";
		
		String jsonStr = getContentFromUrl(url);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		
		obj = jobj.get("@odata.context");
		Assert.assertTrue(obj.toString().endsWith("/$metadata")); //#relation
		
		obj = jobj.get("value");
		Assert.assertTrue("Should be a JSONArray", obj instanceof JSONArray);
	}

	@Test
	public void getDepts() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/PUBLIC.DEPT";
		
		String jsonStr = getContentFromUrl(url);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		
		obj = jobj.get("@odata.context");
		Assert.assertTrue(obj.toString().endsWith("/$metadata#PUBLIC.DEPT"));
		
		obj = jobj.get("value");
		Assert.assertTrue("Should be a JSONArray", obj instanceof JSONArray);
		
		JSONArray jarr = (JSONArray) obj;
		Assert.assertEquals(3, jarr.size());
	}

	@Test
	public void getDeptsWithFilter() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/PUBLIC.DEPT?$filter=NAME+eq+'HR'";
		
		String jsonStr = getContentFromUrl(url);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		
		obj = jobj.get("@odata.context");
		Assert.assertTrue(obj.toString().endsWith("/$metadata#PUBLIC.DEPT"));
		
		obj = jobj.get("value");
		Assert.assertTrue("Should be a JSONArray", obj instanceof JSONArray);
		
		JSONArray jarr = (JSONArray) obj;
		Assert.assertEquals(1, jarr.size());
	}
	
	@Test
	public void getDeptByKey() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/PUBLIC.DEPT(2)";
		
		String jsonStr = getContentFromUrl(url);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		
		obj = jobj.get("@odata.context");
		Assert.assertTrue(obj.toString().endsWith("/$metadata#PUBLIC.DEPT"));
		
		Object prop = jobj.get("ID");
		Assert.assertEquals(2L, prop);
		prop = jobj.get("NAME");
		Assert.assertEquals("Engineering", prop);
		prop = jobj.get("PARENT_ID");
		Assert.assertEquals(0L, prop);
	}

	@Test
	public void getPairByKey() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/PUBLIC.PAIR(ID1=1,ID2=3)";
		
		String jsonStr = getContentFromUrl(url);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		
		obj = jobj.get("@odata.context");
		Assert.assertTrue(obj.toString().endsWith("/$metadata#PUBLIC.PAIR"));
		
		Object prop = jobj.get("ID1");
		Assert.assertEquals(1L, prop);
		prop = jobj.get("ID2");
		Assert.assertEquals(3L, prop);
		prop = jobj.get("REMARKS");
		Assert.assertEquals("some text", prop);
	}

	@Test
	public void getPairByKeyAnotherOrder() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/PUBLIC.PAIR(ID2=3,ID1=1)";
		
		String jsonStr = getContentFromUrl(url);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		
		obj = jobj.get("@odata.context");
		Assert.assertTrue(obj.toString().endsWith("/$metadata#PUBLIC.PAIR"));
		
		Object prop = jobj.get("ID1");
		Assert.assertEquals(1L, prop);
		prop = jobj.get("ID2");
		Assert.assertEquals(3L, prop);
		prop = jobj.get("REMARKS");
		Assert.assertEquals("some text", prop);
	}
	
	@Test
	public void getQueryPositionalParams() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/NAMED_PARAMS_1(p1=1,p2=2,p3=3)";
		
		String jsonStr = getContentFromUrl(url);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		JSONArray jarr = (JSONArray) jobj.get("value");
		
		jobj = (JSONObject) jarr.get(0);
		Object prop = jobj.get("C1");
		Assert.assertEquals("1", prop);
		
		jobj = (JSONObject) jarr.get(1);
		prop = jobj.get("C1");
		Assert.assertEquals("2", prop);
		
		jobj = (JSONObject) jarr.get(2);
		prop = jobj.get("C1");
		Assert.assertEquals("3", prop);
	}

	@Test
	//@Ignore("ODataServlet does not handle named parameters yet")
	public void getQueryNamedParams() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/NAMED_PARAMS_1(par1=1,par2=2)";
		
		String jsonStr = getContentFromUrl(url);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;
		JSONArray jarr = (JSONArray) jobj.get("value");
		
		jobj = (JSONObject) jarr.get(0);
		Object prop = jobj.get("C1");
		Assert.assertEquals("1", prop);
		
		jobj = (JSONObject) jarr.get(1);
		prop = jobj.get("C1");
		Assert.assertEquals("2", prop);
		
		jobj = (JSONObject) jarr.get(2);
		prop = jobj.get("C1");
		Assert.assertEquals("1", prop);
	}
	
	@Test
	public void createEmp() throws Exception {
		HttpClient httpclient = AbstractWebTest.getHttpClient();
		HttpPost httpPost = new HttpPost(odataUrl+"/EMP");
		String json = "{\"ID\": 10, \"NAME\": \"Bill\", \"SUPERVISOR_ID\": 1, \"DEPARTMENT_ID\": 1, \"SALARY\": 4000}";
		httpPost.setEntity(new StringEntity(json));
		
		HttpResponse response1 = httpclient.execute(httpPost);
		
		Assert.assertEquals(201, response1.getStatusLine().getStatusCode());
		httpPost.releaseConnection();
	}
	
	@Test
	public void updateEmp() throws Exception {
		HttpClient httpclient = AbstractWebTest.getHttpClient();
		HttpPatch httpPatch = new HttpPatch(odataUrl+"/EMP(5)");
		String json = "{\"SALARY\": 2500}";
		httpPatch.setEntity(new StringEntity(json));
		
		HttpResponse response1 = httpclient.execute(httpPatch);
		
		Assert.assertEquals(204, response1.getStatusLine().getStatusCode());
		String content = getContent(response1);
		Assert.assertEquals("", content);

		Header header = response1.getFirstHeader("X-UpdateCount");
		Assert.assertEquals("1", header.getValue());

		/*Header[] headers = response1.getAllHeaders();
		for(Header h: headers) {
			System.out.println(h.getName()+" / "+h.getValue());
		}*/
		
		httpPatch.releaseConnection();
	}

	@Test
	public void deleteEmp() throws Exception {
		HttpClient httpclient = AbstractWebTest.getHttpClient();
		HttpDelete httpDel = new HttpDelete(odataUrl+"/EMP(3)");
		
		HttpResponse response1 = httpclient.execute(httpDel);
		
		Assert.assertEquals(204, response1.getStatusLine().getStatusCode());
		String content = getContent(response1);
		Assert.assertEquals("", content);

		Header header = response1.getFirstHeader("X-UpdateCount");
		Assert.assertEquals("1", header.getValue());

		/*Header[] headers = response1.getAllHeaders();
		for(Header h: headers) {
			System.out.println(h.getName()+" / "+h.getValue());
		}*/
		
		httpDel.releaseConnection();
	}

	@Test
	public void beanSingletonQueryCurrentUser() throws IOException, ParserConfigurationException, SAXException {
		String url = odataUrl+"/currentUser";
		
		String jsonStr = getContentFromUrl(url);
		//System.out.println("content:\n"+jsonStr);

		Object obj = JSONValue.parse(jsonStr);
		Assert.assertTrue("Should be a JSONObject", obj instanceof JSONObject);
		
		JSONObject jobj = (JSONObject) obj;

		Object prop = jobj.get("username");
		Assert.assertEquals("anonymous", prop);
	}
	
	/*
	 * see: https://olingo.apache.org/doc/odata4/tutorials/od4_basic_client_read.html
	 */
	ODataClient getODataClient() {
		ODataClient client = ODataClientFactory.getClient();
		client.getConfiguration().setDefaultPubFormat(ContentType.APPLICATION_JSON);
		return client;
	}
	
	@Test
	public void odataMetadataRequest() {
		//ODataClient client = ODataClientFactory.getClient();
		//client.getConfiguration().setDefaultPubFormat(ContentType.APPLICATION_JSON);
		Edm edm = getODataClient().getRetrieveRequestFactory().getMetadataRequest(odataUrl).execute().getBody();
		EdmEntityContainer eec = edm.getEntityContainer();
		List<EdmEntitySet> ees = eec.getEntitySets();
		log.info("List<EdmEntitySet>: "+ees);
		for(EdmEntitySet e: ees) {
			log.info(" - "+e.getName());
			//log.info(" - "+e.getName()+" / "+e.getEntityType());
		}
		
		List<EdmSchema> es = edm.getSchemas();
		log.info("List<EdmSchema>: "+es);
		for(EdmSchema s: es) {
			log.info(" - "+s.getAlias()+" / "+s.getNamespace());
		}
	}
	
	@Test
	public void odataXMLMetadataRequest() {
		XMLMetadata xmlMetadata = getODataClient().getRetrieveRequestFactory().getXMLMetadataRequest(odataUrl).execute().getBody();
		log.info("EdmVersion: "+xmlMetadata.getEdmVersion());
		List<CsdlSchema> schemas = xmlMetadata.getSchemas();
		for(CsdlSchema s: schemas) {
			log.info("CsdlSchema: "+s.getAlias()+" / "+s.getNamespace());
			CsdlEntityContainer ec = s.getEntityContainer();
			List<CsdlEntitySet> es = ec.getEntitySets();
			for(CsdlEntitySet e: es) {
				//log.info(" - "+e.getName());
				log.info(" - "+e.getName()+" / "+e.getType()+" / "+e.getTypeFQN());
			}
		}
	}

}
