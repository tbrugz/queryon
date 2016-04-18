package tbrugz.queryon;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.http.HttpServletResponse;

import org.pegdown.PegDownProcessor;

/*
 * see: https://github.com/sirthias/pegdown
 * 
 * XXX: add prepend & append html
 */
public class MarkdownServlet extends PagesServlet {
	
	private static final long serialVersionUID = 1L;
	
	//public static final String MD_MIMETYPE = "text/markdown";
	public static final String HTML_MIMETYPE = "text/html";

	void getAndDumpPage(Connection conn, String relation, String pathInfo, HttpServletResponse resp) throws SQLException, IOException {
		ResultSet rs = getPage(conn, relation, pathInfo);
		//String id = rs.getString(1);
		//String mimeType = rs.getString(2);
		String body = rs.getString(3);
		
		//resp.setContentType(mimeType);
		resp.setContentType(HTML_MIMETYPE);
		//resp.setHeader(HEADER_PAGE_ID, id);
		PegDownProcessor pdp = new PegDownProcessor();
		String md = pdp.markdownToHtml(body);
		resp.getWriter().write(md);
		
		rs.close();
	}

}
