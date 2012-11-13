package tbrugz.queryon;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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

import tbrugz.sqldump.SQLDump;
import tbrugz.sqldump.SQLUtils;
import tbrugz.sqldump.datadump.DumpSyntax;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.def.SchemaModelGrabber;
import tbrugz.sqldump.util.ParametrizedProperties;
import tbrugz.sqldump.util.Utils;

public class QueryOn extends HttpServlet {
	
	public enum ActionType {
		SELECT,
		INSERT,
		UPDATE,
		DELETE,
		EXECUTE, //TODO: execute action!
		QUERY,   //TODO: SQLQueries action!
		CONFIG   //or status? show model, user, vars...
	}
	
	//XXX: order by? 3a,1d,2d?
	public static class RequestSpec {
		String object = null;
		String action = null;
		int offset, length;
		List<String> columns = new ArrayList<String>();
		List<String> params = new ArrayList<String>();
		String outputTypeStr = DEFAULT_OUTPUT_SYNTAX;
		DumpSyntax outputSyntax = null;
		
		public RequestSpec(QueryOn qon, HttpServletRequest req, Properties prop) throws ServletException {
			String varUrl = req.getPathInfo();
			int lastDotIndex = varUrl.lastIndexOf('.');
			if(lastDotIndex>-1) {
				outputTypeStr = varUrl.substring(lastDotIndex+1);
				varUrl = varUrl.substring(0, lastDotIndex);
			}
			
			String[] URIparts = varUrl.split("/");
			List<String> URIpartz = new ArrayList<String>( Arrays.asList(URIparts) );
			log.info("urlparts: "+URIpartz);
			if(URIpartz.size()<3) { throw new ServletException("URL must have at least 2 parts"); }

			object = URIpartz.remove(0);
			if(object == null || object.equals("")) {
				//first part may be empty
				object = URIpartz.remove(0);
			}
			action = URIpartz.remove(0);
			action = action.toUpperCase();
			for(int i=0;i<URIpartz.size();i++) {
				params.add(URIpartz.get(i));
			}
			
			outputSyntax = qon.getDumpSyntax(outputTypeStr, prop);
			if(outputSyntax == null) {
				throw new ServletException("Unknown output syntax: "+outputTypeStr);
			}
			//---------------------
			
			String offsetStr = req.getParameter("offset");
			if(offsetStr!=null) offset = Integer.parseInt(offsetStr);

			String lengthStr = req.getParameter("length");
			if(lengthStr!=null) length = Integer.parseInt(lengthStr);
			
			for(int i=1;;i++) {
				String value = req.getParameter("c"+i);
				if(value==null) break;
				columns.add(value);
			}

			for(int i=1;;i++) {
				String value = req.getParameter("p"+i);
				if(value==null) break;
				params.add(value);
			}
		}
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
		
		String[] objectParts = reqspec.object.split("\\.");
		
		Table table = null;
		if(objectParts.length>1) {
			table = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getTables(), DBObjectType.TABLE, objectParts[0], objectParts[1]);
		}
		else {
			table = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getTables(), DBObjectType.TABLE, objectParts[0]);
		}
		
		if(table == null) { throw new ServletException("Object "+reqspec.object+" not found"); }
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
			case SELECT:
				doSelect(table, reqspec, req, resp);
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
	}
	
	void doSelect(Table table, RequestSpec reqspec, HttpServletRequest req, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException, NamingException {
		Connection conn = SQLUtils.ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
		String columns = "*";
		if(reqspec.columns.size()>0) {
			columns = Utils.join(reqspec.columns, ", ");
		}
		String sql = "select "+columns+
			" from " + (table.getSchemaName()!=null?table.getSchemaName()+".":"") + table.getName();
		int parametersToBind = 0;
		if(reqspec.params.size()>0) {
			Constraint pk = table.getPKConstraint();
			sql += " where ";
			for(int i=0;i<pk.uniqueColumns.size();i++) {
				if(reqspec.params.size()<=i) { break; }
				//String s = reqspec.params.get(i);
				sql += (i!=0?" and ":"")+pk.uniqueColumns.get(i)+" = ?"; //+reqspec.params.get(i)
				parametersToBind++;
			}
		}
		log.info("sql: "+sql);
		
		PreparedStatement st = conn.prepareStatement(sql);
		for(int i=0;i<parametersToBind;i++) {
			st.setString(i+1, reqspec.params.get(i));
		}
		ResultSet rs = st.executeQuery();
		if(reqspec.offset>0) {
			rs.absolute(reqspec.offset);
		}
		int count = 0;
		DumpSyntax ds = reqspec.outputSyntax;
		
		ds.initDump(table.getName(), 
				table.getPKConstraint()!=null?table.getPKConstraint().uniqueColumns:null,
				rs.getMetaData());

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
		conn.close();
	}
	
	Map<String, DumpSyntax> syntaxes = new HashMap<String, DumpSyntax>();
	
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
