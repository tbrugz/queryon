package tbrugz.queryon.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;

import javax.naming.NamingException;

import org.apache.http.HttpConnectionFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.RequestLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.DefaultBHttpServerConnection;
import org.apache.http.impl.DefaultBHttpServerConnectionFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.io.DefaultHttpRequestParserFactory;
import org.apache.http.io.HttpMessageParserFactory;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import tbrugz.sqldump.sqlrun.SQLRun;
import tbrugz.sqldump.util.IOUtil;

public abstract class AbstractWebTest {

	public static final String basedir = "src/test/java";

	//static String workDir = "work/test/";
	//static String utf8 = "UTF-8";
	
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

	public static CloseableHttpClient getHttpClient() {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		return httpclient;
	}
	
	public static String getContent(HttpResponse response) throws IllegalStateException, IOException {
		HttpEntity entity = response.getEntity();
		if(entity==null) { return ""; }
		InputStream instream = entity.getContent();
		return IOUtil.readFromReader(new InputStreamReader(instream));
	}
	
	public static String getContentFromUrl(String url) throws ClientProtocolException, IOException {
		// https://stackoverflow.com/questions/15336477/deprecated-java-httpclient-how-hard-can-it-be
		HttpClient httpclient = getHttpClient();
		HttpGet httpGet = new HttpGet(url);
		HttpResponse response1 = httpclient.execute(httpGet);
		String content = getContent(response1);
		if(response1.getStatusLine().getStatusCode()>=400) {
			//System.out.println("content:: "+content);
			throw new RuntimeException(response1.getStatusLine().getStatusCode()+": "+content);
		}
		return content;
	}
	
	// https://stackoverflow.com/a/20460548
	HttpConnectionFactory<DefaultBHttpServerConnection> getConnectionFactory() {
		HttpRequestFactory reqFact = new HttpRequestFactory() {
			public HttpRequest newHttpRequest(final RequestLine requestline) throws MethodNotSupportedException {
				return new BasicHttpEntityEnclosingRequest(requestline);
			}

			public HttpRequest newHttpRequest(final String method, final String uri)
					throws MethodNotSupportedException {
				return new BasicHttpEntityEnclosingRequest(method, uri);
			}
			
		};
		HttpMessageParserFactory<HttpRequest> parserFact = new DefaultHttpRequestParserFactory(null, reqFact);
		HttpConnectionFactory<DefaultBHttpServerConnection> connFact = new DefaultBHttpServerConnectionFactory(null, parserFact, null);
		return connFact;
	}

}
