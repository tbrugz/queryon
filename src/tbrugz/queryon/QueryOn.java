package tbrugz.queryon;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.sqldump.SQLDump;
import tbrugz.sqldump.SQLUtils;
import tbrugz.sqldump.datadump.CSVDataDump;
import tbrugz.sqldump.datadump.DumpSyntax;
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
		EXECUTE
	}
	
	//TODO: order by?
	public static class RequestSpec {
		int offset, length;
		List<String> columns = new ArrayList<String>();
		List<String> params = new ArrayList<String>();
		//String outputFormat = "csv"; //XXX: change
		DumpSyntax outputSyntax = new CSVDataDump();
		
		public RequestSpec(HttpServletRequest req) {
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
	
	static Log log = LogFactory.getLog(SQLDump.class);

	static final String PROPERTIES_RESOURCE = "/queryon.properties";
	static final String CONN_PROPS_PREFIX = "queryon";
	
	Properties prop = new ParametrizedProperties();
	SchemaModel model;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			prop.load(QueryOn.class.getResourceAsStream(PROPERTIES_RESOURCE));
			//Connection conn = SQLUtils.ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop, false);
			modelGrabber(model, prop);
			//XXX: add sqlqueries as views?
		} catch (Exception e) {
			throw new ServletException(e);
		}
	}
	
	void modelGrabber(SchemaModel sm, Properties prop/*, Connection conn*/) throws ClassNotFoundException, SQLException {
		String grabClassName = prop.getProperty(SQLDump.PROP_SCHEMAGRAB_GRABCLASS);
		SchemaModelGrabber schemaGrabber = (SchemaModelGrabber) SQLDump.getClassInstance(grabClassName, SQLDump.DEFAULT_CLASSLOADING_PACKAGES);
		if(schemaGrabber==null) {
			log.warn("schema grabber class '"+grabClassName+"' not found");
			throw new RuntimeException("schema grabber class '"+grabClassName+"' not found");
		}
		
		schemaGrabber.procProperties(prop);
		if(schemaGrabber.needsConnection()) {
			Connection conn = SQLUtils.ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
			DBMSResources.instance().updateMetaData(conn.getMetaData());
			schemaGrabber.setConnection(conn);
		}
		sm = schemaGrabber.grabSchema();
		DBMSResources.updateDbId(sm.getSqlDialect());
		//TODO: close connection
	}

	//TODO: prevent sql injection
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		String URI = req.getRequestURI();
		String[] URIparts = URI.split("/");
		if(URIparts.length<2) { throw new ServletException("URL must have at least 2 parts"); }
		
		String object = URIparts[0];
		String action = URIparts[1];
		
		String[] objectParts = object.split("\\.");
		
		Table table = null;
		if(objectParts.length>1) {
			table = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(model.getTables(), DBObjectType.TABLE, objectParts[0], objectParts[1]);
		}
		else {
			table = DBIdentifiable.getDBIdentifiableByTypeAndName(model.getTables(), DBObjectType.TABLE, objectParts[0]);
		}
		
		if(table == null) { throw new ServletException("Object "+object+" not found"); }
		action = action.toUpperCase();
		
		ActionType atype = null;
		try {
			atype = ActionType.valueOf(action);
		}
		catch(IllegalArgumentException e) {
			throw new ServletException("Unknown action: "+action);
		}
		
		RequestSpec reqspec = new RequestSpec(req);
		
		try {
			switch (atype) {
			case SELECT:
				doSelect(table, reqspec, req, resp);
				break;
			default:
				throw new ServletException("Unknown action: "+action); 
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
	}
	
	void doSelect(Table table, RequestSpec reqspec, HttpServletRequest req, HttpServletResponse resp) throws IOException, ClassNotFoundException, SQLException {
		Connection conn = SQLUtils.ConnectionUtil.initDBConnection(CONN_PROPS_PREFIX, prop);
		String columns = "*";
		if(reqspec.columns.size()>0) {
			columns = Utils.join(reqspec.columns, ", ");
		}
		String sql = "select "+columns+
			" from " + (table.getSchemaName()!=null?table.getSchemaName()+".":"") + table.name;
		PreparedStatement st = conn.prepareStatement(sql);
		ResultSet rs = st.executeQuery();
		if(reqspec.offset>0) {
			rs.absolute(reqspec.offset);
		}
		int count = 0;
		DumpSyntax ds = reqspec.outputSyntax;
		
		ds.dumpHeader(resp.getWriter());
		while(rs.next()) {
			ds.dumpRow(rs, count, resp.getWriter());
			count++;
			if(reqspec.length>0 && count>reqspec.length) break;
		}
		ds.dumpFooter(resp.getWriter());
	}
}
