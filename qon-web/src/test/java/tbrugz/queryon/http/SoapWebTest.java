package tbrugz.queryon.http;

import static tbrugz.queryon.http.JettySetup.qonUrl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.tools.common.ToolContext;
import org.apache.cxf.tools.wsdlto.WSDLToJava;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.sun.xml.ws.api.model.wsdl.WSDLModel;
import com.sun.xml.ws.api.server.SDDocumentSource;
import com.sun.xml.ws.api.wsdl.parser.WSDLParserExtension;
import com.sun.xml.ws.api.wsdl.parser.XMLEntityResolver;

import tbrugz.sqldump.sqlrun.SQLRun;
import tbrugz.sqldump.util.IOUtil;

public class SoapWebTest {

	private static final Log log = LogFactory.getLog(SoapWebTest.class);
	
	public static final String basedir = "src/test/java";
	public static final String outdir = "work/output";

	public static final String generatedCodeDir = "target/test/java/";
	
	static final String soaplUrl = qonUrl + "/soap";
	
	static DocumentBuilderFactory dbFactory;
	static DocumentBuilder dBuilder;
	
	@BeforeClass
	public static void setup() throws Exception {
		setupH2();
		JettySetup.setupServer();
		
		File dir = new File(outdir);
		dir.mkdirs();
		
		dbFactory = DocumentBuilderFactory.newInstance();
		dBuilder = dbFactory.newDocumentBuilder();
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
	
	class TestXMLEntityResolver implements XMLEntityResolver {
		@Override
		public Parser resolveEntity(String publicId, String systemId)
				throws SAXException, IOException, XMLStreamException {
			//System.out.println("publicId: "+publicId+" / systemId: "+systemId);
			return null;
		}
	}

	//---------------------
	
	public static String getContent(HttpResponse response) throws IllegalStateException, IOException {
		HttpEntity entity = response.getEntity();
		if(entity==null) { return ""; }
		InputStream instream = entity.getContent();
		return IOUtil.readFromReader(new InputStreamReader(instream));
	}
	
	static InputStream httpGetContentStream(String url) throws ClientProtocolException, IOException {
		HttpClient httpclient = AbstractWebTest.getHttpClient();
		HttpGet httpReq = new HttpGet(url);
		HttpResponse response1 = httpclient.execute(httpReq);
		HttpEntity entity1 = response1.getEntity();
		InputStream instream = entity1.getContent();
		return instream;
	}
	
	static String httpGetContent(String url) throws ClientProtocolException, IOException {
		HttpClient httpclient = AbstractWebTest.getHttpClient();
		HttpGet httpReq = new HttpGet(url);
		HttpResponse response1 = httpclient.execute(httpReq);
		String content = getContent(response1);
		httpReq.releaseConnection();
		if(response1.getStatusLine().getStatusCode()>=400) {
			throw new RuntimeException(response1.getStatusLine().getStatusCode()+": "+content);
		}
		return content;
	}

	static InputStream httpPostContentStream(String url, String body) throws ClientProtocolException, IOException {
		HttpClient httpclient = AbstractWebTest.getHttpClient();
		HttpPost httpReq = new HttpPost(url);
		httpReq.setEntity(new StringEntity(body));
		HttpResponse response1 = httpclient.execute(httpReq);
		HttpEntity entity1 = response1.getEntity();
		InputStream instream = entity1.getContent();
		return instream;
	}
	
	//---------------------
	
	@Test
	public void getWsdl() throws ClientProtocolException, IOException, SAXException {
		InputStream instream = httpGetContentStream(soaplUrl+"?wsdl");
		Document doc = dBuilder.parse(instream);
		Assert.assertNotNull(doc);
		
		//String content = httpGetContent(soaplUrl+"?wsdl");
		//System.out.println(content);
	}

	//@Ignore
	@Test
	public void getWsdlAsString() throws ClientProtocolException, IOException, SAXException {
		String content = httpGetContent(soaplUrl+"?wsdl");
		//System.out.println(content);
		String fileout = outdir+"/soaptest.wsdl";
		FileWriter fw = new FileWriter(fileout);
		fw.write(content);
		fw.close();
		//log.info(content);
		log.info("wsdl generated at "+fileout);
	}
	
	// see: https://stackoverflow.com/questions/2917130/is-there-any-easy-way-to-perform-junit-test-for-wsdl-ws-i-compliance
	@Test
	public void parseAndValidateWSDLWithJaxWS() throws IOException, XMLStreamException, SAXException {
		final URL url = new URL(soaplUrl+"?wsdl");
		log.info(url);
		final SDDocumentSource doc = SDDocumentSource.create(url);
		final XMLEntityResolver.Parser parser = new XMLEntityResolver.Parser(doc);
		WSDLModel model = WSDLModel.WSDLParser.parse(parser, new TestXMLEntityResolver(), false, new WSDLParserExtension[] {});
		Assert.assertNotNull(model);
	}

	@Test
	public void request1() throws IOException, XMLStreamException, SAXException {
		String body = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:quer=\"http://bitbucket.org/tbrugz/queryon/queryOnService.xsd\">\n" + 
				"	   <soapenv:Header/>\n" + 
				"	   <soapenv:Body>\n" + 
				"	      <quer:QUERY.EMP_Q1Request>\n" + 
				"	         <limit>2</limit>\n" + 
				"	      </quer:QUERY.EMP_Q1Request>\n" + 
				"	   </soapenv:Body>\n" + 
				"	</soapenv:Envelope>";
		InputStream is = httpPostContentStream(soaplUrl, body);
		String s = IOUtil.readFromReader(new InputStreamReader(is));
		log.info("request1: response="+s);
	}
	
	@Test
	public void requestQueryWithParams() throws IOException, XMLStreamException, SAXException {
		String body = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:quer=\"http://bitbucket.org/tbrugz/queryon/queryOnService.xsd\">\n" + 
				"	   <soapenv:Header/>\n" + 
				"	   <soapenv:Body>\n" + 
				"	      <quer:QUERY.EMP_Q1Request>\n" + 
				"	         <limit>2</limit>\n" + 
				"	      </quer:QUERY.EMP_Q1Request>\n" + 
				"	   </soapenv:Body>\n" + 
				"	</soapenv:Envelope>";
		InputStream is = httpPostContentStream(soaplUrl, body);
		String s = IOUtil.readFromReader(new InputStreamReader(is));
		log.info("requestQueryWithParams: response="+s);
	}
	
	// https://cxf.apache.org/docs/wsdl-to-java.html
	@Test
	public void generateJavaFromWsdl() throws Exception {
		//'-validate' fixed on java 8_b96 ? https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8016153
		// java.version: 1.8.0_92 / java.version: 1.8.0_191
		log.info("java.version: "+System.getProperty("java.version"));
		String url = soaplUrl+"?wsdl";
		//String[] args = { "-d", generatedCodeDir, url };
		//String[] args = { "-verbose", "-mark-generated", "-d", generatedCodeDir, url };
		String[] args = { "-verbose", "-all", "-d", generatedCodeDir, url };
		//String[] args = { "-validate", "-verbose", "-d", generatedCodeDir, url };
		
		//WSDLToJava.main(args); //do not generate exceptions...
		WSDLToJava w2j = new WSDLToJava(args);
		w2j.run(new ToolContext());
		
		// XXX compile? add to (test) classpath?
	}

}
