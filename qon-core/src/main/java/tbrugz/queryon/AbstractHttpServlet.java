package tbrugz.queryon;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class AbstractHttpServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(AbstractHttpServlet.class);
	
	public static final String MIME_TEXT = "text/plain";
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doProcess(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doProcess(req, resp);
	}
	
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			super.service(req, resp);
		} catch(BadRequestException e) {
			log.warn("BadRequestException ["+this.getClass().getSimpleName()+"]" + (resp.isCommitted()?"[committed]":"") + ": " + e.getMessage());
			log.debug("BadRequestException ["+this.getClass().getSimpleName()+"][committed? "+resp.isCommitted()+"]: "+e.getMessage(), e);
			resp.setStatus(e.getCode());
			resp.setContentType(MIME_TEXT);
			resp.getWriter().write(e.getMessage());
		}
		/*catch (ServletException e) {
			throw e;
		} catch (Exception e) {
			//e.printStackTrace();
			throw new ServletException(e);
		}*/
	}

	protected abstract void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException;
	
}
