package tbrugz.queryon.util;

import javax.servlet.http.HttpServletRequest;

public class WebUtils {

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
	
}
