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

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import tbrugz.queryon.util.QOnContextUtils;
import tbrugz.queryon.util.WebUtils;

/*
 * see: https://github.com/vsch/flexmark-java
 */
public class MarkdownServlet extends PagesServlet {
	
	private static final long serialVersionUID = 1L;
	
	//public static final String MD_MIMETYPE = "text/markdown";
	public static final String HTML_MIMETYPE = "text/html";
	
	//public static final String PROP_XPEND = "queryon.pages.markdown.xpend";
	public static final String PROP_PREPEND = "queryon.pages.markdown.prepend";
	public static final String PROP_APPEND = "queryon.pages.markdown.append";

	public static final String DEFAULT_INDEX = "index.md";
	
	// Parser & HtmlRenderer are thread-safe once built
	static final Parser PARSER;
	static final HtmlRenderer RENDERER;
	
	static {
		MutableDataSet options = new MutableDataSet();
		options.set(Parser.EXTENSIONS, java.util.Arrays.asList(
				TablesExtension.create(),
				StrikethroughExtension.create(),
				TaskListExtension.create(),
				AnchorLinkExtension.create()
				));
		PARSER = Parser.builder(options).build();
		RENDERER = HtmlRenderer.builder(options).build();
	}
	
	boolean doXpend = true;
	boolean reqXpend = doXpend;
	String prepend = null;
	String append = null;
	
	@Override
	public void init() throws ServletException {
		super.init();
		Properties prop = QOnContextUtils.getProperties(getServletContext());
		//doXpend = Utils.getPropBool(prop, PROP_XPEND, doXpend);
		prepend = prop.getProperty(PROP_PREPEND);
		append = prop.getProperty(PROP_APPEND);
		indexFile = DEFAULT_INDEX;
	}
	
	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
		WebUtils.setResponseUtf8(resp);
		//resp.setHeader(HEADER_PAGE_ID, id);
		Node document = PARSER.parse(body);
		String md = RENDERER.render(document);
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

	@Override
	public String getDefaultUrlMapping() {
		return "/md/*";
	}
	
}
