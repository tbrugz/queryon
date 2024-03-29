package tbrugz.queryon.auth;

import org.apache.shiro.subject.Subject;

public class UserInfo {
	
	final boolean authenticated;
	final String username;
	//final Set<String> roles;
	
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

	public String toString() {
		return "UserInfo[username="+username+";authenticated="+authenticated+"]";
	}
	
}
