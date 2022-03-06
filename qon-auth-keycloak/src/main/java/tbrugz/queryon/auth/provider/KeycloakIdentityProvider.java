package tbrugz.queryon.auth.provider;

import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class KeycloakIdentityProvider implements IdentityProvider {

	static final Log log = LogFactory.getLog(KeycloakIdentityProvider.class);
	
	HttpServletRequest request;
	
	@Override
	public void setInfo(HttpServletRequest request, Properties prop) {
		this.request = request;
	}
	
	@Override
	public boolean isAuthenticated() {
		return KeycloakUtils.isAuthenticated(request);
	}
	
	@Override
	public String getIdentity() {
		return KeycloakUtils.getPreferredUsername(request);
	}
	
	@Override
	public String getRealm() {
		return "KeycloakRealm";
	}

}
