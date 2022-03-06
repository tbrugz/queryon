package tbrugz.queryon.auth.provider;

import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

public interface IdentityProvider {
	
	public void setInfo(Properties prop);
	
	public boolean isAuthenticated(HttpServletRequest request);
	
	public String getIdentity(HttpServletRequest request);
	
	public String getRealm();
	
}
