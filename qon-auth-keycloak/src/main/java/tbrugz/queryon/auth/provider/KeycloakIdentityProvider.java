package tbrugz.queryon.auth.provider;

import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class KeycloakIdentityProvider implements IdentityProvider {

	static final Log log = LogFactory.getLog(KeycloakIdentityProvider.class);
	
	@Override
	public void setInfo(Properties prop) {
	}
	
	@Override
	public boolean isAuthenticated(HttpServletRequest request) {
		return KeycloakUtils.isAuthenticated(request);
	}
	
	@Override
	public String getIdentity(HttpServletRequest request) {
		return KeycloakUtils.getPreferredUsername(request);
	}
	
	@Override
	public String getRealm() {
		return "KeycloakRealm";
	}

}
