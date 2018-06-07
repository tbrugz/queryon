package tbrugz.queryon.exception;

import javax.servlet.http.HttpServletResponse;

import tbrugz.queryon.BadRequestException;

public class InternalServerException extends BadRequestException {

	private static final long serialVersionUID = 1L;

	public InternalServerException(String message) {
		super(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	}

	public InternalServerException(String message, Throwable t) {
		super(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t);
	}
}
