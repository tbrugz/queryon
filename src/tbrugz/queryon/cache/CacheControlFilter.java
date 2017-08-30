package tbrugz.queryon.cache;

import java.io.IOException;
import java.util.Date;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CacheControlFilter implements Filter {

	private static final Log log = LogFactory.getLog(CacheControlFilter.class);
	
	public static final String CACHE_MAX_AGE = "cache-max-age";
	
	boolean privateCache = false;
	boolean addLastModifiedNow = false;
	boolean addNowAsETag = true;
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void destroy() {
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if(request instanceof HttpServletRequest) {
			HttpServletRequest req = (HttpServletRequest) request;
			if("GET".equals(req.getMethod())) {
				String maxAge = req.getParameter(CACHE_MAX_AGE);
				if(maxAge!=null) {
					if(isInt(maxAge)) {
						HttpServletResponse resp = (HttpServletResponse) response;
						String value = (privateCache?"private, ":"") + "max-age="+maxAge;
						resp.setHeader("Cache-Control", value);
						
						if(addLastModifiedNow) {
							Date now = new Date();
							resp.setDateHeader("Last-Modified", now.getTime());
						}
						
						if(addNowAsETag) {
							Date now = new Date();
							resp.setHeader("ETag", "\""+String.valueOf(now.getTime())+"\"");
						}
						
						log.debug("header: Cache-Control: "+value);
					}
					else {
						log.warn("patemeter '"+CACHE_MAX_AGE+"' not an integer: "+maxAge);
					}
				}
			}
		}
		chain.doFilter(request, response);
	}
	
	boolean isInt(String s) {
		try {
			Integer.parseInt(s);
			return true;
		}
		catch(NumberFormatException e) {
			return false;
		}
	}

}
