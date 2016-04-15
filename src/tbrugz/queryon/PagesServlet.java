package tbrugz.queryon;

import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class PagesServlet extends AbstractHttpServlet {

	private static final long serialVersionUID = 1L;
	
	private static final Log log = LogFactory.getLog(PagesServlet.class);
	
	public static final String PROP_PREFIX = "queryon.qon-pages";
	public static final String SUFFIX_TABLE = ".table";
	
	public static final String DEFAULT_PAGES_TABLE = "QON_PAGES";

	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		// get path
		String pathInfo = req.getPathInfo();
		if(pathInfo==null) { throw new BadRequestException("URL (path-info) must not be null"); }
		
		Properties prop = (Properties) req.getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		// get qon_pages table
		String relation = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_PAGES_TABLE);
		
		// get id
		//XXX: query qon_pages...
		String id = "20";
		
		// redirect
		String redir = "/q/"+relation+"?p1="+id+"&valuefield=BODY&mimefield=MIME";
		log.info("pathInfo = "+pathInfo+" ; redir = "+redir);
		req.getRequestDispatcher(redir).forward(req, resp);
	}

}
