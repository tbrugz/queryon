package tbrugz.queryon;

import javax.servlet.http.HttpServletResponse;

public class NotFoundException extends BadRequestException {
	private static final long serialVersionUID = 1L;

	public NotFoundException(String message) {
		super(message, HttpServletResponse.SC_NOT_FOUND);
	}
}
