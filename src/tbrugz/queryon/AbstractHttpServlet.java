package tbrugz.queryon;

import java.io.IOException;
import java.sql.SQLException;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class AbstractHttpServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(AbstractHttpServlet.class);
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		//throw new BadRequestException("Only POST allowed", HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			doProcess(req, resp);
		} catch(BadRequestException e) {
			log.warn("BadRequestException: "+e.getMessage());
			resp.setStatus(e.getCode());
			resp.getWriter().write(e.getMessage());
		} /*catch (ServletException e) {
			//e.printStackTrace();
			throw e;
		} */ catch (Exception e) {
			//e.printStackTrace();
			throw new ServletException(e);
		}
	}

	abstract void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException;
	
}
