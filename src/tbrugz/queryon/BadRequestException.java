package tbrugz.queryon;

public class BadRequestException extends RuntimeException {

	final int code;
	
	public BadRequestException(String message) {
		super(message);
		code = 400;
	}
	
	public int getCode() {
		return code;
	}
}
