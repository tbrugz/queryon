package tbrugz.queryon.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import tbrugz.queryon.BadRequestException;

public class WebUtils {

	public static final String MIME_TEXT = "text/plain";
	
	public static final String PROP_WEB_APPNAME = "queryon.web.appname";

	public static String getRequestFullContext(HttpServletRequest req) {
		String ret = req.getScheme() + "://" + req.getServerName() +
				( ("http".equals(req.getScheme()) && req.getServerPort()==80) || ("https".equals(req.getScheme()) && req.getServerPort()==443) ? "" : ":"+req.getServerPort() ) +
				req.getContextPath();
		return ret;
	}
	
	public static Integer getIntegerParameter(HttpServletRequest req, String param) {
		return getIntegerParameter(req, param, null);
	}
	
	public static Integer getIntegerParameter(HttpServletRequest req, String param, Integer defaultValue) {
		String value = req.getParameter(param);
		if (value!=null) {
			return Integer.parseInt(value);
		}
		return defaultValue;
	}
	
	public static void writeException(HttpServletResponse resp, BadRequestException e, boolean debugMode) throws IOException {
		//e.printStackTrace();
		//log.warn("BRE: "+e.getMessage()+
		//		(e.internalMessage!=null?" ; internal="+e.internalMessage:"")); 
		resp.setStatus(e.getCode());
		resp.setContentType(MIME_TEXT);
		resp.getWriter().write(e.getMessage());
		
		if(debugMode) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			resp.getWriter().write("\n\n");
			resp.getWriter().write(errors.toString());
		}
	}
	
}
