package tbrugz.queryon.api;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.naming.NamingException;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.QueryOn;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.syntaxes.ODataJsonSyntax;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.FK;
import tbrugz.sqldump.dbmodel.NamedDBObject;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.resultset.ResultSetListAdapter;

public class ODataServlet extends QueryOn {
	
	public static class Entity {
		//String schema;
		String name;
		String kind;
		String url;
		
		public Entity(String schema, String name, String kind) {
			//this.schema = schema;
			String fullName = (schema!=null?schema+".":"")+name;
			this.name = fullName;
			this.kind = kind;
			this.url = fullName;
		}
		
		public String getName() {
			return name;
		}
		public String getKind() {
			return kind;
		}
		public String getUrl() {
			return url;
		}
	}

	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(ODataServlet.class);
	
	static final String DEFAULT_ODATA_CONTEXT = "odata";
	static final String ODATA_VERSION = "4.0";
	
	//static String baseQonUrl = "/q";
	//static String baseODataUrl = "/odata";
	
	/*@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		doRedir2Queryon(req, resp);
	}*/
	
	/*
	public void doRedir2Queryon(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		String redirUrl = null;
		
		if(req.getPathInfo()==null || req.getPathInfo().isEmpty() || req.getPathInfo().equals("/")) {
			redirUrl = "/relation.odata";
		}
		else {
			redirUrl = (req.getPathInfo()!=null?req.getPathInfo():"");
		}
		
		if(req.getQueryString()!=null) {
			redirUrl += "?"+req.getQueryString();
		}
		
		redirUrl = baseQonUrl + redirUrl;
		
		log.info("request: path="+req.getPathInfo()+" ; query="+req.getQueryString()+" ; redir="+redirUrl);
		RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(redirUrl);
		dispatcher.forward(req, resp);
	}
	*/
	
	@Override
	protected void doInitProperties(ServletConfig config) {
	}
	
	@Override
	protected void doInit(ServletContext context) throws ServletException {
		prop.putAll((Properties) context.getAttribute(ATTR_PROP));
		dsutils = (DumpSyntaxUtils) context.getAttribute(ATTR_DUMP_SYNTAX_UTILS);
		servletContext = context;
		servletUrlContext = DEFAULT_ODATA_CONTEXT;
		//log.info("context: "+servletContext.getContextPath()+" ; servletUrlContext: "+servletUrlContext);
		
		initFromProperties();
	}
	
	@Override
	protected void doService(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if(req.getPathInfo()==null || req.getPathInfo().isEmpty()
				//|| req.getPathInfo().equals("/")
				) {
			//String redirUrl = "/relation";
			//String redirUrl = "/object";
			String redirUrl = "/";
			if(req.getQueryString()!=null) {
				redirUrl += "?"+req.getQueryString();
			}
			//redirUrl = baseODataUrl + redirUrl;
			redirUrl = "/" + servletUrlContext + redirUrl;
			
			log.info("forward: path="+req.getPathInfo()+" ; query="+req.getQueryString()+" ; redir="+redirUrl);
			RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(redirUrl);
			dispatcher.forward(req, resp);
			return;
		}
		
		if("$metadata".equals(req.getPathInfo())) {
			//XXX: generate XML metadata
			// http://services.odata.org/V4/(S(frp3ql3rlcjpvorgy0422buq))/TripPinServiceRW/$metadata#Photos
			// EnumType, ComplexType, *EntityType, *Function, ?Action, EntityContainer(EntitySet, FunctionImport, ActionImport, Annotation), Annotations
		}

		log.info(">> pathInfo: "+req.getPathInfo()+" ; method: "+req.getMethod());
		
		// ? header: Content-Type: application/json;odata.metadata=minimal;odata.streaming=true;IEEE754Compatible=false;charset=utf-8
		// x header: OData-Version: 4.0
		resp.setHeader("OData-Version", ODATA_VERSION);
		
		super.doService(req, resp);
	}
	
	@Override
	protected RequestSpec getRequestSpec(HttpServletRequest req) throws ServletException, IOException {
		return new ODataRequest(dsutils, req, prop, 0, ODataJsonSyntax.ODATA_ID, false, 0, "relation");
	}
	
	static final List<String> statusUniqueColumns = Arrays.asList(new String[]{"name"});
	static final List<String> statusXtraColumns = Arrays.asList(new String[]{"kind", "url"});
	//XXX kind: EntitySet, Singleton, FunctionImport
	
	@SuppressWarnings("resource")
	@Override
	protected void doStatus(SchemaModel model, DBObjectType statusType, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws IntrospectionException, SQLException, IOException, ServletException, ClassNotFoundException, NamingException {
		ResultSet rs = null;
		List<FK> importedFKs = null;
		List<Constraint> uks = null;
		
		final String objectName = statusType.desc();
		PrivilegeType privilege = PrivilegeType.SELECT;

		List<Entity> list = new ArrayList<Entity>();
		//XXX: sort objects?
		list.addAll(getEntities(model.getViews(), "EntitySet"));
		list.addAll(getEntities(model.getTables(), "EntitySet"));
		list.addAll(getEntities(model.getExecutables(), "FunctionImport"));
		rs = new ResultSetListAdapter<Entity>(objectName, statusUniqueColumns, statusXtraColumns, list, Entity.class);

		//List<ExecutableObject> list = new ArrayList<ExecutableObject>(); list.addAll(model.getExecutables());
		//rs = new ResultSetListAdapter<ExecutableObject>(objectName, statusUniqueColumns, list, ExecutableObject.class);
		
		//List<FK> list = new ArrayList<FK>(); list.addAll(model.getForeignKeys());
		//rs = new ResultSetListAdapter<FK>(objectName, statusUniqueColumns, list, FK.class);
		
		rs = filterStatus(rs, reqspec, currentUser, privilege);
		
		dumpResultSet(rs, reqspec, null, objectName, statusUniqueColumns, importedFKs, uks, true, resp);
		if(rs!=null) { rs.close(); }
	}
	
	List<Entity> getEntities(Set<? extends NamedDBObject> list, String kind) {
		List<Entity> ret = new ArrayList<Entity>();
		for(NamedDBObject o: list) {
			ret.add(new Entity(o.getSchemaName(), o.getName(), kind));
		}
		return ret;
	}
	
	/*@Override
	protected DBObjectType statusObject(String name) {
		if(name.isEmpty()) {
			return DBObjectType.RELATION;
		}
		return super.statusObject(name);
	}*/

}