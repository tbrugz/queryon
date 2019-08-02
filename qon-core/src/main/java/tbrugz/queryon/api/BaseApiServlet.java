package tbrugz.queryon.api;

import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import tbrugz.queryon.QueryOn;
import tbrugz.queryon.util.DumpSyntaxUtils;

public abstract class BaseApiServlet extends QueryOn {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doInitProperties(ServletConfig config) {
	}
	
	@Override
	protected void doInit(ServletContext context) throws ServletException {
		prop.putAll((Properties) context.getAttribute(ATTR_PROP));
		dsutils = (DumpSyntaxUtils) context.getAttribute(ATTR_DUMP_SYNTAX_UTILS);
		servletContext = context;
		//servletUrlContext = "xxx";
		//log.info("context: "+servletContext.getContextPath()+" ; servletUrlContext: "+servletUrlContext);
		
		initFromProperties();
	}
	
}
