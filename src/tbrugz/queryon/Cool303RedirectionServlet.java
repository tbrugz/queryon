package tbrugz.queryon;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @see http://www.w3.org/TR/cooluris/
 * 
 * @author <tbrugz@gmail.com>
 */
/*
 * TODO: init params: add current-server, current-port and/or current-context to path
 */
public class Cool303RedirectionServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	static final Log log = LogFactory.getLog(Cool303RedirectionServlet.class);
	
	public static String PARAM_URL_PREPEND = "url-prepend"; 
	
	String urlPrepend = null;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		urlPrepend = config.getInitParameter(PARAM_URL_PREPEND);
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		//TODO: test if resource (& id?) exists
		resp.setStatus(303);
		String redirTo = (urlPrepend!=null?urlPrepend:"") +
				req.getPathInfo();
		
		log.info("redir to: "+redirTo);
		resp.setHeader("Location", redirTo);
	}

}
