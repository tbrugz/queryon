package tbrugz.queryon.exception;

import javax.servlet.http.HttpServletResponse;

import tbrugz.queryon.BadRequestException;

public class ForbiddenException extends BadRequestException {

	private static final long serialVersionUID = 1L;
	
	final boolean authenticated;

	//@Deprecated // ?
	public ForbiddenException(String message) {
		super(message, HttpServletResponse.SC_FORBIDDEN);
		this.authenticated = true; // null?
	}
	
	public ForbiddenException(String message, boolean authenticated) {
		super(message, HttpServletResponse.SC_FORBIDDEN);
		//XXX 403 Forbidden or 401 Unauthorized ?
		//super(message, authenticated ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED);
		this.authenticated = authenticated;
	}

	public boolean isAuthenticated() {
		return authenticated;
	}

}
