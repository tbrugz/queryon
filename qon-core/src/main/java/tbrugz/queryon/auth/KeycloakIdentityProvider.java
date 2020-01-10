package tbrugz.queryon.auth;

import java.security.Principal;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.keycloak.KeycloakPrincipal;

public class KeycloakIdentityProvider implements IdentityProvider {

	static final Log log = LogFactory.getLog(KeycloakIdentityProvider.class);
	
	HttpServletRequest request;
	
	@Override
	public void setRequest(HttpServletRequest request) {
		this.request = request;
	}
	
	@Override
	public boolean isAuthenticated() {
		Principal principal = request.getUserPrincipal();
		if(principal!=null) {
			if(principal instanceof KeycloakPrincipal) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public String getIdentity() {
		Principal principal = request.getUserPrincipal();
		if(principal!=null) {
			//log.debug("getUserPrincipal: getName() = "+principal.getName()+" / principal = "+principal+" / class = "+principal.getClass().getName());
			if(principal instanceof KeycloakPrincipal) {
				// https://stackoverflow.com/questions/31864062/fetch-logged-in-username-in-a-webapp-secured-with-keycloak
				KeycloakPrincipal<?> kp = (KeycloakPrincipal<?>) principal;
				return kp.getKeycloakSecurityContext().getIdToken().getPreferredUsername();
			}
			/*else {
				return principal.getName();
			}*/
		}
		return null;
	}

}
