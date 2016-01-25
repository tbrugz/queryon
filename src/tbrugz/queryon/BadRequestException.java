package tbrugz.queryon;

import javax.servlet.http.HttpServletResponse;

public class BadRequestException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	final int code;
	final String internalMessage;
	
	public BadRequestException(String message) {
		this(message, HttpServletResponse.SC_BAD_REQUEST); //400
	}

	public BadRequestException(String message, int code) {
		super(message);
		this.code = code;
		this.internalMessage = null;
	}

	public BadRequestException(String message, Throwable t) {
		this(message,  HttpServletResponse.SC_BAD_REQUEST, t);
	}

	public BadRequestException(String message, String internalMessage, Throwable t) {
		this(message, HttpServletResponse.SC_BAD_REQUEST, t);
	}
	
	public BadRequestException(String message, int code, Throwable t) {
		this(message, null, HttpServletResponse.SC_BAD_REQUEST, t);
	}

	public BadRequestException(String message, String internalMessage, int code, Throwable t) {
		super(message, t);
		this.code = code;
		this.internalMessage = internalMessage;
	}
	
	public int getCode() {
		return code;
	}
}
