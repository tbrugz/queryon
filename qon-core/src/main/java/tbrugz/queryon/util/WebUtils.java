package tbrugz.queryon.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLWarning;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.ResponseSpec;

public class WebUtils {

	static final Log log = LogFactory.getLog(WebUtils.class);
	
	public static final String MIME_TEXT = "text/plain";
	
	public static final String PROP_WEB_APPNAME = "queryon.web.appname";
	public static final String PROP_WEB_LOGINMESSAGE = "queryon.web.login-message";
	
	public static final String PARAM_UTF8 = "utf8";
	
	public final static String UTF8_CHECK = "\u2713";

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
		resp.reset();
		resp.setStatus(e.getCode());
		resp.setContentType(MIME_TEXT);
		resp.getWriter().write(e.getMessage());
		
		if(debugMode) {
			log.warn("Exception: "+e);
			log.debug("Exception: "+e.getMessage(), e);
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			resp.getWriter().write("\n\n");
			resp.getWriter().write(errors.toString());
		}
	}
	
	public static boolean convertParametersToUtf8(HttpServletRequest req) {
		String utf8 = req.getParameter(PARAM_UTF8);
		if(utf8!=null) {
			if(utf8.equals(UTF8_CHECK)) {
				log.debug("[ok] utf8: "+utf8);
			}
			else {
				// assume url encoding as latin1 (iso-8859-1)
				// https://stackoverflow.com/questions/10517268/how-to-pass-unicode-characters-as-jsp-servlet-request-getparameter
				// req.setCharacterEncoding("xxx"); //??
				log.warn("[err] utf8: ["+MiscUtils.toIntArrayAsString(utf8)+"] [expected="+UTF8_CHECK+"]");
				// 226 156 147 ? http://www.utf8-chartable.de/unicode-utf8-table.pl?start=9984&number=128&names=-&utf8=dec
				return true;
			}
		}
		return false;
	}
	
	public static void addSqlWarningsAsHeaders(SQLWarning sqlw, HttpServletResponse resp) {
		while(sqlw!=null) {
			log.warn("sqlw: "+sqlw);
			resp.addHeader(ResponseSpec.HEADER_WARNING, sqlw.getMessage()+" ["+sqlw.getSQLState()+"]");
			sqlw = sqlw.getNextWarning();
		}
	}
	
}
