package tbrugz.queryon.auth.provider;

import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

public interface IdentityProvider {
	
	public void setInfo(HttpServletRequest request, Properties prop);
	
	public boolean isAuthenticated();
	
	public String getIdentity();
	
	public String getRealm();
	
}
