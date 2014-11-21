package tbrugz.queryon.http;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.util.Factory;

import winstone.Launcher;

public class TestSetup {
	
	public final static int port = 8889;
	public final static String baseUrl = "http://localhost:"+port+"/q";
	public final static String qonSchemaBaseUrl = "http://localhost:"+port+"/qos";
	
	private static Launcher winstone = null;
	
	public static void setupWinstone() throws IOException {
		if(winstone!=null) { return; }
			
		Map<String, String> args = new HashMap<String, String>();
		args.put("webroot", "src_test/tbrugz/queryon/http"); // or any other command line args, eg port
		args.put("httpPort", ""+port);
		args.put("ajp13Port", "-1");
		Launcher.initLogger(args);
		winstone = new Launcher(args); // spawns threads, so your application doesn't block
		
		Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run() {
				System.err.println("shutting down winstone");
				try {
					winstone.shutdown();
				}
				catch(Exception e) {
					System.err.println("Exception shutting down: "+e);
				}
				System.err.println("winstone shutted down");
			}
		});
		
		setupShiro();
	}
	
	public static void setupShiro() {
		// see http://shiro.apache.org/testing.html
		Factory<org.apache.shiro.mgt.SecurityManager> factory = new IniSecurityManagerFactory("classpath:test.shiro.all-permission.ini");
		SecurityUtils.setSecurityManager(factory.getInstance());
	}

	public static void shutdown() {
		//shutdownShiro(); //??
		//winstone.shutdown();
	}
	
}
