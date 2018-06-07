package tbrugz.queryon.exception;

import javax.servlet.http.HttpServletResponse;

import tbrugz.queryon.BadRequestException;

public class ForbiddenException extends BadRequestException {

	private static final long serialVersionUID = 1L;

	public ForbiddenException(String message) {
		super(message, HttpServletResponse.SC_FORBIDDEN);
	}

}
