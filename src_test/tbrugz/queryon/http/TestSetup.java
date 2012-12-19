package tbrugz.queryon.http;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import winstone.Launcher;

public class TestSetup {
	
	final static int port = 8889;
	final static String baseUrl = "http://localhost:"+port;
	
	private static Launcher winstone = null;
	
	public static void setupWinstone() throws IOException {
		if(winstone!=null) { return; }
			
		Map<String, String> args = new HashMap<String, String>();
		args.put("webroot", "bin/tbrugz/queryon/http"); // or any other command line args, eg port
		args.put("httpPort", ""+port);
		args.put("ajp13Port", "-1");
		Launcher.initLogger(args);
		winstone = new Launcher(args); // spawns threads, so your application doesn't block
		
		Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run() {
				System.err.println("shutting down winstone");
				winstone.shutdown();
				System.err.println("winstone shutted down");
			}
		});
	}

	public static void shutdown() {
		//winstone.shutdown(); 
	}
	
}
