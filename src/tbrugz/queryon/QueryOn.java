package tbrugz.queryon;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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

import tbrugz.queryon.resultset.ResultSetFilterDecorator;
import tbrugz.queryon.resultset.ResultSetLimitOffsetDecorator;
import tbrugz.queryon.resultset.ResultSetListAdapter;
import tbrugz.sqldump.SQLDump;
import tbrugz.sqldump.SQLUtils;
import tbrugz.sqldump.datadump.DumpSyntax;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.Constraint.ConstraintType;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.ExecutableParameter;
import tbrugz.sqldump.dbmodel.FK;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.def.SchemaModelGrabber;
import tbrugz.sqldump.util.ParametrizedProperties;

/**
 * @see Web API Design - http://info.apigee.com/Portals/62317/docs/web%20api.pdf
 */
public class QueryOn extends HttpServlet {
	
	public enum ActionType {
		SELECT,  //done
		INSERT,
		UPDATE,
		DELETE,
		EXECUTE, //~TODOne: execute action!
		//QUERY,   //TODOne: SQLQueries action!
		STATUS   //~TODOne: or CONFIG? show model, user, vars...
		//XXXxx: FINDBYKEY action? return only the first result
	}
	
	public static final String SO_TABLE = "table", 
			SO_VIEW = "view",
			SO_EXECUTABLE = "executable",
			SO_FK = "fk";
	
	public static final String[] STATUS_OBJECTS = { SO_TABLE, SO_VIEW, SO_EXECUTABLE, SO_FK,
			SO_TABLE.toUpperCase(), SO_VIEW.toUpperCase(), SO_EXECUTABLE.toUpperCase(), SO_FK.toUpperCase()
			}; 
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
			String strategyStr = prop.getProperty("dbid."+dbid+".limitoffsetstretegy", "DEFAULT");
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

	static final String PROPERTIES_RESOURCE = "/queryon.properties";
	static final String CONN_PROPS_PREFIX = "queryon";
	static final String PROP_DEFAULT_LIMIT = "queryon.limit.default";
	static final String PROP_MAX_LIMIT = "queryon.limit.max";

	static final String DEFAULT_OUTPUT_SYNTAX = "html";
	
	final Properties prop = new ParametrizedProperties();
	DumpSyntaxUtils dsutils;
	SchemaModel model;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			prop.load(QueryOn.class.getResourceAsStream(PROPERTIES_RESOURCE));
			//Connection conn = SQLUtils.ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop, false);
			model = modelGrabber(prop);
			dsutils = new DumpSyntaxUtils(prop);
			//XXXxx: add sqlqueries as views? yes
		} catch (Exception e) {
			e.printStackTrace();
			throw new ServletException(e);
		} catch (Error e) {
			e.printStackTrace();
			throw e;
		}
	}
	
	//XXX: move to SchemaModelUtils?
	SchemaModel modelGrabber(Properties prop/*, Connection conn*/) throws ClassNotFoundException, SQLException, NamingException {
		String grabClassName = prop.getProperty(SQLDump.PROP_SCHEMAGRAB_GRABCLASS);
		SchemaModelGrabber schemaGrabber = (SchemaModelGrabber) SQLDump.getClassInstance(grabClassName, SQLDump.DEFAULT_CLASSLOADING_PACKAGES);
		if(schemaGrabber==null) {
			log.warn("schema grabber class '"+grabClassName+"' not found");
			throw new RuntimeException("schema grabber class '"+grabClassName+"' not found");
		}
		
		DBMSResources.instance().setup(prop);
		schemaGrabber.procProperties(prop);
		
		Connection conn = null;
		if(schemaGrabber.needsConnection()) {
			conn = SQLUtils.ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
			DBMSResources.instance().updateMetaData(conn.getMetaData());
			schemaGrabber.setConnection(conn);
		}
		SchemaModel sm = schemaGrabber.grabSchema();
		DBMSResources.updateDbId(sm.getSqlDialect());
		
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
			resp.setStatus(400);
			resp.getWriter().write(e.getMessage());
			//throw e;
		}
	}
	
	void doService(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		log.info(">> pathInfo: "+req.getPathInfo());
		
		RequestSpec reqspec = new RequestSpec(dsutils, req, prop);
		//XXX app-specific xtra parameters, like auth properties? app should extend QueryOn & implement addXtraParameters
		
		ActionType atype = null;
		DBIdentifiable dbobj = null;
		//StatusObject sobject = StatusObject.valueOf(reqspec.object)
		//XXX should status object names have special syntax? like meta:table, meta:fk
		if(Arrays.asList(STATUS_OBJECTS).contains(reqspec.object)) {
			atype = ActionType.STATUS;
		}
		else {
			dbobj = SchemaModelUtils.getDBIdentifiableBySchemaAndName(model, reqspec);
			if(dbobj==null) {
				throw new BadRequestException("unknown object: "+reqspec.object);
			}
			
			if(dbobj instanceof Relation) {
				atype = ActionType.SELECT;
			}
			else if(dbobj instanceof ExecutableObject) {
				atype = ActionType.EXECUTE;
			}
			else {
				throw new BadRequestException("unknown object type: "+dbobj.getClass().getName()+" [obj="+reqspec.object+"]");
			}
		}
		
		try {
			switch (atype) {
			case SELECT: {
				Relation rel = (Relation) dbobj; 
				if(rel==null) {
					log.warn("strange... rel is null");
					rel = SchemaModelUtils.getTable(model, reqspec, true); //XXX: option to search views based on property?
				}
				doSelect(rel, reqspec, resp);
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
			case STATUS:
				doStatus(reqspec, resp);
				break;
			default:
				throw new BadRequestException("Unknown action type: "+atype); 
			}
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
	
	void doSelect(Relation relation, RequestSpec reqspec, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		Connection conn = SQLUtils.ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
		
		SQL sql = SQL.createSQL(relation, reqspec);
		
		Constraint pk = null;
		List<Constraint> conss = relation.getConstraints();
		if(conss!=null) {
			Constraint uk = null;
			for(Constraint c: conss) {
				if(c.type==ConstraintType.PK) { pk = c; break; }
				if(c.type==ConstraintType.UNIQUE && uk == null) { uk = c; }
			}
			if(pk == null && uk != null) {
				pk = uk;
			}
		}
		
		// pk/uk filter
		int parametersToBind = 0;
		String filter = "";
		//TODO: what if parameters already defined in query?
		if(reqspec.params.size()>0) {
			if(pk==null) {
				log.warn("filter params defined "+reqspec.params+" but table '"+relation.getName()+"' has no PK or UNIQUE constraint");
			}
			else {
				for(int i=0;i<pk.uniqueColumns.size();i++) {
					if(reqspec.params.size()<=i) { break; }
					//String s = reqspec.params.get(i);
					filter += (i!=0?" and ":"")+pk.uniqueColumns.get(i)+" = ?"; //+reqspec.params.get(i)
					parametersToBind++;
				}
			}
		}
		sql.addFilter(filter);

		// xtra filters
		// TODO parameters: remove reqspec.params in excess of #parametersToBind ?
		List<String> colNames = relation.getColumnNames();
		if(colNames!=null) {
			Set<String> columns = new HashSet<String>();
			columns.addAll(colNames);
			for(String col: reqspec.filterEquals.keySet()) {
				if(columns.contains(col)) {
					//XXX column type?
					reqspec.params.add(reqspec.filterEquals.get(col));
					parametersToBind++;
					sql.addFilter(col+" = ?");
				}
				else {
					log.warn("unknown column: "+col+" [relation="+relation.getName()+"]");
				}
			}
			for(String col: reqspec.filterIn.keySet()) {
				if(columns.contains(col)) {
					//XXX column type?
					StringBuffer sb = new StringBuffer();
					sb.append(col+" in (");
					String[] values = reqspec.filterIn.get(col);
					for(int i=0;i<values.length;i++) {
						String value = values[i];
						sb.append((i>0?", ":"")+"?");
						reqspec.params.add(value);
						parametersToBind++;
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
		
		//XXX app-specific xtra filters, like auth filters? app should extend QueryOn & implement addXtraConstraints
		//appXtraConstraints(relation, sql, reqspec, req);

		//limit-offset
		//how to decide strategy? default is LimitOffsetStrategy.RESULTSET_CONTROL
		//query type (table, view, query), resultsetType? (not avaiable at this point), database type
		LimitOffsetStrategy loStrategy = LimitOffsetStrategy.getDefaultStrategy(model.getSqlDialect());
		if(loStrategy!=LimitOffsetStrategy.RESULTSET_CONTROL) {
			log.info("pre-sql:\n"+sql.getSql());
		}
		sql.addLimitOffset(loStrategy, reqspec);
		
		//query finished!
		log.info("sql:\n"+sql.getFinalSql());
		//XXX log sql parameter values?
		
		PreparedStatement st = conn.prepareStatement(sql.getFinalSql());
		for(int i=0;i<parametersToBind;i++) {
			st.setString(i+1, reqspec.params.get(i));
		}
		ResultSet rs = st.executeQuery();
		
		boolean applyLimitOffsetInResultSet = loStrategy==LimitOffsetStrategy.RESULTSET_CONTROL;
		
		dumpResultSet(rs, reqspec, relation.getName(), pk!=null?pk.uniqueColumns:null, applyLimitOffsetInResultSet, resp);
		conn.close();
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
		Connection conn = SQLUtils.ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
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
			if(ep.inout==ExecutableParameter.INOUT.IN || ep.inout==ExecutableParameter.INOUT.INOUT) {
				stmt.setString(i+paramOffset, reqspec.params.get(i));
			}
			if(ep.inout==ExecutableParameter.INOUT.OUT || ep.inout==ExecutableParameter.INOUT.INOUT) {
				stmt.registerOutParameter(i+paramOffset, DBUtil.getSQLTypeForColumnType(ep.dataType));
				outParamCount++;
			}
		}
		log.info("sql exec: "+sql);
		stmt.execute();
		Object retObject = null;
		for(int i=0;i<eo.getParams().size();i++) {
			ExecutableParameter ep = eo.getParams().get(i);
			if(ep.inout==ExecutableParameter.INOUT.OUT || ep.inout==ExecutableParameter.INOUT.INOUT) {
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
				dumpResultSet((ResultSet)retObject, reqspec, reqspec.object, null, true, resp);
			}
			else {
				resp.getWriter().write(retObject.toString());
			}
		}
		else {
			resp.getWriter().write("execution successful - no return");
		}
		conn.close();
	}

	static final List<String> statusUniqueColumns = Arrays.asList(new String[]{"schemaName", "name"});
	static final List<String> tableAllColumns = Arrays.asList(new String[]{"PKConstraint", "columnNames", "constraints", "remarks", "type"}); // XXX: add "columns"?
	
	void doStatus(RequestSpec reqspec, HttpServletResponse resp) throws IntrospectionException, SQLException, IOException, ServletException {
		ResultSet rs = null;
		//XXX: filter by schemaName, name? ResultSetFilterAdapter(rs, colnames, colvalues)?
		if(SO_TABLE.equalsIgnoreCase(reqspec.object)) {
			List<Table> list = new ArrayList<Table>(); list.addAll(model.getTables());
			rs = new ResultSetListAdapter<Table>("status", statusUniqueColumns, tableAllColumns, list, Table.class);
		}
		else if(SO_VIEW.equalsIgnoreCase(reqspec.object)) {
			List<View> list = new ArrayList<View>(); list.addAll(model.getViews());
			rs = new ResultSetListAdapter<View>("status", statusUniqueColumns, list, View.class);
		}
		else if(SO_EXECUTABLE.equalsIgnoreCase(reqspec.object)) {
			List<ExecutableObject> list = new ArrayList<ExecutableObject>(); list.addAll(model.getExecutables());
			rs = new ResultSetListAdapter<ExecutableObject>("status", statusUniqueColumns, list, ExecutableObject.class);
		}
		else if(SO_FK.equalsIgnoreCase(reqspec.object)) {
			List<FK> list = new ArrayList<FK>(); list.addAll(model.getForeignKeys());
			rs = new ResultSetListAdapter<FK>("status", statusUniqueColumns, list, FK.class);
		}
		else {
			throw new BadRequestException("unknown object: "+reqspec.object);
		}
		
		if(reqspec.params!=null && reqspec.params.size()>0) {
			rs = new ResultSetFilterDecorator(rs, Arrays.asList(new Integer[]{1,2}), reqspec.params);
		}
		dumpResultSet(rs, reqspec, "status", statusUniqueColumns, true, resp);
	}
	
	void dumpResultSet(ResultSet rs, RequestSpec reqspec, String queryName, List<String> uniqueColumns, boolean mayApplyLimitOffset, HttpServletResponse resp) throws SQLException, IOException {
		if(mayApplyLimitOffset) {
			rs = new ResultSetLimitOffsetDecorator(rs, reqspec.limit, reqspec.offset);
		}
		int count = 0;
		DumpSyntax ds = reqspec.outputSyntax;
		
		ds.initDump(queryName, uniqueColumns, rs.getMetaData());

		resp.addHeader("Content-type", ds.getMimeType());
		//XXX download? http://stackoverflow.com/questions/398237/how-to-use-the-csv-mime-type
		//resp.addHeader("Content-disposition", "attachment;filename="+table.name+"."+ds.getDefaultFileExtension());
		
		ds.dumpHeader(resp.getWriter());
		while(rs.next()) {
			ds.dumpRow(rs, count, resp.getWriter());
			count++;
		}
		ds.dumpFooter(resp.getWriter());
	}
}
