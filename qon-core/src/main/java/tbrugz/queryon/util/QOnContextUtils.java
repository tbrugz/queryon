package tbrugz.queryon.util;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.UpdatePlugin;

public class QOnContextUtils {

	@SuppressWarnings("unused")
	private static final Log log = LogFactory.getLog(QOnContextUtils.class);

	public static final String ATTR_DUMP_SYNTAX_UTILS = "dsutils";
	public static final String ATTR_PROP = "prop";
	public static final String ATTR_SCHEMAS_MAP = "schemasmap";
	public static final String ATTR_UPDATE_PLUGINS = "update-plugins";
	
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

	@SuppressWarnings("unchecked")
	public static Map<String, List<String>> getSchemasByModel(ServletContext context) {
		return (Map<String, List<String>>) context.getAttribute(ATTR_SCHEMAS_MAP);
	}

	public static void setSchemasByModel(ServletContext context, Map<String, List<String>> schemasByModel) {
		context.setAttribute(ATTR_SCHEMAS_MAP, schemasByModel);
	}

	@SuppressWarnings("unchecked")
	public static Map<String, List<UpdatePlugin>> getUpdatePlugins(ServletContext context) {
		return (Map<String, List<UpdatePlugin>>) context.getAttribute(ATTR_UPDATE_PLUGINS);
	}

	public static void setUpdatePlugins(ServletContext context, Map<String, List<UpdatePlugin>> updatePlugins) {
		context.setAttribute(ATTR_UPDATE_PLUGINS, updatePlugins);
	}

}
