package tbrugz.queryon;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

/*
 * see: https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS
 */
public class CorsFilter implements Filter {

	@Override
	public void init(FilterConfig arg0) throws ServletException {
	}
	
	@Override
	public void destroy() {
	}
	
	@Override
	public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
		if(resp instanceof HttpServletResponse) {
			HttpServletResponse hresp = (HttpServletResponse) resp;
			hresp.addHeader("Access-Control-Allow-Origin", "*");
			hresp.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS, HEAD");
			hresp.addHeader("Access-Control-Max-Age", "1728000"); //in seconds ; 1728000 = 20 days
		}
		chain.doFilter(req, resp);
	}

}
