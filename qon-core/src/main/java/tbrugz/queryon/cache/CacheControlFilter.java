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

import tbrugz.queryon.util.MiscUtils;

public class CacheControlFilter implements Filter {

	private static final Log log = LogFactory.getLog(CacheControlFilter.class);
	
	public static final String CACHE_MAX_AGE = "cache-max-age";
	
	boolean privateCache = false;
	boolean addLastModifiedNow = false;
	boolean addNowAsETag = true;
	int maxAge = 3600;
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		log.info("initializing CacheControlFilter");
		String cacheMaxAge = filterConfig.getInitParameter(CACHE_MAX_AGE);
		if(cacheMaxAge!=null) {
			if(MiscUtils.isInt(cacheMaxAge)) {
				maxAge = Integer.parseInt(cacheMaxAge);
				log.info(CACHE_MAX_AGE+" = "+maxAge);
			}
			else {
				log.warn("patemeter '"+CACHE_MAX_AGE+"' not an integer: "+cacheMaxAge);
			}
		}
	}

	@Override
	public void destroy() {
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if(request instanceof HttpServletRequest) {
			HttpServletRequest req = (HttpServletRequest) request;
			String method = req.getMethod();
			if("GET".equals(method) || "HEAD".equals(method)) {
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
		}
		chain.doFilter(request, response);
	}

}
