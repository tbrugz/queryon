package tbrugz.queryon.util;

import java.util.Properties;

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class QOnContextUtils {

	@SuppressWarnings("unused")
	private static final Log log = LogFactory.getLog(QOnContextUtils.class);

	public static final String ATTR_DUMP_SYNTAX_UTILS = "dsutils";
	public static final String ATTR_PROP = "prop";
	
	public static DumpSyntaxUtils getDumpSyntaxUtils(ServletContext context) {
		return (DumpSyntaxUtils) context.getAttribute(ATTR_DUMP_SYNTAX_UTILS);
	}

	public static void setDumpSyntaxUtils(ServletContext context, DumpSyntaxUtils dsutils) {
		context.setAttribute(ATTR_DUMP_SYNTAX_UTILS, dsutils);
	}

	public static Properties getProperties(ServletContext context) {
		return (Properties) context.getAttribute(ATTR_PROP);
	}

	public static void setProperties(ServletContext context, Properties prop) {
		context.setAttribute(ATTR_PROP, prop);
	}

}
