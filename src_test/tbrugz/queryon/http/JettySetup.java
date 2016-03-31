package tbrugz.queryon.http;

import java.io.IOException;
import java.net.ServerSocket;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.util.Factory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.webapp.WebAppContext;

/*
 * https://wiki.eclipse.org/Jetty/Tutorial/Embedding_Jetty
 * http://git.eclipse.org/c/jetty/org.eclipse.jetty.project.git/tree/example-jetty-embedded/src/main/java/org/eclipse/jetty/embedded?h=jetty-8 
 */
public class JettySetup {
	
	public static int startPort = 8889;
	public static int maxPort = 10000;
	
	public static int port = startPort;
	
	public static String qonUrl = "http://localhost:"+port;
	public static String baseUrl = "http://localhost:"+port+"/q";
	public static String qonSchemaBaseUrl = "http://localhost:"+port+"/qos";
	
	private static Server server = null;
	
	public static void setupServer() throws Exception {
		if(server!=null) { return; }
		server = new Server();
		
		port = getAvaiablePort(startPort, maxPort);
		setupTestUrls();
		
		Connector connector = new SelectChannelConnector();
		connector.setPort(port);
		server.setConnectors(new Connector[]{ connector });

		WebAppContext webapp = new WebAppContext();
		String webRoot = "src_test/tbrugz/queryon/http";
		webapp.setDescriptor(webRoot+"/WEB-INF/web.xml");
		webapp.setResourceBase(webRoot);
		webapp.setContextPath("/");
		webapp.setParentLoaderPriority(true);
		server.setHandler(webapp);
		
		/*Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run() {
				System.err.println("shutting down winstone");
				try {
					//XXX shutdown?
				}
				catch(Exception e) {
					System.err.println("Exception shutting down: "+e);
					e.printStackTrace();
				}
				System.err.println("winstone shutted down");
			}
		});*/
		
		server.start();
		//server.join();
		server.setStopAtShutdown(true);
		
		setupShiro();
	}
	
	public static void setupShiro() {
		// see http://shiro.apache.org/testing.html
		Factory<org.apache.shiro.mgt.SecurityManager> factory = new IniSecurityManagerFactory("classpath:test.shiro.permission.ini");
		SecurityUtils.setSecurityManager(factory.getInstance());
	}
	
	public static void setupTestUrls() {
		qonUrl = "http://localhost:"+port;
		baseUrl = "http://localhost:"+port+"/q";
		qonSchemaBaseUrl = "http://localhost:"+port+"/qos";
	}

	public static void shutdown() throws Exception {
		//shutdownShiro(); //??
		//winstone.shutdown();
		server.setGracefulShutdown(0);
		//server.stop();
		//server.destroy();
	}
	
	public static int getAvaiablePort(int testPortInit, int testPortMax) {
		int testPort = testPortInit;
		while(!isAvaiable(testPort) && testPort<testPortMax) { testPort++; }
		return testPort;
	}
	
	// http://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
	public static boolean isAvaiable(int port) {
		ServerSocket ss = null;
		try {
			ss = new ServerSocket(port);
			ss.setReuseAddress(true);
			return true;
		} catch (IOException e) {
			return false;
		} finally {
			if (ss != null) {
				try {
					ss.close();
				} catch (IOException e) {
				}
			}
		}
	}
	
	/*public static void main(String[] args) throws IOException {
		setupWinstone();
	}*/

}
