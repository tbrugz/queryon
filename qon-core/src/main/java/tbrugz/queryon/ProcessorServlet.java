package tbrugz.queryon;

import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.queryon.util.ShiroUtils;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.def.ProcessComponent;
import tbrugz.sqldump.def.Processor;
import tbrugz.sqldump.def.SchemaModelDumper;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.ParametrizedProperties;
import tbrugz.sqldump.util.Utils;

/*
 * XXXdone: allow only POST method? (not idempotent)
 */
public class ProcessorServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	static final Log log = LogFactory.getLog(ProcessorServlet.class);
	
	static final String PROCESSOR_PERMISSION_PREFIX = "PROCESSOR:";
	static final int ERROR_STATUS = 500;
	
	ServletConfig config = null; //XXX: remove ServletConfig config
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		this.config = config;
	}
	
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
			log.warn(e.getClass().getSimpleName()+" ["+e.getCode()+"]: "+e.getMessage());
			log.debug(e.getClass().getSimpleName()+" ["+e.getCode()+"]: "+e.getMessage(), e);
			resp.setStatus(e.getCode());
			resp.setContentType(AbstractHttpServlet.MIME_TEXT);
			if(resp.isCommitted()) {
				log.warn("response already committed...");
			}
			else {
				resp.getWriter().write(e.getMessage());
			}
		} catch (ServletException e) {
			//resp.setStatus(ERROR_STATUS);
			//resp.setContentType(AbstractHttpServlet.MIME_TEXT);
			//resp.getWriter().write(e.getMessage());
			
			log.warn(e.getClass().getSimpleName()+": "+e.getMessage(), e);
			throw e;
		} catch (Exception e) {
			//resp.setStatus(ERROR_STATUS);
			//resp.setContentType(AbstractHttpServlet.MIME_TEXT);
			//resp.getWriter().write(e.getMessage());
			
			log.warn(e.getClass().getSimpleName()+": "+e.getMessage(), e);
			throw new ServletException(e);
		} catch (Throwable e) {
			log.warn(e.getClass().getSimpleName()+": "+e.getMessage(), e);
			throw new ServletException(e);
		}
	}

	/*
	 * XXX add all request parameters as properties?
	 * XXXdone option to write processor's output?
	 */
	void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException, ServletException {
		String s = req.getPathInfo();
		if(s==null) {
			throw new BadRequestException("null pathinfo");
		}
		//log.debug("pathInfo: "+s);
		String[] parts = s.split("/");
		if(parts.length<2) {
			throw new BadRequestException("null processor");
		}
		String procClasses = parts[1];

		String[] classParts = procClasses.split(",");
		for(String procClass: classParts) {
			doProcess(procClass, config.getServletContext(), SchemaModelUtils.getModelId(req), req, resp);
		}
	}

	// called by PROP_PROCESSORS_ON_STARTUP
	public static void doProcess(String procClass, ServletContext context, String modelId) throws ClassNotFoundException, ServletException, SQLException, NamingException, IOException {
		doProcess(procClass, context, modelId, null, null);
	}

	//TODOne: shiro: add permission check
	static void doProcess(String procClass, ServletContext context, String modelId, HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, ServletException, SQLException, NamingException, IOException {
		ProcessComponent procComponent = (ProcessComponent) Utils.getClassInstance(procClass, QueryOn.DEFAULT_CLASSLOADING_PACKAGES);
		if(procComponent==null) {
			throw new BadRequestException("processor class not found: "+procClass);
		}
		
		Properties appprop = (Properties) context.getAttribute(QueryOn.ATTR_PROP);
		if(appprop==null) {
			throw new ServletException("properties attribute is null!");
		}

		// check authorization
		if(req!=null) {
			Subject currentUser = ShiroUtils.getSubject(appprop, req);
			ShiroUtils.checkPermissionAny(currentUser, new String[]{
					PROCESSOR_PERMISSION_PREFIX+procComponent.getClass().getCanonicalName(),
					PROCESSOR_PERMISSION_PREFIX+procComponent.getClass().getSimpleName(),
					});
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
		
		//SchemaModel model = SchemaModelUtils.getModel(context, modelId);
		
		if(procComponent instanceof Processor) {
			
			Processor proc = (Processor) procComponent;
			// if not idempotent, only POST method allowed
			if(!proc.isIdempotent() && req!=null && req.getMethod()!=null && !req.getMethod().equals("POST")) {
				throw new BadRequestException("processor '"+procClass+"' only allowed with POST method");
			}
			doProcessProcessor(proc, prop, modelId, context, req, resp);
			if(!proc.acceptsOutputStream() && !proc.acceptsOutputWriter() && resp!=null) {
				resp.getWriter().write("processor '"+procClass+"' processed\n");
			}
		}
		else if(procComponent instanceof SchemaModelDumper) {
			// dumpers are always idempotent?
			SchemaModelDumper dumper = (SchemaModelDumper) procComponent;
			doProcessDumper(dumper, modelId, context, resp);
			if(!dumper.acceptsOutputStream() && !dumper.acceptsOutputWriter() && resp!=null) {
				resp.getWriter().write("dumper '"+procClass+"' processed\n");
			}
		}
		else {
			throw new IllegalArgumentException("'"+procComponent.getClass()+"': unknown processor type");
		}
	}
	
	static void doProcessProcessor(Processor pc, Properties prop, String modelId, ServletContext context, HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, ServletException, SQLException, NamingException, IOException {
		SchemaModel sm = null;
		if(pc.needsSchemaModel()) {
			sm = SchemaModelUtils.getModel(context, modelId);
			if(sm==null) {
				throw new ServletException("schema model attribute is null!");
			}
			pc.setSchemaModel(sm);
		}
		
		Connection conn = null;
		if(pc.needsConnection()) {
			conn = DBUtil.initDBConn(prop, modelId, sm);
			pc.setConnection(conn);
		}
		
		Flushable output = null;
		try {
			if(pc instanceof WebProcessor) {
				WebProcessor wp = (WebProcessor) pc;

				DumpSyntaxUtils dsutils = (DumpSyntaxUtils) context.getAttribute(QueryOn.ATTR_DUMP_SYNTAX_UTILS);
				RequestSpec reqspec = null;
				DBIdentifiable dbobj = null;
				Subject currentUser = null;
				if(req!=null) {
					reqspec = new RequestSpec(dsutils, req, prop, 1);
					dbobj = SchemaModelUtils.getDBIdentifiableBySchemaAndName(sm, reqspec);
					currentUser = ShiroUtils.getSubject(prop, req);
					//log.info("dbobj: "+dbobj+" currentUser: "+currentUser);
				}
				
				try {
					//wp.setRelation(r);
					wp.setDBIdentifiable(dbobj);
				}
				catch(Exception e) {
					log.warn("error setting DBIdentifiable: reqspec="+reqspec); //+" [modelId="+reqspec.modelId+"]");
				}
				wp.setSubject(currentUser);
				wp.setServletContext(context);
				
				wp.process(reqspec, resp);
			}
			else {
				output = setOutput(pc, resp);
				
				pc.process();
			}
		}
		finally {
			if(output!=null) {
				output.flush();
			}
			if(conn!=null) {
				//QOnModelUtils.setModelMetadata(sm, conn);
				ConnectionUtil.closeConnection(conn);
			}
		}
	}
	
	static void doProcessDumper(SchemaModelDumper dumper, String modelId, ServletContext context, HttpServletResponse resp) throws ClassNotFoundException, ServletException, SQLException, NamingException, IOException {
		SchemaModel sm = SchemaModelUtils.getModel(context, modelId);
		if(sm==null) {
			throw new ServletException("schema model attribute is null!");
		}
		
		Writer w = null;
		OutputStream os = null;
		String mimeType = dumper.getMimeType();
		if(resp!=null) {
			if(dumper.acceptsOutputWriter()) {
				w = resp.getWriter();
				dumper.setOutputWriter(w);
			}
			else if(dumper.acceptsOutputStream()) {
				os = resp.getOutputStream();
				dumper.setOutputStream(os);
			}
			
			if(mimeType!=null) {
				resp.setContentType(mimeType);
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
	
	public static Flushable setOutput(Processor pc, HttpServletResponse resp) throws IOException {
		Flushable ret = null;
		String mimeType = pc.getMimeType();
		if(resp!=null) {
			if(pc.acceptsOutputWriter()) {
				Writer w = resp.getWriter();
				pc.setOutputWriter(w);
				ret = w;
			}
			else if(pc.acceptsOutputStream()) {
				OutputStream os = resp.getOutputStream();
				pc.setOutputStream(os);
				ret = os;
			}
			
			if(mimeType!=null) {
				resp.setContentType(mimeType);
			}
		}
		return ret;
	}
	
}
