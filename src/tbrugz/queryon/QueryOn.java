package tbrugz.queryon;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.net.InetAddress;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.resultset.ResultSetFilterDecorator;
import tbrugz.queryon.resultset.ResultSetLimitOffsetDecorator;
import tbrugz.queryon.resultset.ResultSetPermissionFilterDecorator;
import tbrugz.sqldump.resultset.ResultSetListAdapter;
import tbrugz.sqldump.datadump.DumpSyntax;
import tbrugz.sqldump.datadump.DumpSyntaxRegistry;
import tbrugz.sqldump.datadump.RDFAbstractSyntax;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.Constraint.ConstraintType;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.ExecutableParameter;
import tbrugz.sqldump.dbmodel.FK;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.def.Defs;
import tbrugz.sqldump.def.SchemaModelGrabber;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.ParametrizedProperties;
import tbrugz.sqldump.util.StringDecorator;
import tbrugz.sqldump.util.Utils;

/**
 * @see Web API Design - http://info.apigee.com/Portals/62317/docs/web%20api.pdf
 */
/*
 * TODO r2rml: option to understand URLs like: Department/name=accounting;city=Cambridge
 */
public class QueryOn extends HttpServlet {
	private static final long serialVersionUID = 1L;

	public enum ActionType {
		SELECT,  //done
		INSERT,
		//UPSERT? which http-method is suitable? POST?
		UPDATE,
		DELETE,
		EXECUTE, //~TODOne: execute action!
		//QUERY,   //TODOne: SQLQueries action!
		STATUS,   //~TODOne: or CONFIG? show model, user, vars...
		//XXXxx: FINDBYKEY action? return only the first result
		SELECT_ANY,
		VALIDATE_ANY
	}
	
	// 'status objects' (SO)
	public static final String SO_TABLE = "table", 
			SO_VIEW = "view",
			SO_RELATION = "relation", //tables + views 
			SO_EXECUTABLE = "executable",
			SO_FK = "fk";
	
	public static final String[] STATUS_OBJECTS = {
		SO_TABLE, SO_VIEW, SO_RELATION, SO_EXECUTABLE, SO_FK,
		SO_TABLE.toUpperCase(), SO_VIEW.toUpperCase(), SO_RELATION.toUpperCase(), SO_EXECUTABLE.toUpperCase(), SO_FK.toUpperCase()
	};
	
	public static final String ACTION_QUERY_ANY = "QueryAny";
	public static final String ACTION_VALIDATE_ANY = "ValidateAny";
	
	public static final String CONST_QUERY = "QUERY";
	public static final String CONST_RELATION = "RELATION";
	
	/*public enum StatusObject {
		TABLE,
		VIEW,
		EXECUTABLE,
		FK
	}*/

	public enum LimitOffsetStrategy {
		RESULTSET_CONTROL,
		SQL_LIMIT_OFFSET,
		SQL_ROWNUM,
		SQL_FETCH_FIRST //ANSI:2008? offset?
		;
		
		static final String PROPFILE_DBMS_SPECIFIC = "/dbms-specific-queryon.properties";
		static final ParametrizedProperties prop = new ParametrizedProperties();
		static {
			try {
				prop.load(LimitOffsetStrategy.class.getResourceAsStream(PROPFILE_DBMS_SPECIFIC));
			} catch (IOException e) {
				e.printStackTrace();
				throw new ExceptionInInitializerError(e);
			}
		}
		
		static LimitOffsetStrategy getDefaultStrategy(String dbid) {
			String strategyStr = prop.getProperty("dbid."+dbid+".limitoffsetstretegy", RESULTSET_CONTROL.toString());
			log.info("getLOStrategy["+dbid+"]: "+strategyStr);
			LimitOffsetStrategy strat = LimitOffsetStrategy.valueOf(strategyStr);
			return strat;
		}
		
		/*public boolean mustChangeQuery() {
			switch (this) {
			case RESULTSET_CONTROL:
				return false;
			default:
				return true;
			}
		}*/
	}
	
	static final Log log = LogFactory.getLog(QueryOn.class);

	static final String PROPERTIES_PATH = "properties-resource";
	static final String DEFAULT_PROPERTIES_RESOURCE = "/queryon.properties";
	static final String CONN_PROPS_PREFIX = "queryon";
	
	static final String PROP_DEFAULT_LIMIT = "queryon.limit.default";
	static final String PROP_MAX_LIMIT = "queryon.limit.max";
	static final String PROP_BASE_URL = "queryon.baseurl";
	static final String PROP_HEADERS_ADDCONTENTLOCATION = "queryon.headers.addcontentlocation";
	static final String PROP_XTRASYNTAXES = "queryon.xtrasyntaxes";
	static final String PROP_PROCESSORS_ON_STARTUP = "queryon.processors-on-startup";
	static final String PROP_SQLDIALECT = "queryon.sqldialect";
	static final String PROP_VALIDATE_GETMETADATA = "queryon.validate.x-getmetadata";
	static final String PROP_VALIDATE_ORDERCOLNAME = "queryon.validate.x-ordercolumnname";
	static final String PROP_VALIDATE_FILTERCOLNAME = "queryon.validate.x-filtercolumnname";
	
	static final String PROP_AUTH_ANONUSER = "queryon.auth.anon-username";
	static final String PROP_AUTH_ANONREALM = "queryon.auth.anon-realm";
	
	static final String PROP_GRABCLASS = "queryon.grabclass";
	@Deprecated static final String PROP_SQLDUMP_GRABCLASS = "sqldump.schemagrab.grabclass";
	
	static final String REQ_ATTR_CONTENTLOCATION = "attr.contentlocation";

	static final String DEFAULT_OUTPUT_SYNTAX = "html";
	
	public static final String ATTR_PROP = "prop";
	public static final String ATTR_MODEL = "model";
	
	public static final String METHOD_GET = "GET";
	public static final String METHOD_POST = "POST";
	public static final String METHOD_PUT = "PUT";
	public static final String METHOD_DELETE = "DELETE";
	
	final Properties prop = new ParametrizedProperties();
	DumpSyntaxUtils dsutils;
	SchemaModel model;
	
	boolean doFilterStatusByPermission = true; //XXX: add prop for doFilterStatusByPermission ?
	boolean validateFilterColumnNames = true;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		String propertiesResource = null;
		try {
			propertiesResource = config.getInitParameter(PROPERTIES_PATH);
			if(propertiesResource==null) { propertiesResource = DEFAULT_PROPERTIES_RESOURCE; }
			
			log.info("loading properties: "+propertiesResource);
			//XXX: path: add host port (request object needed?)? servlet mapping url-pattern? 
			String path = "http://"+InetAddress.getLocalHost().getHostName()+"/";
			//+getServletContext().getContextPath();
			prop.setProperty(PROP_BASE_URL, path);
			prop.setProperty(RDFAbstractSyntax.PROP_RDF_BASE, path);
			prop.load(QueryOn.class.getResourceAsStream(propertiesResource));

			DumpSyntaxRegistry.addSyntaxes(prop.getProperty(PROP_XTRASYNTAXES));
			
			model = modelGrabber(prop);
			dsutils = new DumpSyntaxUtils(prop);
			
			log.debug("quote:: "+DBMSResources.instance().getIdentifierQuoteString());
			validateFilterColumnNames = Utils.getPropBool(prop, PROP_VALIDATE_FILTERCOLNAME, validateFilterColumnNames);
			
			SQL.sqlIdDecorator = new StringDecorator.StringQuoterDecorator(DBMSResources.instance().getIdentifierQuoteString());
			SQL.validateOrderColumnNames = Utils.getPropBool(prop, PROP_VALIDATE_ORDERCOLNAME, SQL.validateOrderColumnNames);
			
			config.getServletContext().setAttribute(ATTR_PROP, prop);
			config.getServletContext().setAttribute(ATTR_MODEL, model);
			
			List<String> procsOnStartup = Utils.getStringListFromProp(prop, PROP_PROCESSORS_ON_STARTUP, ",");
			if(procsOnStartup!=null) {
				for(String p: procsOnStartup) {
					try {
						ProcessorServlet.doProcess(p, config);
					}
					catch(Exception e) {
						log.warn("Exception executing processor on startup: "+e, e);
						//XXX: fail on error?
					}
				}
			}
			
		} catch (Exception e) {
			String message = e.toString()+" [prop resource: "+propertiesResource+"]";
			log.error(message);
			e.printStackTrace();
			throw new ServletException(message, e);
		} catch (Error e) {
			log.error(e.toString());
			e.printStackTrace();
			throw e;
		}
	}
	
	//XXX: move to SchemaModelUtils?
	SchemaModel modelGrabber(Properties prop/*, Connection conn*/) throws ClassNotFoundException, SQLException, NamingException {
		String grabClassName = Utils.getPropWithDeprecated(prop, PROP_GRABCLASS, PROP_SQLDUMP_GRABCLASS, null);
		SchemaModelGrabber schemaGrabber = (SchemaModelGrabber) Utils.getClassInstance(grabClassName, Defs.DEFAULT_CLASSLOADING_PACKAGES);
		if(schemaGrabber==null) {
			String message = "schema grabber class '"+grabClassName+"' not found [prop '"+PROP_GRABCLASS+"']";
			log.warn(message);
			throw new RuntimeException(message);
		}
		
		DBMSResources.instance().setup(prop);
		schemaGrabber.setProperties(prop);
		
		Connection conn = null;
		if(schemaGrabber.needsConnection()) {
			conn = ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
			DBMSResources.instance().updateMetaData(conn.getMetaData());
			schemaGrabber.setConnection(conn);
		}
		SchemaModel sm = schemaGrabber.grabSchema();
		String dialect = prop.getProperty(PROP_SQLDIALECT);
		if(dialect!=null) {
			log.info("setting sql-dialect: "+dialect);
			sm.setSqlDialect(dialect);
		}
		DBMSResources.instance().updateDbId(sm.getSqlDialect());
		
		if(conn!=null) { conn.close(); }
		return sm;
	}

	//TODO: prevent sql injection
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			doService(req, resp);
		}
		catch(BadRequestException e) {
			resp.setStatus(e.getCode());
			resp.getWriter().write(e.getMessage());
		}
		catch(ServletException e) {
			e.printStackTrace();
			throw e;
		}
	}
	
	void doService(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		log.info(">> pathInfo: "+req.getPathInfo());
		
		RequestSpec reqspec = new RequestSpec(dsutils, req, prop);
		//XXX app-specific xtra parameters, like auth properties? app should extend QueryOn & implement addXtraParameters
		
		String otype = null;
		ActionType atype = null;
		DBIdentifiable dbobj = null;
		//StatusObject sobject = StatusObject.valueOf(reqspec.object)
		//XXX should status object names have special syntax? like meta:table, meta:fk
		if(Arrays.asList(STATUS_OBJECTS).contains(reqspec.object)) {
			atype = ActionType.STATUS;
			otype = reqspec.object.toLowerCase();
		}
		else if(ACTION_QUERY_ANY.equals(reqspec.object) && METHOD_POST.equals(reqspec.httpMethod)) {
			atype = ActionType.SELECT_ANY;
			otype = ActionType.SELECT_ANY.name();
		}
		else if(ACTION_VALIDATE_ANY.equals(reqspec.object) && METHOD_POST.equals(reqspec.httpMethod)) {
			atype = ActionType.VALIDATE_ANY;
			otype = ActionType.VALIDATE_ANY.name();
		}
		else {
			dbobj = SchemaModelUtils.getDBIdentifiableBySchemaAndName(model, reqspec);
			if(dbobj==null) {
				throw new BadRequestException("object not found: "+reqspec.object, HttpServletResponse.SC_NOT_FOUND);
			}
			
			if(dbobj instanceof Relation) {
				if(reqspec.httpMethod.equals(METHOD_GET)) {
					atype = ActionType.SELECT;
				}
				else if(reqspec.httpMethod.equals(METHOD_POST)) {
					atype = ActionType.INSERT; //upsert?
				}
				else if(reqspec.httpMethod.equals(METHOD_PUT)) {
					atype = ActionType.UPDATE;
				}
				else if(reqspec.httpMethod.equals(METHOD_DELETE)) {
					atype = ActionType.DELETE;
				}
				else {
					throw new BadRequestException("unknown http method: "+reqspec.httpMethod+" [obj="+reqspec.object+"]");
				}
				
				if(dbobj instanceof Table) {
					otype = DBObjectType.TABLE.name();
				}
				else if(dbobj instanceof Query) {
					otype = CONST_QUERY;
				}
				else if(dbobj instanceof View) {
					otype = DBObjectType.VIEW.name();
				}
				else {
					otype = CONST_RELATION;
				}
			}
			else if(dbobj instanceof ExecutableObject) {
				//XXX only if POST method?
				atype = ActionType.EXECUTE;
				otype = DBObjectType.EXECUTABLE.name();
			}
			else {
				throw new BadRequestException("unknown object type: "+dbobj.getClass().getName()+" [obj="+reqspec.object+"]");
			}
		}
		
		try {
			Subject currentUser = ShiroUtils.getSubject(prop);
			
			ShiroUtils.checkPermission(currentUser, otype+":"+atype, reqspec.object);
			switch (atype) {
			case SELECT: {
				Relation rel = (Relation) dbobj;
				if(rel==null) {
					log.warn("strange... rel is null");
					rel = SchemaModelUtils.getRelation(model, reqspec, true); //XXX: option to search views based on property?
				}
				doSelect(rel, reqspec, resp);
				}
				break;
			case SELECT_ANY:
				try {
					Query relation = getQuery(req);
					//XXXxx: validate first & return number of parameters?
					relation.setParameterCount( reqspec.params.size() ); //maybe not good... anyway
					doSelect(relation, reqspec, resp);
				}
				catch(SQLException e) {
					throw new BadRequestException(e.getMessage());
				}
				break;
			case VALIDATE_ANY:
				try {
					Query relation = getQuery(req);
					doValidate(relation, reqspec, resp);
				}
				catch(SQLException e) {
					throw new BadRequestException(e.getMessage());
				}
				break;
			case EXECUTE:
				ExecutableObject eo = (ExecutableObject) dbobj;
				if(eo==null) {
					log.warn("strange... eo is null");
					eo = SchemaModelUtils.getExecutable(model, reqspec);
				}
				doExecute(eo, reqspec, resp);
				break;
			case INSERT: {
				doInsert((Relation) dbobj, reqspec, resp);
				}
				break;
			case UPDATE: {
				doUpdate((Relation) dbobj, reqspec, resp);
				}
				break;
			case DELETE: {
				doDelete((Relation) dbobj, reqspec, resp);
				}
				break;
			case STATUS:
				doStatus(reqspec, currentUser, resp);
				break;
			default:
				throw new BadRequestException("Unknown action type: "+atype); 
			}
		}
		catch(BadRequestException e) {
			//XXX: do not log exception!
			log.warn("BadRequestException: "+e.getMessage());
			throw e;
		}
		catch(SQLException e) {
			throw new ServletException(e);
		}
		catch(IOException e) {
			throw new ServletException(e);
		}
		catch (ClassNotFoundException e) {
			throw new ServletException(e);
		}
		catch (NamingException e) {
			throw new ServletException(e);
		}
		catch (IntrospectionException e) {
			throw new ServletException(e);
		}
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doGet(req, resp);
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doGet(req, resp);
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doGet(req, resp);
	}
	
	Query getQuery(HttpServletRequest req) {
		Query relation = new Query();
		String name = req.getParameter("name");
		/*if(name==null || name.equals("")) {
			throw new BadRequestException("parameter 'name' undefined");
		}*/
		String sql = req.getParameter("sql");
		if(sql==null || sql.equals("")) {
			throw new BadRequestException("parameter 'sql' undefined");
		}
		relation.setName(name);
		relation.setQuery(sql);
		return relation;
	}
	
	void doSelect(Relation relation, RequestSpec reqspec, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		Connection conn = ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
		try {
		
		SQL sql = SQL.createSQL(relation, reqspec);
		
		// bind parameters for Query
		addOriginalParameters(reqspec, sql);
		
		Constraint pk = getPK(relation);
		
		filterByKey(relation, reqspec, pk, sql);

		// xtra filters
		// TODO parameters: remove reqspec.params in excess of #parametersToBind ?
		filterByXtraParams(relation, reqspec, sql);
		
		//XXX app-specific xtra filters, like auth filters? app should extend QueryOn & implement addXtraConstraints
		//appXtraConstraints(relation, sql, reqspec, req);
		
		// order by
		sql.applyOrder(reqspec);

		// projection (select columns)
		sql.applyProjection(reqspec, relation);
		
		//XXX: option to add distinct?

		//limit-offset
		//how to decide strategy? default is LimitOffsetStrategy.RESULTSET_CONTROL
		//query type (table, view, query), resultsetType? (not avaiable at this point), database type
		LimitOffsetStrategy loStrategy = LimitOffsetStrategy.getDefaultStrategy(model.getSqlDialect());
		if(loStrategy!=LimitOffsetStrategy.RESULTSET_CONTROL) {
			log.debug("pre-sql:\n"+sql.getSql());
		}
		sql.addLimitOffset(loStrategy, reqspec);
		
		//query finished!
		log.info("sql:\n"+sql.getFinalSql());
		//XXX log sql parameter values?
		
		PreparedStatement st = conn.prepareStatement(sql.getFinalSql());
		bindParameters(st, sql);
		
		ResultSet rs = st.executeQuery();
		
		boolean applyLimitOffsetInResultSet = loStrategy==LimitOffsetStrategy.RESULTSET_CONTROL;

		List<FK> fks = DBIdentifiable.getImportedKeys(relation, model.getForeignKeys());
		List<Constraint> uks = DBIdentifiable.getUKs(relation);
		
		if(Utils.getPropBool(prop, PROP_HEADERS_ADDCONTENTLOCATION, false)) {
			String contentLocation = reqspec.getCanonicalUrl(prop);
			log.info("content-location header: "+contentLocation);
			reqspec.request.setAttribute(REQ_ATTR_CONTENTLOCATION, contentLocation);
		}
		
		dumpResultSet(rs, reqspec, relation.getName(), pk!=null?pk.getUniqueColumns():null, fks, uks, applyLimitOffsetInResultSet, resp);
		
		}
		catch(SQLException e) {
			conn.rollback();
			//XXX: create new SQLException including the query string?
			throw e;
		}
		finally {
			conn.close();
		}
	}
	
	/*
	 * XXX: option to select different validate strategies (drivers may validate queries differently)
	 * - current impl
	 * - no stmt.getMetaData()
	 * - run query with limit of 0 or 1? set parameters with what? null? random?
	 */
	void doValidate(Relation relation, RequestSpec reqspec, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		Connection conn = ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
		try {
			SQL sql = SQL.createSQL(relation, reqspec);
			PreparedStatement stmt = conn.prepareStatement(sql.getFinalSql());

			ParameterMetaData pmd = stmt.getParameterMetaData();
			int params = pmd.getParameterCount();
			log.info("doValidate: #params="+params);
			boolean doGetMetadata = Utils.getPropBool(prop, PROP_VALIDATE_GETMETADATA, true);
			if(doGetMetadata) {
				stmt.getMetaData(); // needed to *really* validate query (at least on oracle)
			}

			//XXX: return number of bind parameters? return as ResultSet?
			resp.getWriter().write(String.valueOf(params));
		}
		catch(SQLException e) {
			log.info("doValidate: error validating: "+e);
			//log.debug("doValidate: error validating: "+e.getMessage(), e);
			conn.rollback();
			throw e;
		}
		finally {
			conn.close();
		}
	}
	
	/*
	 * http://docs.oracle.com/javase/6/docs/api/java/sql/CallableStatement.html
	 *  
	 * {?= call <procedure-name>[(<arg1>,<arg2>, ...)]}
	 * {call <procedure-name>[(<arg1>,<arg2>, ...)]}
	 * 
	 * The type of all OUT parameters must be registered prior to executing the stored procedure; their values are retrieved after execution via the get methods provided here.
	 */
	void doExecute(ExecutableObject eo, RequestSpec reqspec, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		log.info("eo: "+eo);
		Connection conn = ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
		try {
			
		StringBuffer sql = new StringBuffer();
		sql.append("{ "); //sql.append("begin ");
		if(eo.getType()==DBObjectType.FUNCTION) {
			sql.append("?= "); //sql.append("? := ");
		}
		sql.append("call ");
		sql.append(
			(eo.getSchemaName()!=null?eo.getSchemaName()+".":"")+
			(eo.getPackageName()!=null?eo.getPackageName()+".":"")+
			eo.getName());
		if(eo.getParams()!=null) {
			sql.append("(");
			for(int i=0;i<eo.getParams().size();i++) {
				//ExecutableParameter ep = eo.params.get(i);
				sql.append((i>0?", ":"")+"?");
			}
			sql.append(")");
		}
		sql.append(" }"); //sql.append("; end;");
		CallableStatement stmt = conn.prepareCall(sql.toString());
		int paramOffset = 1 + (eo.getType()==DBObjectType.FUNCTION?1:0);
		int outParamCount = 0;
		for(int i=0;i<eo.getParams().size();i++) {
			ExecutableParameter ep = eo.getParams().get(i);
			if(ep.getInout()==ExecutableParameter.INOUT.IN || ep.getInout()==ExecutableParameter.INOUT.INOUT) {
				stmt.setString(i+paramOffset, reqspec.params.get(i));
			}
			if(ep.getInout()==ExecutableParameter.INOUT.OUT || ep.getInout()==ExecutableParameter.INOUT.INOUT) {
				stmt.registerOutParameter(i+paramOffset, DBUtil.getSQLTypeForColumnType(ep.getDataType()));
				outParamCount++;
			}
		}
		log.info("sql exec: "+sql);
		stmt.execute();
		Object retObject = null;
		for(int i=0;i<eo.getParams().size();i++) {
			ExecutableParameter ep = eo.getParams().get(i);
			if(ep.getInout()==ExecutableParameter.INOUT.OUT || ep.getInout()==ExecutableParameter.INOUT.INOUT) {
				retObject = stmt.getObject(i+paramOffset);
			}
			if(retObject!=null) {
				if(outParamCount>1) {
					log.info("there are "+outParamCount+" out parameter. Only the first will be returned");
				}
				break; //gets first result
			}
		}

		if(retObject!=null) {
			if(retObject instanceof ResultSet) {
				dumpResultSet((ResultSet)retObject, reqspec, reqspec.object, null, null, null, true, resp);
			}
			else {
				resp.getWriter().write(retObject.toString());
			}
		}
		else {
			resp.getWriter().write("execution successful - no return");
		}

		}
		catch(SQLException e) {
			conn.rollback();
			throw e;
		}
		finally {
			conn.close();
		}
	}

	static final List<String> statusUniqueColumns = Arrays.asList(new String[]{"schemaName", "name"});
	// XXX: add "columns"?
	static final List<String> tableAllColumns =     Arrays.asList(new String[]{"columnNames", "constraints", "remarks", "relationType", "PKConstraint"});
	static final List<String> viewAllColumns  =     Arrays.asList(new String[]{"columnNames", "constraints", "remarks", "relationType", "parameterCount"});
	static final List<String> relationAllColumns  = Arrays.asList(new String[]{"columnNames", "constraints", "remarks", "relationType", "parameterCount"});
	
	void doStatus(RequestSpec reqspec, Subject currentUser, HttpServletResponse resp) throws IntrospectionException, SQLException, IOException, ServletException {
		ResultSet rs = null;
		List<FK> importedFKs = null;
		List<Constraint> uks = null;
		String objectName = null;
		//XXX: filter by schemaName, name? ResultSetFilterDecorator(rs, colpositions, colvalues)?
		if(SO_TABLE.equalsIgnoreCase(reqspec.object)) {
			objectName = SO_TABLE;
			List<Table> list = new ArrayList<Table>(); list.addAll(model.getTables());
			rs = new ResultSetListAdapter<Table>(objectName, statusUniqueColumns, tableAllColumns, list, Table.class);
			//XXX importedFKs = ...
		}
		else if(SO_VIEW.equalsIgnoreCase(reqspec.object)) {
			objectName = SO_VIEW;
			List<View> list = new ArrayList<View>(); list.addAll(model.getViews());
			rs = new ResultSetListAdapter<View>(objectName, statusUniqueColumns, viewAllColumns, list, View.class);
			//XXX importedFKs = ...
		}
		else if(SO_RELATION.equalsIgnoreCase(reqspec.object)) {
			objectName = SO_RELATION;
			List<Relation> list = new ArrayList<Relation>(); list.addAll(model.getViews()); list.addAll(model.getTables()); //XXX: sort relations?
			rs = new ResultSetListAdapter<Relation>(objectName, statusUniqueColumns, relationAllColumns, list, Relation.class);
			// importedFKs = ... ?
		}
		else if(SO_EXECUTABLE.equalsIgnoreCase(reqspec.object)) {
			objectName = SO_EXECUTABLE;
			List<ExecutableObject> list = new ArrayList<ExecutableObject>(); list.addAll(model.getExecutables());
			rs = new ResultSetListAdapter<ExecutableObject>(objectName, statusUniqueColumns, list, ExecutableObject.class);
			//XXX importedFKs = ...
		}
		else if(SO_FK.equalsIgnoreCase(reqspec.object)) {
			objectName = SO_FK;
			List<FK> list = new ArrayList<FK>(); list.addAll(model.getForeignKeys());
			rs = new ResultSetListAdapter<FK>(objectName, statusUniqueColumns, list, FK.class);
			//XXX importedFKs = ...
		}
		else {
			throw new BadRequestException("unknown object: "+reqspec.object);
		}
		
		if(reqspec.params!=null && reqspec.params.size()>0) {
			rs = new ResultSetFilterDecorator(rs, Arrays.asList(new Integer[]{1,2}), reqspec.params);
		}
		if(doFilterStatusByPermission) {
			// filter by [object-type]:SELECT:[schema]:[name]
			rs = new ResultSetPermissionFilterDecorator(rs, currentUser, "[6]:SELECT:[1]:[2]");
		}
		dumpResultSet(rs, reqspec, objectName, statusUniqueColumns, importedFKs, uks, true, resp);
		if(rs!=null) { rs.close(); }
	}
	
	void doDelete(Relation relation, RequestSpec reqspec, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException, ServletException {
		Connection conn = ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
		try {
		SQL sql = SQL.createDeleteSQL(relation);

		Constraint pk = getPK(relation);
		filterByKey(relation, reqspec, pk, sql);

		// xtra filters
		filterByXtraParams(relation, reqspec, sql);
		
		PreparedStatement st = conn.prepareStatement(sql.getFinalSql());
		bindParameters(st, sql);

		log.info("sql delete: "+sql);
		
		int count = st.executeUpdate();
		
		if(fullKeyDefined(reqspec, pk)) {
			if(count==0) {
				conn.rollback();
				throw new BadRequestException("Element not found", 404);
			}
			if(count>1) {
				//may never occur...
				conn.rollback();
				throw new ServletException("Full key defined but "+count+" elements deleted");
			}
		}
		else {
			//XXX: boundaries for # of updated (deleted) rows?
		}
		
		//XXX: (heterogeneous) array to ResultSet adapter?
		conn.commit();
		resp.getWriter().write(count+" rows deleted");
		
		}
		catch(SQLException e) {
			conn.rollback();
			throw e;
		}
		finally {
			conn.close();
		}
	}

	void doUpdate(Relation relation, RequestSpec reqspec, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		Connection conn = ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
		try {

		SQL sql = SQL.createUpdateSQL(relation);
		
		Set<String> columns = new HashSet<String>();
		columns.addAll(relation.getColumnNames());

		StringBuffer sb = new StringBuffer();
		Iterator<String> cols = reqspec.updateValues.keySet().iterator();
		for(int i=0; cols.hasNext(); i++) {
			String col = cols.next();
			if(! columns.contains(col)) {
				log.warn("unknown column: "+col);
				continue;
			}
			sb.append((i!=0?", ":"")+col+" = ?");
			sql.bindParameterValues.add(reqspec.updateValues.get(col));
		}

		if("".equals(sb.toString())) {
			throw new BadRequestException("No valid columns");
		}
		sql.applyUpdate(sb.toString());

		Constraint pk = getPK(relation);
		filterByKey(relation, reqspec, pk, sql);

		// xtra filters
		filterByXtraParams(relation, reqspec, sql);

		log.info("pre-sql update: "+sql);
		
		PreparedStatement st = conn.prepareStatement(sql.getFinalSql());
		bindParameters(st, sql);

		log.info("sql update: "+sql);
		
		int count = st.executeUpdate();
		//XXX: boundaries for # of updated rows?
		//XXX: (heterogeneous) array / map to ResultSet adapter?
		conn.commit();
		resp.getWriter().write(count+" rows updated");

		}
		catch(SQLException e) {
			conn.rollback();
			throw e;
		}
		finally {
			conn.close();
		}
	}

	void doInsert(Relation relation, RequestSpec reqspec, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		Connection conn = ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
		try {

		SQL sql = SQL.createInsertSQL(relation);

		Set<String> columns = new HashSet<String>();
		columns.addAll(relation.getColumnNames());

		//use url params to set PK cols values
		Constraint pk = getPK(relation);
		if(pk!=null) {
			for(int i=0;i<pk.getUniqueColumns().size() && i<reqspec.params.size();i++) {
				String pkcol = pk.getUniqueColumns().get(i);
				if(! columns.contains(pkcol)) {
					log.warn("unknown PK column: "+pkcol);
					continue;
				}
				String pkval = reqspec.params.get(i);
				if(pkcol!=null && pkval!=null) {
					reqspec.updateValues.put(pkcol, pkval);
				}
			}
		}
		
		StringBuffer sbCols = new StringBuffer();
		StringBuffer sbVals = new StringBuffer();
		Iterator<String> cols = reqspec.updateValues.keySet().iterator();
		for(int i=0; cols.hasNext(); i++) {
			String col = cols.next();
			if(! columns.contains(col)) {
				log.warn("unknown 'value' column: "+col);
				continue;
			}
			sbCols.append((i!=0?", ":"")+col);
			sbVals.append((i!=0?", ":"")+"?");
			sql.bindParameterValues.add(reqspec.updateValues.get(col));
		}
		
		if("".equals(sbCols.toString())) {
			//log.warn("no valid columns");
			throw new BadRequestException("No valid columns");
		}
		sql.applyInsert(sbCols.toString(), sbVals.toString());

		PreparedStatement st = conn.prepareStatement(sql.getFinalSql());
		bindParameters(st, sql);

		log.info("sql insert: "+sql);
		
		int count = st.executeUpdate();
		//XXX: boundaries for # of updated rows?
		//XXX: (heterogeneous) array / map to ResultSet adapter?
		conn.commit();
		resp.setStatus(HttpServletResponse.SC_CREATED);
		resp.getWriter().write(count+" rows inserted");
		
		}
		catch(SQLException e) {
			conn.rollback();
			throw e;
		}
		finally {
			conn.close();
		}
	}
	
	Constraint getPK(Relation relation) {
		Constraint pk = null;
		List<Constraint> conss = relation.getConstraints();
		if(conss!=null) {
			Constraint uk = null;
			for(Constraint c: conss) {
				if(c.getType()==ConstraintType.PK) { pk = c; break; }
				if(c.getType()==ConstraintType.UNIQUE && uk == null) { uk = c; }
			}
			if(pk == null && uk != null) {
				pk = uk;
			}
		}
		return pk;
	}
	
	boolean fullKeyDefined(RequestSpec reqspec, Constraint pk) {
		if(pk==null) {
			return false;
		}
		//log.info("#cols: pk="+pk.uniqueColumns.size()+", req="+reqspec.params.size());
		return pk.getUniqueColumns().size() <= reqspec.params.size();
	}
	
	void filterByKey(Relation relation, RequestSpec reqspec, Constraint pk, SQL sql) {
		String filter = "";
		//TODO: what if parameters already defined in query?
		if(reqspec.params.size()>0) {
			if(pk==null) {
				log.warn("filter params defined "+reqspec.params+" but table '"+relation.getName()+"' has no PK or UNIQUE constraint");
			}
			else {
				for(int i=0;i<pk.getUniqueColumns().size();i++) {
					if(reqspec.params.size()<=i) { break; }
					//String s = reqspec.params.get(i);
					filter += (i!=0?" and ":"")+SQL.sqlIdDecorator.get(pk.getUniqueColumns().get(i))+" = ?"; //+reqspec.params.get(i)
					sql.bindParameterValues.add(reqspec.params.get(i));
				}
			}
		}
		sql.addFilter(filter);
	}
	
	void filterByXtraParams(Relation relation, RequestSpec reqspec, SQL sql) {
		// TODO parameters: remove reqspec.params in excess of #parametersToBind ?
		
		List<String> colNames = relation.getColumnNames();
		if(colNames!=null) {
			Set<String> columns = new HashSet<String>();
			columns.addAll(colNames);
			for(String col: reqspec.filterEquals.keySet()) {
				if(!validateFilterColumnNames || columns.contains(col)) {
					//XXX column type?
					sql.bindParameterValues.add(reqspec.filterEquals.get(col));
					sql.addFilter(SQL.sqlIdDecorator.get(col)+" = ?");
				}
				else {
					log.warn("unknown column: "+col+" [relation="+relation.getName()+"]");
				}
			}
			for(String col: reqspec.filterIn.keySet()) {
				if(!validateFilterColumnNames || columns.contains(col)) {
					//XXX column type?
					StringBuffer sb = new StringBuffer();
					sb.append(SQL.sqlIdDecorator.get(col)+" in (");
					String[] values = reqspec.filterIn.get(col);
					for(int i=0;i<values.length;i++) {
						String value = values[i];
						sb.append((i>0?", ":"")+"?");
						sql.bindParameterValues.add(value);
					}
					sb.append(")");
					sql.addFilter(sb.toString());
				}
				else {
					log.warn("unknown column: "+col+" [relation="+relation.getName()+"]");
				}
			}
		}
		else {
			if(reqspec.filterEquals.size()>0) {
				log.warn("relation '"+relation.getName()+"' has no columns specified");
			}
		}
	}
	
	void addOriginalParameters(RequestSpec reqspec, SQL sql) throws SQLException {
		int informedParams = reqspec.params.size();
		//XXX bind all or bind none?
		//int bindParamsLoop = informedParams; //bind all
		int bindParamsLoop = -1; // bind none
		if(sql.originalBindParameterCount!=null) {
			if(sql.originalBindParameterCount > informedParams) {
				//XXX option to bind params with null?
				throw new BadRequestException("Query '"+reqspec.object+"' needs "+sql.originalBindParameterCount+" parameters but "
					+((informedParams>0)?"only "+informedParams:"none")
					+((informedParams>1)?" were":" was")
					+" informed");
			}
			bindParamsLoop = sql.originalBindParameterCount;
		}
		for(int i=0;i<bindParamsLoop;i++) {
			sql.bindParameterValues.add(reqspec.params.get(i));
		}
	}
	
	void bindParameters(PreparedStatement st, SQL sql) throws SQLException {
		for(int i=0;i<sql.bindParameterValues.size();i++) {
			st.setString(i+1, sql.bindParameterValues.get(i));
		}
	}
	
	void dumpResultSet(ResultSet rs, RequestSpec reqspec, String queryName, 
			List<String> uniqueColumns, List<FK> importedFKs, List<Constraint> UKs,
			boolean mayApplyLimitOffset, HttpServletResponse resp) 
			throws SQLException, IOException {
		if(mayApplyLimitOffset) {
			rs = new ResultSetLimitOffsetDecorator(rs, reqspec.limit, reqspec.offset);
		}
		int count = 0;
		DumpSyntax ds = reqspec.outputSyntax;
		if(ds.usesImportedFKs()) {
			ds.setImportedFKs(importedFKs);
		}
		if(ds.usesAllUKs()) {
			ds.setAllUKs(UKs);
		}
		
		ds.initDump(queryName, uniqueColumns, rs.getMetaData());

		resp.addHeader("Content-Type", ds.getMimeType());
		//XXX download? http://stackoverflow.com/questions/398237/how-to-use-the-csv-mime-type
		//resp.addHeader("Content-disposition", "attachment;filename="+table.name+"."+ds.getDefaultFileExtension());
		String contentLocation = (String) reqspec.request.getAttribute(REQ_ATTR_CONTENTLOCATION);
		if(contentLocation!=null) {
			resp.addHeader("Content-Location", contentLocation);
		}
		
		ds.dumpHeader(resp.getWriter());
		while(rs.next()) {
			ds.dumpRow(rs, count, resp.getWriter());
			count++;
		}
		ds.dumpFooter(count, resp.getWriter());
	}
}
