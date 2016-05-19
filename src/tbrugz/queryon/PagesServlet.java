package tbrugz.queryon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.IOUtil;

/*
 * TODO: filter by ROLES_FILTER?
 */
public class PagesServlet extends AbstractHttpServlet {

	private static final long serialVersionUID = 1L;
	
	private static final Log log = LogFactory.getLog(PagesServlet.class);
	
	public static final String PROP_PREFIX = "queryon.qon-pages";
	public static final String SUFFIX_TABLE = ".table";
	
	public static final String DEFAULT_PAGES_TABLE = "QON_PAGES";
	
	public static final String HEADER_PAGE_ID = "X-Page-Id";

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
		
		// get and dump page
		String modelId = SchemaModelUtils.getModelId(req);
		Connection conn = DBUtil.initDBConn(prop, modelId);
		try {
			getAndDumpPage(conn, relation, pathInfo, resp);
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		} 
	}
	
	/*String getId(Connection conn, String relation, String pathInfo) throws SQLException {
		PreparedStatement st = conn.prepareStatement("select id from "+relation+" where path = ?");
		st.setString(1, pathInfo);
		
		ResultSet rs = st.executeQuery();
		if(rs.next()) {
			String id = rs.getString(1);
			rs.close();
			return id;
		}
		throw new NotFoundException("resource not found: "+pathInfo);
	}*/

	/*void getAndDumpPage(Connection conn, String relation, String pathInfo, HttpServletResponse resp) throws SQLException {
		PreparedStatement st = conn.prepareStatement("select id, mime, body from "+relation+" where path = ?");
		st.setString(1, pathInfo);
		
		ResultSet rs = st.executeQuery();
		
		QueryOn.dumpBlob(rs,
				reqspec,
				queryName,
				false, //mayApplyLimitOffset
				resp);

		rs.close();
	}*/

	void getAndDumpPage(Connection conn, String relation, String pathInfo, HttpServletResponse resp) throws SQLException, IOException {
		ResultSet rs = getPage(conn, relation, pathInfo);
		String id = rs.getString(1);
		String mimeType = rs.getString(2);
		//String body = rs.getString(3);
		
		resp.setContentType(mimeType);
		//resp.setHeader(HEADER_PAGE_ID, id);
		InputStream is = rs.getBinaryStream(3);
		boolean wasNull = rs.wasNull();
		if(wasNull) {
			//log.info("col3 was null");
			is = rs.getBinaryStream(4);
			wasNull = rs.wasNull();
		}
		
		if(is!=null) {
			if(!wasNull) {
				//log.info("col4 was not null");
				OutputStream os = resp.getOutputStream();
				IOUtil.pipeStreams(is, os);
			}
			else {
				//log.warn("cols 3 and 4 were null");
				sendNoContent(resp, pathInfo, id);
			}
			is.close();
		}
		else {
			sendNoContent(resp, pathInfo, id);
		}
		
		rs.close();
	}
	
	/*
	 * XXX: add HAS_BODY, HAS_BINARY_DATA - populated by trigger? UpdatePlugin? - so that it can be shown in pages list
	 */
	ResultSet getPage(Connection conn, String relation, String pathInfo) throws SQLException, IOException {
		PreparedStatement st = conn.prepareStatement("select id, mime, body, binary_data, has_body"+
				"\nfrom "+relation+" where path = ?");
		st.setString(1, pathInfo);
		
		ResultSet rs = st.executeQuery();
		
		if(!rs.next()) {
			rs.close();
			throw new NotFoundException("resource not found: "+pathInfo);
		}
		return rs;
	}
	
	void forward(HttpServletRequest req, HttpServletResponse resp, String relation, String id) throws ServletException, IOException {
		String redir = "/q/"+relation+"?p1="+id+"&valuefield=BODY&mimefield=MIME";
		log.info("redir = "+redir);
		//resp.setHeader(HEADER_PAGE_ID, id);
		req.getRequestDispatcher(redir).forward(req, resp);
	}
	
	void sendNoContent(HttpServletResponse resp, String pathInfo, String id) {
		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		log.info("page '"+pathInfo+"' has no content [id="+id+"]");
	}

}
