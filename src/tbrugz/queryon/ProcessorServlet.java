package tbrugz.queryon;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Enumeration;
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
import tbrugz.sqldump.def.ProcessComponent;
import tbrugz.sqldump.def.Processor;
import tbrugz.sqldump.def.SchemaModelDumper;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.ParametrizedProperties;
import tbrugz.sqldump.util.Utils;

/*
 * XXX: allow only POST method? (not idempotent)
 */
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
		doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
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
	 * XXXdone option to write processor's output?
	 */
	void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException, ServletException {
		String s = req.getPathInfo();
		//log.info("pathInfo: "+s);
		String[] parts = s.split("/");
		String procClasses = parts[1];

		String[] classParts = procClasses.split(",");
		for(String procClass: classParts) {
			doProcess(procClass, config, req, resp);
		}
	}

	public static void doProcess(String procClass, ServletConfig config) throws ClassNotFoundException, ServletException, SQLException, NamingException, IOException {
		doProcess(procClass, config, null, null);
	}

	public static void doProcess(String procClass, ServletConfig config, HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, ServletException, SQLException, NamingException, IOException {
		ProcessComponent procComponent = (ProcessComponent) Utils.getClassInstance(procClass, Defs.DEFAULT_CLASSLOADING_PACKAGES);
		if(procComponent==null) {
			throw new ClassNotFoundException(procClass);
		}
		
		Properties appprop = (Properties) config.getServletContext().getAttribute(QueryOn.ATTR_PROP);
		if(appprop==null) {
			throw new ServletException("properties attribute is null!");
		}
		Properties prop = new ParametrizedProperties();
		prop.putAll(appprop);
		if(req!=null) {
			Enumeration<String> en = req.getParameterNames();
			while(en.hasMoreElements()) {
				String s = en.nextElement();
				prop.setProperty(s, req.getParameter(s));
			}
		}
		procComponent.setProperties(prop);
		
		if(procComponent instanceof Processor) {
			Processor proc = (Processor) procComponent;
			doProcessProcessor(proc, prop, config, resp);
			if(!proc.acceptsOutputStream() && !proc.acceptsOutputWriter() && resp!=null) {
				resp.getWriter().write("processor '"+procClass+"' processed");
			}
		}
		else if(procComponent instanceof SchemaModelDumper) {
			SchemaModelDumper dumper = (SchemaModelDumper) procComponent;
			doProcessDumper(dumper, config, resp);
			if(!dumper.acceptsOutputStream() && !dumper.acceptsOutputWriter() && resp!=null) {
				resp.getWriter().write("dumper '"+procClass+"' processed");
			}
		}
		else {
			throw new IllegalArgumentException("'"+procComponent.getClass()+"': unknown processor type");
		}
	}
	
	static void doProcessProcessor(Processor pc, Properties prop, ServletConfig config, HttpServletResponse resp) throws ClassNotFoundException, ServletException, SQLException, NamingException, IOException {
		if(pc.needsSchemaModel()) {
			SchemaModel sm = (SchemaModel) config.getServletContext().getAttribute(QueryOn.ATTR_MODEL);
			if(sm==null) {
				throw new ServletException("schema model attribute is null!");
			}
			pc.setSchemaModel(sm);
		}
		
		Connection conn = null;
		if(pc.needsConnection()) {
			conn = ConnectionUtil.initDBConnection(QueryOn.CONN_PROPS_PREFIX, prop);
			pc.setConnection(conn);
		}
		
		Writer w = null;
		OutputStream os = null;
		if(resp!=null) {
			if(pc.acceptsOutputWriter()) {
				w = resp.getWriter();
				pc.setOutputWriter(w);
			}
			else if(pc.acceptsOutputStream()) {
				os = resp.getOutputStream();
				pc.setOutputStream(os);
			}
		}
		
		pc.process();
		
		if(w!=null) {
			w.flush();
		}
		if(os!=null) {
			os.flush();
		}

		if(conn!=null) { conn.close(); }
	}
	
	static void doProcessDumper(SchemaModelDumper dumper, ServletConfig config, HttpServletResponse resp) throws ClassNotFoundException, ServletException, SQLException, NamingException, IOException {
		SchemaModel sm = (SchemaModel) config.getServletContext().getAttribute(QueryOn.ATTR_MODEL);
		if(sm==null) {
			throw new ServletException("schema model attribute is null!");
		}
		
		Writer w = null;
		OutputStream os = null;
		if(resp!=null) {
			if(dumper.acceptsOutputWriter()) {
				w = resp.getWriter();
				dumper.setOutputWriter(w);
			}
			else if(dumper.acceptsOutputStream()) {
				os = resp.getOutputStream();
				dumper.setOutputStream(os);
			}
		}
		//XXX else: System.out?
		
		dumper.dumpSchema(sm);
		
		if(w!=null) {
			w.flush();
		}
		if(os!=null) {
			os.flush();
		}
	}

}
