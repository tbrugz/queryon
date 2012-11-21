package tbrugz.queryon;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.resultset.ResultSetCollectionAdapter;
import tbrugz.queryon.resultset.ResultSetFilterDecorator;
import tbrugz.sqldump.SQLDump;
import tbrugz.sqldump.SQLUtils;
import tbrugz.sqldump.datadump.DumpSyntax;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.Constraint.ConstraintType;
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
import tbrugz.sqldump.util.Utils;

public class QueryOn extends HttpServlet {
	
	public enum ActionType {
		SELECT,  //done
		INSERT,
		UPDATE,
		DELETE,
		EXECUTE, //~TODO: execute action!
		QUERY,   //TODOne: SQLQueries action!
		STATUS   //~TODO: or CONFIG? show model, user, vars...
		//XXX: FINDBYKEY action? return only the first result
	}
	
	static final Log log = LogFactory.getLog(QueryOn.class);

	static final String PROPERTIES_RESOURCE = "/queryon.properties";
	static final String CONN_PROPS_PREFIX = "queryon";
	static final String DEFAULT_OUTPUT_SYNTAX = "html";
	
	Properties prop = new ParametrizedProperties();
	SchemaModel model;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			prop.load(QueryOn.class.getResourceAsStream(PROPERTIES_RESOURCE));
			//Connection conn = SQLUtils.ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop, false);
			model = modelGrabber(prop);
			//XXX: add sqlqueries as views?
		} catch (Exception e) {
			e.printStackTrace();
			throw new ServletException(e);
		} catch (Error e) {
			e.printStackTrace();
			throw e;
		}
	}
	
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
		log.info(">> pathInfo: "+req.getPathInfo());
		
		RequestSpec reqspec = new RequestSpec(this, req, prop);
		
		//XXX: validate column names
		
		ActionType atype = null;
		try {
			atype = ActionType.valueOf(reqspec.action);
		}
		catch(IllegalArgumentException e) {
			throw new ServletException("Unknown action: "+reqspec.action);
		}
		
		try {
			switch (atype) {
			case SELECT: {
				Relation rel = SchemaModelUtils.getTable(model, reqspec, true); //XXX: option to search views based on property?
				doSelect(rel, reqspec, resp);
				}
				break;
			case QUERY: {
				Relation rel = SchemaModelUtils.getView(model, reqspec);
				doSelect(rel, reqspec, resp);
				}
				break;
			case EXECUTE:
				ExecutableObject eo = SchemaModelUtils.getExecutable(model, reqspec);
				doExecute(eo, reqspec, resp);
				break;
			case STATUS:
				doStatus(reqspec, resp);
				break;
			default:
				throw new ServletException("Unknown action: "+reqspec.action); 
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
	
	static final String PARAM_WHERE_CLAUSE = "[where-clause]";
	static final String PARAM_FILTER_CLAUSE = "[filter-clause]";
	// order-clause? limit/offset-clause?
	
	void doSelect(Relation relation, RequestSpec reqspec, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException, ServletException {
		Connection conn = SQLUtils.ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
		
		boolean isSQLWrapped = false;
		
		String sql = null;
		if(relation instanceof Table) {
			sql = createSQL((Table)relation, reqspec);
		}
		else if(relation instanceof View) {
			//XXX: other query builder strategy besides [where-clause]? contains 'cursor'?
			sql = ((View)relation).query;
		}
		else {
			throw new ServletException("unknown relation type: "+relation.getClass().getName());
		}
		
		Constraint pk = null;
		List<Constraint> conss = relation.getConstraints();
		if(conss!=null) {
			for(Constraint c: conss) {
				if(c.type==ConstraintType.PK) { pk = c; break; }
			}
		}
		
		int parametersToBind = 0;
		String filter = "";
		//TODO: what if parameters already defined in query?
		if(reqspec.params.size()>0 && pk!=null) {
			//Constraint pk = relation.getPKConstraint();
			for(int i=0;i<pk.uniqueColumns.size();i++) {
				if(reqspec.params.size()<=i) { break; }
				//String s = reqspec.params.get(i);
				filter += (i!=0?" and ":"")+pk.uniqueColumns.get(i)+" = ?"; //+reqspec.params.get(i)
				parametersToBind++;
			}
		}
		if(sql.contains(PARAM_WHERE_CLAUSE)) {
			sql = sql.replace(PARAM_WHERE_CLAUSE, filter.length()>0?" where "+filter:"");
		}
		else if(sql.contains(PARAM_FILTER_CLAUSE)) {
			sql = sql.replace(PARAM_FILTER_CLAUSE, filter.length()>0? " and "+filter:"");
		}
		else if(filter!=null && !filter.equals("")) {
			//FIXME: if selecting from Table object, do not need to wrap
			sql = "select * from ( "+sql+" )";
			isSQLWrapped = true;
			sql += " where "+filter;
			
			/*if(!isSQLWrapped) {
				log.warn("sql may be malformed. sql: "+sql);
			}*/
		}
		log.info("sql: "+sql);
		
		PreparedStatement st = conn.prepareStatement(sql);
		for(int i=0;i<parametersToBind;i++) {
			st.setString(i+1, reqspec.params.get(i));
		}
		ResultSet rs = st.executeQuery();
		
		dumpResultSet(rs, reqspec, relation.getName(), pk!=null?pk.uniqueColumns:null, resp);
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
		if(eo.type==DBObjectType.FUNCTION) {
			sql.append("?= "); //sql.append("? := ");
		}
		sql.append("call ");
		sql.append(
			(eo.getSchemaName()!=null?eo.getSchemaName()+".":"")+
			(eo.packageName!=null?eo.packageName+".":"")+
			eo.getName());
		if(eo.params!=null) {
			sql.append("(");
			for(int i=0;i<eo.params.size();i++) {
				//ExecutableParameter ep = eo.params.get(i);
				sql.append((i>0?", ":"")+"?");
			}
			sql.append(")");
		}
		sql.append(" }"); //sql.append("; end;");
		CallableStatement stmt = conn.prepareCall(sql.toString());
		int paramOffset = 1 + (eo.type==DBObjectType.FUNCTION?1:0);
		int outParamCount = 0;
		for(int i=0;i<eo.params.size();i++) {
			ExecutableParameter ep = eo.params.get(i);
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
		for(int i=0;i<eo.params.size();i++) {
			ExecutableParameter ep = eo.params.get(i);
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
				dumpResultSet((ResultSet)retObject, reqspec, reqspec.object, null, resp);
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
	
	void doStatus(RequestSpec reqspec, HttpServletResponse resp) throws IntrospectionException, SQLException, IOException, ServletException {
		ResultSet rs = null;
		//XXX: filter by schemaName, name? ResultSetFilterAdapter(rs, colnames, colvalues)?
		if("table".equalsIgnoreCase(reqspec.object)) {
			rs = new ResultSetCollectionAdapter<Table>("status", statusUniqueColumns, model.getTables());
		}
		else if("view".equalsIgnoreCase(reqspec.object)) {
			rs = new ResultSetCollectionAdapter<View>("status", statusUniqueColumns, model.getViews());
		}
		else if("executable".equalsIgnoreCase(reqspec.object)) {
			rs = new ResultSetCollectionAdapter<ExecutableObject>("status", statusUniqueColumns, model.getExecutables());
		}
		else if("fk".equalsIgnoreCase(reqspec.object)) {
			rs = new ResultSetCollectionAdapter<FK>("status", statusUniqueColumns, model.getForeignKeys());
		}
		else {
			throw new ServletException("unknown object: "+reqspec.object);
		}
		
		if(reqspec.params!=null && reqspec.params.size()>0) {
			rs = new ResultSetFilterDecorator(rs, Arrays.asList(new Integer[]{1,2}), reqspec.params);
		}
		dumpResultSet(rs, reqspec, "status", statusUniqueColumns, resp);
	}
	
	void dumpResultSet(ResultSet rs, RequestSpec reqspec, String queryName, List<String> uniqueColumns, HttpServletResponse resp) throws SQLException, IOException {
		if(reqspec.offset>0) {
			rs.absolute(reqspec.offset);
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
			if(reqspec.length>0 && count>reqspec.length) break;
		}
		ds.dumpFooter(resp.getWriter());
	}
	
	Map<String, DumpSyntax> syntaxes = new HashMap<String, DumpSyntax>();
	
	static String createSQL(Table table, RequestSpec reqspec) {
		String columns = "*";
		if(reqspec.columns.size()>0) {
			columns = Utils.join(reqspec.columns, ", ");
		}
		String sql = "select "+columns+
			" from " + (table.getSchemaName()!=null?table.getSchemaName()+".":"") + table.getName();
		return sql;
	}
	
	DumpSyntax getDumpSyntax(String format, Properties prop) {
		DumpSyntax dsx = syntaxes.get(format);
		if(dsx!=null) { return dsx; }
		
		for(Class<? extends DumpSyntax> dsc: DumpSyntax.getSyntaxes()) {
			DumpSyntax ds = (DumpSyntax) Utils.getClassInstance(dsc);
			if(ds!=null && ds.getSyntaxId().equals(format)) {
				ds.procProperties(prop);
				syntaxes.put(format, ds);
				return ds;
			}
		}
		return null;
	}
}
