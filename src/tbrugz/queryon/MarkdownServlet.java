package tbrugz.queryon;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import javax.servlet.ServletException;
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
	
	public static final String PROP_PREPEND = "queryon.pages.markdown.prepend";
	public static final String PROP_APPEND = "queryon.pages.markdown.append";
	
	String prepend = null;
	String append = null;
	
	@Override
	public void init() throws ServletException {
		super.init();
		Properties appprop = (Properties) getServletContext().getAttribute(QueryOn.ATTR_PROP);
		prepend = appprop.getProperty(PROP_PREPEND);
		append = appprop.getProperty(PROP_APPEND);
	}

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
		Writer w = resp.getWriter();
		
		if(prepend!=null) {
			w.write(prepend);
		}
		w.write(md);
		if(append!=null) {
			w.write(append);
		}
		
		rs.close();
	}

}
