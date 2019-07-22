package tbrugz.queryon.auth;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.naming.AuthenticationException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.ShiroException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import tbrugz.queryon.AbstractHttpServlet;
import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.ResponseSpec;

public class AuthServlet extends AbstractHttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Log log = LogFactory.getLog(AuthServlet.class);
	
	public static final String ACTION_CURRENT_USER = "currentUser";
	public static final String ACTION_LOGIN = "login";
	public static final String ACTION_LOGOUT = "logout";
	
	boolean currentUserWithExtraInfo = true;
	
	Properties prop;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		prop = (Properties) config.getServletContext().getAttribute(QueryOn.ATTR_PROP);
	}
	
	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		String pathInfo = req.getPathInfo();
		if(pathInfo==null || pathInfo.length()<1) { throw new BadRequestException("URL (path-info) must not be null or empty"); }
		
		pathInfo = pathInfo.substring(1);
		//log.info("pathInfo = "+pathInfo+" ; req.getPathInfo() = "+req.getPathInfo()+" ; req.getQueryString() = "+req.getQueryString());
		log.info("pathInfo = "+pathInfo);

		AuthActions beanActions = new AuthActions(prop);
		if(pathInfo.equals(ACTION_CURRENT_USER)) {
			UserInfo ui = null;
			if(currentUserWithExtraInfo) {
				ui = beanActions.getCurrentUserXtra(req);
			}
			else {
				ui = beanActions.getCurrentUser(req);
			}
			writeBean(ui, resp);
		}
		else if(pathInfo.equals(ACTION_LOGIN)) {
			checkHttpMethod(req, QueryOn.METHOD_POST);
			try {
				UserInfo ui = beanActions.doLogin(getParameterMap(req));
				writeBean(ui, resp);
			}
			catch(Exception e) { // ShiroException | AuthenticationException
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().write(getExceptionMessage(e));
			}
		}
		else if(pathInfo.equals(ACTION_LOGOUT)) {
			beanActions.doLogout();
		}
		else {
			throw new BadRequestException("unknown action: "+pathInfo);
		}
	}
	
	void checkHttpMethod(HttpServletRequest req, String method) {
		if(!req.getMethod().equalsIgnoreCase(method)) {
			throw new BadRequestException("method not allowed: "+req.getMethod());
		}
	}
	
	static boolean isContentTypeJson(String contentType) {
		return contentType!=null && contentType.startsWith(ResponseSpec.MIME_TYPE_JSON);
	}
	
	static String getExceptionMessage(Exception e) {
		if(e instanceof UnknownAccountException) {
			return "Unknown account";
		}
		else if(e instanceof IncorrectCredentialsException) {
			return "Incorrect password";
		}
		else if(e instanceof ShiroException) {
			return "Shiro Exception: "+e;
		}
		else if(e instanceof AuthenticationException) {
			return "Authentication Exception: "+e;
		}
		return "Unexpected Exception: "+e;
	}
	
	Map<String, String> getParameterMap(HttpServletRequest req) throws JsonSyntaxException, JsonIOException, IOException {
		Map<String, String> ret = new HashMap<String, String>();
		Enumeration<String> en = req.getParameterNames();
		while(en.hasMoreElements()) {
			String key = en.nextElement();
			ret.put(key, req.getParameter(key));
		}
		// if content-type is json, process body as json
		if(isContentTypeJson(req.getContentType())) {
			Gson gson = new Gson();
			@SuppressWarnings("unchecked")
			Map<String, Object> map = gson.fromJson(req.getReader(), Map.class);
			if(map!=null) {
				for(Map.Entry<String, Object> e: map.entrySet()) {
					ret.put(e.getKey(), String.valueOf(e.getValue()) );
				}
			}
		}
		
		return ret;
	}
	
	void writeBean(Object bean, HttpServletResponse resp) throws IOException {
		resp.setContentType(ResponseSpec.MIME_TYPE_JSON);
		Gson gson = new Gson();
		resp.getWriter().write(gson.toJson(bean));
	}

}
