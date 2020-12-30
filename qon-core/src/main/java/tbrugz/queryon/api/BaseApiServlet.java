package tbrugz.queryon.api;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import tbrugz.queryon.QueryOn;
import tbrugz.queryon.util.QOnContextUtils;

public abstract class BaseApiServlet extends QueryOn {

	private static final long serialVersionUID = 1L;

	/*@Override
	protected void doInitConfig(ServletConfig config) {
	}*/
	
	@Override
	protected void doInit(ServletContext context) throws ServletException {
		prop.putAll(QOnContextUtils.getProperties(context));
		//servletContext = context;
		//servletUrlContext = "xxx";
		//log.info("context: "+servletContext.getContextPath()+" ; servletUrlContext: "+servletUrlContext);
		
		initFromProperties(QOnContextUtils.getDumpSyntaxUtils(context));
	}
	
}
