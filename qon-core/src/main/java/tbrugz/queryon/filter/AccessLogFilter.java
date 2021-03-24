package tbrugz.queryon.filter;

import java.io.IOException;
import java.net.InetAddress;
import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.QOnContextUtils;
import tbrugz.sqldump.util.ConnectionUtil;

/*
 * Access Logger: logs access (http requests) to database table
 *
 * see:
 * https://en.wikipedia.org/wiki/Common_Gateway_Interface
 * https://httpd.apache.org/docs/current/mod/mod_log_config.html
 * https://javaee.github.io/javaee-spec/javadocs/javax/servlet/http/HttpServletRequest.html
 */
public class AccessLogFilter implements Filter {

	static final Log log = LogFactory.getLog(AccessLogFilter.class);
	
	// statuscode, url, username, timestamp
	// headers(xx)
	
	public static final String SERVER_NAME = "server_name";
	public static final String REQUEST_METHOD = "request_method";
	public static final String REQUEST_URI = "request_uri";
	public static final String QUERY_STRING = "query_string";
	public static final String HEADER_REFERER = "header_referer";
	public static final String HEADER_USERAGENT = "header_useragent";
	// XXX ? PROTOCOL / SERVER_PROTOCOL (http/1.1, ...) - https://javaee.github.io/javaee-spec/javadocs/javax/servlet/ServletRequest.html#getProtocol
	// request: getScheme()? getServerPort()?
	// request: getContentLength()? getContentType()?
	public static final String USERNAME = "username";
	public static final String REMOTE_ADDR = "remote_addr";
	
	public static final String STATUS_CODE = "status_code";
	public static final String TIMESTAMP_INI = "timestamp_ini";
	public static final String ELAPSED_MS = "elapsed_ms";

	static final String DEFAUL_TABLE_NAME = "QON_ACCESS_LOG";
	
	String modelId = null;
	String tableName = DEFAUL_TABLE_NAME;
	String serverHostName = null;

	final boolean grabQueryString = true,
		grabServerName = true,
		grabHeaderReferer = false,
		grabHeaderUserAgent = false
		;
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// setup tableName & modelId
		log.info("initializing AccessLogFilter"); // [name = "+filterConfig.getFilterName()+"]");
		// does not work: filters are initiated before servlets
		//modelId = SchemaModelUtils.getDefaultModelId(filterConfig.getServletContext());
		modelId = filterConfig.getInitParameter("modelId");
		String tmpTableName = filterConfig.getInitParameter("tableName");
		if(tmpTableName!=null) {
			tableName = tmpTableName;
		}
		if(grabServerName) {
			try {
				serverHostName = InetAddress.getLocalHost().getHostName().toLowerCase();
			}
			catch(java.net.UnknownHostException e) {
				throw new ServletException(e);
			}
		}
		log.info("modelId="+modelId+" ; tableName="+tableName);
	}
	
	@Override
	public void destroy() {
	}
	
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse resp = (HttpServletResponse) response;
		
		long tsIni = System.currentTimeMillis();
		Exception ex = null;
		
		try {
			chain.doFilter(req, resp);
		}
		catch (IOException e) {
			ex = e;
		}
		catch (ServletException e) {
			ex = e;
		}
		
		long tsEnd = System.currentTimeMillis();
		long elapsed = tsEnd - tsIni;

		// request fields
		//String scheme = req.getScheme(); // http/https/ftp...
		String method = req.getMethod();
		String url = req.getRequestURI();
		String queryString = grabQueryString ? req.getQueryString() : null;
		Principal userPrincipal = req.getUserPrincipal();
		String username = req.getRemoteUser();
		String remoteAddr = req.getRemoteAddr();
		//String authType = req.getAuthType();
		// XXX req.getHeader("")
		//req.getProtocol(); // protocol/version

		// response fields
		int status = resp.getStatus();
		// XXX resp.getHeader("")
		
		// make map
		Map<String, Object> attr = new HashMap<String, Object>();
		if(grabServerName) {
			String serverName = req.getServerName();
			if(serverName!=null) {
				attr.put(SERVER_NAME, serverName);
			}
			else {
				attr.put(SERVER_NAME, serverHostName);
			}
		}
		attr.put(REQUEST_METHOD, method);
		attr.put(REQUEST_URI, url);
		if(grabQueryString) {
			attr.put(QUERY_STRING, queryString);
		}
		if(userPrincipal!=null) {
			attr.put(USERNAME, userPrincipal.getName());
		}
		else if(username!=null) {
			attr.put(USERNAME, username);
		}
		attr.put(REMOTE_ADDR, remoteAddr);
		if(grabHeaderReferer) {
			attr.put(HEADER_REFERER, req.getHeader("Referer"));
		}
		if(grabHeaderUserAgent) {
			attr.put(HEADER_USERAGENT, req.getHeader("User-Agent"));
		}
		attr.put(STATUS_CODE, status);
		attr.put(TIMESTAMP_INI, new Date(tsIni));
		attr.put(ELAPSED_MS, elapsed);
		
		// log request
		logAcess(attr, req.getServletContext());
		
		// throw execption
		if(ex!=null) {
			if(ex instanceof IOException) {
				throw (IOException) ex;
			}
			throw (ServletException) ex;
		}

	}
	
	void logAcess(Map<String, Object> propMap, ServletContext context) {
		RunnableLogger rl = new RunnableLogger(propMap, context);
		new Thread(rl).start();
	}

	class RunnableLogger implements Runnable {
		final Map<String, Object> propMap;
		final ServletContext context;
		
		public RunnableLogger(Map<String, Object> propMap, ServletContext context) {
			this.propMap = propMap;
			this.context = context;
		}

		@Override
		public void run() {
			Connection conn = null;
			try {
				Properties prop = QOnContextUtils.getProperties(context);
				conn = DBUtil.initDBConn(prop, modelId);
				String insert = createInsert();
				PreparedStatement st = conn.prepareStatement(insert);
				Iterator<String> keys = propMap.keySet().iterator();
				for(int i=1;i<=propMap.size();i++) {
					st.setObject(i, propMap.get(keys.next()));
				}
				int count = st.executeUpdate();
				if(count==1) {
					conn.commit();
					//log.debug("Access logged: map = "+propMap);
				}
				else {
					log.warn("Error logging access [count = "+count+"]");
				}
			} catch (ClassNotFoundException e) {
				log.warn("Exception: "+e);
			} catch (SQLException e) {
				log.warn("Exception [modelId = "+modelId+"]: "+e);
			} catch (NamingException e) {
				log.warn("Exception [modelId = "+modelId+"]: "+e);
			} catch (RuntimeException e) {
				log.warn("Exception [modelId = "+modelId+"]: "+e);
			} finally {
				ConnectionUtil.closeConnection(conn);
			}
		}
		
		String createInsert() {
			StringBuilder sb = new StringBuilder();
			sb.append("insert into "+tableName+" (");
			Iterator<String> keys = propMap.keySet().iterator();
			int i=0;
			for(;keys.hasNext();i++) {
				if(i>0) { sb.append(", "); }
				sb.append(keys.next());
			}
			sb.append(") values (");
			for(int j=0;j<i;j++) {
				if(j>0) { sb.append(", "); }
				sb.append("?");
			}
			sb.append(")");
			return sb.toString();
		}
	}

}


