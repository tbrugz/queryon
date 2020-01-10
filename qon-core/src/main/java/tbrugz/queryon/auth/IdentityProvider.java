package tbrugz.queryon.auth;

import javax.servlet.http.HttpServletRequest;

public interface IdentityProvider {
	
	public void setRequest(HttpServletRequest request);
	
	public boolean isAuthenticated();
	
	public String getIdentity();
	
}
