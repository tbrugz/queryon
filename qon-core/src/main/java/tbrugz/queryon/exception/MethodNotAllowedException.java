package tbrugz.queryon.exception;

import javax.servlet.http.HttpServletResponse;

import tbrugz.queryon.BadRequestException;

public class MethodNotAllowedException extends BadRequestException {
	private static final long serialVersionUID = 1L;

	public MethodNotAllowedException(String message) {
		super(message, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
	}
}
