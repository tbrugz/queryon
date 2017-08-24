package tbrugz.queryon;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.pegdown.Extensions;
import org.pegdown.PegDownProcessor;

/*
 * see: https://github.com/sirthias/pegdown
 */
public class MarkdownServlet extends PagesServlet {
	
	private static final long serialVersionUID = 1L;
	
	//public static final String MD_MIMETYPE = "text/markdown";
	public static final String HTML_MIMETYPE = "text/html";
	
	//public static final String PROP_XPEND = "queryon.pages.markdown.xpend";
	public static final String PROP_PREPEND = "queryon.pages.markdown.prepend";
	public static final String PROP_APPEND = "queryon.pages.markdown.append";

	public static final String DEFAULT_INDEX = "index.md";
	
	public static final int DEFAULT_PEGDOWN_OPTIONS = Extensions.EXTANCHORLINKS | Extensions.TABLES | Extensions.STRIKETHROUGH | Extensions.TASKLISTITEMS;
	
	boolean doXpend = true;
	boolean reqXpend = doXpend;
	String prepend = null;
	String append = null;
	
	@Override
	public void init() throws ServletException {
		super.init();
		Properties prop = (Properties) getServletContext().getAttribute(QueryOn.ATTR_PROP);
		//doXpend = Utils.getPropBool(prop, PROP_XPEND, doXpend);
		prepend = prop.getProperty(PROP_PREPEND);
		append = prop.getProperty(PROP_APPEND);
		indexFile = DEFAULT_INDEX;
	}
	
	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		String xpendStr = req.getParameter("xpend");
		if(xpendStr!=null) {
			reqXpend = "true".equals(xpendStr);
		}
		else {
			reqXpend = doXpend;
		}
		super.doProcess(req, resp);
	}

	void getAndDumpPage(Connection conn, String relation, String pathInfo, HttpServletResponse resp) throws SQLException, IOException {
		ResultSet rs = getPage(conn, relation, pathInfo);
		//String id = rs.getString(1);
		//String mimeType = rs.getString(2);
		String body = rs.getString(3);
		
		//resp.setContentType(mimeType);
		resp.setContentType(HTML_MIMETYPE);
		//resp.setHeader(HEADER_PAGE_ID, id);
		int pegDownOpts = DEFAULT_PEGDOWN_OPTIONS;
		PegDownProcessor pdp = new PegDownProcessor(pegDownOpts);
		String md = pdp.markdownToHtml(body);
		Writer w = resp.getWriter();
		
		if(reqXpend && prepend!=null) {
			w.write(prepend);
		}
		w.write(md);
		if(reqXpend && append!=null) {
			w.write(append);
		}
		
		rs.close();
	}

}
