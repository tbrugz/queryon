package tbrugz.queryon;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.exception.NotFoundException;
import tbrugz.sqldump.util.ConnectionUtil;

public class PagesServlet extends AbstractHttpServlet {

	private static final long serialVersionUID = 1L;
	
	private static final Log log = LogFactory.getLog(PagesServlet.class);
	
	public static final String PROP_PREFIX = "queryon.qon-pages";
	public static final String SUFFIX_TABLE = ".table";
	
	public static final String DEFAULT_PAGES_TABLE = "QON_PAGES";

	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		// get path
		String pathInfo = req.getPathInfo();
		if(pathInfo==null) { throw new BadRequestException("URL (path-info) must not be null"); }
		pathInfo = pathInfo.substring(1);
		log.info("pathInfo = "+pathInfo+" ; req.getPathInfo() = "+req.getPathInfo());
		
		Properties prop = (Properties) req.getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		// get qon_pages table
		String relation = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_PAGES_TABLE);

		// get id from URL parameter
		String id = req.getParameter("id");
		if(id!=null) {
			forward(req, resp, relation, id);
			return;
		}
		
		// get id from QON_PAGES
		String modelId = SchemaModelUtils.getModelId(req);
		Connection conn = DBUtil.initDBConn(prop, modelId);
		try {
			id = getId(conn, relation, pathInfo);
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		} 
		
		// redirect
		forward(req, resp, relation, id);
	}
	
	String getId(Connection conn, String relation, String pathInfo) throws SQLException {
		PreparedStatement st = conn.prepareStatement("select id from "+relation+" where path = ?");
		st.setString(1, pathInfo);
		
		ResultSet rs = st.executeQuery();
		if(rs.next()) {
			String id = rs.getString(1);
			rs.close();
			return id;
		}
		throw new NotFoundException("resource not found: "+pathInfo);
	}
	
	void forward(HttpServletRequest req, HttpServletResponse resp, String relation, String id) throws ServletException, IOException {
		String redir = "/q/"+relation+"?p1="+id+"&valuefield=BODY&mimefield=MIME";
		log.info("redir = "+redir);
		req.getRequestDispatcher(redir).forward(req, resp);
	}

}