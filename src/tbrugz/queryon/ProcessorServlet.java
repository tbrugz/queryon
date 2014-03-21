package tbrugz.queryon;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.def.Defs;
import tbrugz.sqldump.def.Processor;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.Utils;

public class ProcessorServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	static final Log log = LogFactory.getLog(ProcessorServlet.class);
	
	ServletConfig config = null;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		this.config = config;
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			doProcess(req, resp);
		} catch (ServletException e) {
			e.printStackTrace();
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			throw new ServletException(e);
		}
	}

	/*
	 * XXX add all request parameters as properties?
	 * XXX option to write processor's output?
	 */
	void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException, ServletException {
		String s = req.getPathInfo();
		//log.info("pathInfo: "+s);
		String[] parts = s.split("/");
		String procClass = parts[1];
		
		doProcess(procClass, config);
		resp.getWriter().write("processor '"+procClass+"' processed");
	}
	
	static void doProcess(String procClass, ServletConfig config) throws ClassNotFoundException, ServletException, SQLException, NamingException {
		Processor pc = (Processor) Utils.getClassInstance(procClass, Defs.DEFAULT_CLASSLOADING_PACKAGES);
		if(pc==null) {
			throw new ClassNotFoundException(procClass);
			//throw new ClassNotFoundException(procClass+" [pathInfo: "+s+"]");
		}
		
		Properties prop = (Properties) config.getServletContext().getAttribute(QueryOn.ATTR_PROP);
		SchemaModel sm = (SchemaModel) config.getServletContext().getAttribute(QueryOn.ATTR_MODEL);
		if(prop==null) {
			throw new ServletException("properties attribute is null!");
		}
		if(sm==null) {
			throw new ServletException("schema model attribute is null!");
		}
		
		pc.setProperties(prop);
		Connection conn = null;
		if(pc.needsConnection()) {
			conn = ConnectionUtil.initDBConnection(QueryOn.CONN_PROPS_PREFIX, prop);
			pc.setConnection(conn);
		}
		pc.setSchemaModel(sm);
		pc.process();
		
		if(conn!=null) { conn.close(); }
	}
	
}
