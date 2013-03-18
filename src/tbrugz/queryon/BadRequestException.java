package tbrugz.queryon;

import javax.servlet.http.HttpServletResponse;

public class BadRequestException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
	final int code;
	
	public BadRequestException(String message) {
		this(message, HttpServletResponse.SC_BAD_REQUEST); //400
	}

	public BadRequestException(String message, int code) {
		super(message);
		this.code = code;
	}
	
	public int getCode() {
		return code;
	}
}
