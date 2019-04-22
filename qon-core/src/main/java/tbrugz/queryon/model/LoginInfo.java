package tbrugz.queryon.model;

public class LoginInfo {

	final String username;
	final String password;
	
	public LoginInfo(String username, String password) {
		this.username = username;
		this.password = password;
	}

	public String getUsername() {
		return username;
	}
	
	public String getPassword() {
		return password;
	}
	
}
