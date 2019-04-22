package tbrugz.queryon.model;

import org.apache.shiro.subject.Subject;

public class UserInfo {
	
	boolean authenticated;
	String username;
	//Set<String> roles;
	
	public UserInfo(Subject subject) {
		authenticated = subject.isAuthenticated();
		Object principal = subject.getPrincipal();
		username = principal!=null ? String.valueOf(principal) : null;
	}

	public boolean isAuthenticated() {
		return authenticated;
	}
	
	public String getUsername() {
		return username;
	}
	
}
