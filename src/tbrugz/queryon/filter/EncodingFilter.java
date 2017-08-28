package tbrugz.queryon.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/*
 * see: https://stackoverflow.com/questions/23312950/how-to-set-character-encoding-and-content-type-globally-in-a-java-web-app
 */
public class EncodingFilter implements Filter {

	public static final String UTF8 = "UTF-8";
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	@Override
	public void destroy() {
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		request.setCharacterEncoding(UTF8);
		response.setCharacterEncoding(UTF8);
		chain.doFilter(request, response);
	}

}
