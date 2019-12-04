package tbrugz.queryon.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.sqldump.dbmodel.Relation;

public class UpdatePluginUtils {

	static final Log log = LogFactory.getLog(UpdatePluginUtils.class);
	
	public static final String ATTR_INIT_WARNINGS_PREFIX = "qon-init-warnings";
	
	@SuppressWarnings("unchecked")
	public static void putWarning(ServletContext context, String warnKey, String schemaName, String queryName, String warning) {
		//String warnKey = ATTR_QUERIES_WARNINGS_PREFIX+"."+modelId;
		Map<String, String> warnings = (Map<String, String>) context.getAttribute(warnKey);
		if(warnings==null) {
			log.warn("warning key '"+warnKey+"' should not be null");
			warnings = new LinkedHashMap<String, String>();
			context.setAttribute(warnKey, warnings);
		}
		warnings.put((schemaName!=null?schemaName+".":"") + queryName, warning);
	}

	public static void clearWarnings(ServletContext context, String warnKey) {
		//String warnKey = ATTR_QUERIES_WARNINGS_PREFIX+"."+modelId;
		Map<String, String> warnings = new LinkedHashMap<String, String>();
		context.setAttribute(warnKey, warnings);
	}
	
	@SuppressWarnings("unchecked")
	public static void removeWarning(ServletContext context, String warnKey, String schemaName, String queryName) {
		//String warnKey = ATTR_QUERIES_WARNINGS_PREFIX+"."+modelId;
		Map<String, String> warnings = (Map<String, String>) context.getAttribute(warnKey);
		warnings.remove((schemaName!=null?schemaName+".":"") + queryName);
	}
	
	public static void putWarning(ServletContext context, String warnKey, Relation r, String warning) {
		putWarning(context, warnKey, r.getSchemaName(), r.getName(), warning);
	}

	public static void removeWarning(ServletContext context, String warnKey, Relation r) {
		removeWarning(context, warnKey, r.getSchemaName(), r.getName());
	}
	
}
