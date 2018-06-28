package tbrugz.queryon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletConfig;
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
	public static final String SUFFIX_URL_404 = ".url-404";
	
	public static final String DEFAULT_PAGES_TABLE = "QON_PAGES";
	public static final String DEFAULT_INDEX = "index.html";
	
	public static final String HEADER_PAGE_ID = "X-Page-Id";
	
	public static final String ATTR_REQ_FORWARDED = "req.forwarded";
	
	String relation = null;
	String notFoundUrl = null;
	String indexFile = DEFAULT_INDEX; //XXX: add prop for index(.html) ?
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		Properties prop = (Properties) config.getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		// get qon_pages table
		relation = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_PAGES_TABLE);
		
		// get not found (404) url
		notFoundUrl = prop.getProperty(PROP_PREFIX+SUFFIX_URL_404);
		if(notFoundUrl!=null) {
			log.info("init: not found [404] url = "+notFoundUrl);
		}
	}
	
	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		// get path
		String pathInfo = req.getPathInfo();
		if(pathInfo==null) { throw new BadRequestException("URL (path-info) must not be null"); }
		pathInfo = pathInfo.substring(1);
		log.info("pathInfo = "+pathInfo+" ; req.getPathInfo() = "+req.getPathInfo()+" ; req.getQueryString() = "+req.getQueryString());
		
		Properties prop = (Properties) req.getServletContext().getAttribute(QueryOn.ATTR_PROP);

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
		catch(NotFoundException e) {
			if(notFoundUrl!=null) {
				forwardNotFound(req, resp, pathInfo, e);
			}
			else {
				throw e;
			}
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
		
		resp.setContentType(mimeType);
		//resp.setHeader(HEADER_PAGE_ID, id);

		boolean hasWritterOutput = false;
		// character stream
		{
			Reader reader = rs.getCharacterStream(3);
			if(reader!=null) {
				boolean wasNull = rs.wasNull();
				if(!wasNull) {
					//log.info("col4 was not null");
					Writer writer = resp.getWriter();
					IOUtil.pipeCharacterStreams(reader, writer);
					hasWritterOutput = true;
				}
				reader.close();
			}
		}

		// binary stream
		if(!hasWritterOutput) {
			//log.info("col3 was null");
			InputStream is = rs.getBinaryStream(4);
			boolean wasNull = rs.wasNull();
			if(is!=null) {
				if(!wasNull) {
					//log.info("col4 was not null");
					OutputStream os = resp.getOutputStream();
					IOUtil.pipeStreams(is, os);
					hasWritterOutput = true;
				}
				is.close();
			}
		}
		
		if(!hasWritterOutput) {
			sendNoContent(resp, pathInfo, id);
		}
		
		rs.close();
	}
	
	/*
	 * XXX: add HAS_BODY, HAS_BINARY_DATA - populated by trigger? UpdatePlugin? - so that it can be shown in pages list
	 */
	ResultSet getPage(Connection conn, String relation, String pathInfo) throws SQLException, IOException {
		String sql = "select id, mime, body, binary_data, has_body"+
				"\nfrom "+relation+" where path = ?";
		List<String> params = new ArrayList<String>();
		params.add(pathInfo);
		
		if(pathInfo.endsWith("/") && indexFile!=null) {
			sql += " or path = ?";
			params.add(pathInfo+indexFile);
		}
		
		PreparedStatement st = conn.prepareStatement(sql);
		for(int i=0;i<params.size();i++) {
			st.setString(i+1, params.get(i));
		}
		
		ResultSet rs = st.executeQuery();
		
		if(!rs.next()) {
			rs.close();
			throw new NotFoundException("resource not found: "+pathInfo);
		}
		return rs;
	}
	
	void forward(HttpServletRequest req, HttpServletResponse resp, String relation, String id) throws ServletException, IOException {
		String redir = "/q/"+relation+"?p1="+id+"&valuefield=BODY&mimetypefield=MIME";
		log.info("redir = "+redir);
		//resp.setHeader(HEADER_PAGE_ID, id);
		req.getRequestDispatcher(redir).forward(req, resp);
	}
	
	void sendNoContent(HttpServletResponse resp, String pathInfo, String id) {
		log.info("page '"+pathInfo+"' has no content [id="+id+"]");
		resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}

	void forwardNotFound(HttpServletRequest req, HttpServletResponse resp, String pathInfo, NotFoundException e) throws ServletException, IOException {
		log.info("redir [404] = "+notFoundUrl+" ; "+req.getPathInfo());
		Boolean isForwarded = (Boolean) req.getAttribute(ATTR_REQ_FORWARDED);
		if(isForwarded!=null && isForwarded) {
			log.warn("redir [404]: already forwarded... infinite loop? ["+notFoundUrl+"]");
			throw e;
		}
		if(req.getPathInfo().contains(notFoundUrl)) {
			log.warn("redir [404]: notFoundUrl ["+notFoundUrl+"] not found itself!");
			throw e;
		}
		resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
		req.setAttribute(ATTR_REQ_FORWARDED, true);
		//req.setAttribute(RequestDispatcher.ERROR_EXCEPTION, e);
		req.getRequestDispatcher(notFoundUrl).forward(req, resp);
	}
	
	static final int BUFFER_SIZE = 1024*8;

	/*@Deprecated
	public static void pipeCharacterStreams(Reader r, Writer w) throws IOException {
		char[] buffer = new char[BUFFER_SIZE];
		int len;
		while ((len = r.read(buffer)) != -1) {
			w.write(buffer, 0, len);
		}
	}*/

}
