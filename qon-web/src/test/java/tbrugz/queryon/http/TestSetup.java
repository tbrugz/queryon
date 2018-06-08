package tbrugz.queryon.http;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.util.Factory;

//import winstone.Launcher;

public class TestSetup {
	
	public static int startPort = 8889;
	public static int maxPort = 10000;
	
	public static int port = startPort;
	
	/*public static String qonUrl = "http://localhost:"+port;
	public static String baseUrl = "http://localhost:"+port+"/q";
	public static String qonSchemaBaseUrl = "http://localhost:"+port+"/qos";*/
	
	//private static Launcher winstone = null;
	
	public static void setupWinstone() throws IOException {
		//if(winstone!=null) { return; }
		
		port = getAvaiablePort(startPort, maxPort);
		setupTestUrls();
		Map<String, String> args = new HashMap<String, String>();
		args.put("webroot", WinstoneAndH2HttpRequestTest.basedir+"/tbrugz/queryon/http"); // or any other command line args, eg port
		args.put("httpPort", ""+port);
		args.put("ajp13Port", "-1");
		//Launcher.initLogger(args);
		//winstone = new Launcher(args); // spawns threads, so your application doesn't block
		
		Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run() {
				System.err.println("shutting down winstone");
				try {
					//winstone.shutdown();
				}
				catch(Exception e) {
					System.err.println("Exception shutting down: "+e);
					e.printStackTrace();
				}
				System.err.println("winstone shutted down");
			}
		});
		
		setupShiro();
	}
	
	public static void setupShiro() {
		// see http://shiro.apache.org/testing.html
		Factory<org.apache.shiro.mgt.SecurityManager> factory = new IniSecurityManagerFactory("classpath:test.shiro.permission.ini");
		SecurityUtils.setSecurityManager(factory.getInstance());
	}
	
	public static void setupTestUrls() {
		/*qonUrl = "http://localhost:"+port;
		baseUrl = "http://localhost:"+port+"/q";
		qonSchemaBaseUrl = "http://localhost:"+port+"/qos";*/
	}

	public static void shutdown() {
		//shutdownShiro(); //??
		//winstone.shutdown();
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
