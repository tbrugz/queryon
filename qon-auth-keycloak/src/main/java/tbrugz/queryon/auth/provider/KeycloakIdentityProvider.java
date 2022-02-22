package tbrugz.queryon.auth.provider;

import java.security.Principal;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.IDToken;

public class KeycloakIdentityProvider implements IdentityProvider {

	static final Log log = LogFactory.getLog(KeycloakIdentityProvider.class);
	
	HttpServletRequest request;
	
	@Override
	public void setInfo(HttpServletRequest request, Properties prop) {
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
				//log.debug("kp.getName(): "+kp.getName());
				KeycloakSecurityContext securityContext = kp.getKeycloakSecurityContext();
				//log.debug("sc.realm: "+securityContext.getRealm());
				/*
				AuthorizationContext authContext = securityContext.getAuthorizationContext();
				if(authContext!=null) {
					log.debug("authContext.getPermissions(): "+authContext.getPermissions());
				}
				else {
					log.debug("null authContext");
				}
				*/
				
				AccessToken acesstoken = securityContext.getToken();
				if(acesstoken==null) {
					log.debug("getIdentity: null AccessToken");
					IDToken idtoken = securityContext.getIdToken();
					if(idtoken==null) {
						log.warn("getIdentity: null IDToken and AccessToken");
						return null;
					}
					else {
						//log.info("idtoken.getPreferredUsername: "+idtoken.getPreferredUsername());
						return idtoken.getPreferredUsername();
					}
				}

				/*
				{
					log.info("acesstoken.getPreferredUsername: "+acesstoken.getPreferredUsername());
					Access access = acesstoken.getRealmAccess();
					if(access!=null) {
						//log.info("acesstoken.getRealmAccess: "+access);
						log.info("acesstoken.getRealmAccess.getRoles: "+access.getRoles());
					}
					else {
						log.debug("null acesstoken.getRealmAccess");
					}
					IDToken idtoken = securityContext.getIdToken();
					log.info("idtoken.getPreferredUsername[2]: "+idtoken.getPreferredUsername());
				}
				*/

				return acesstoken.getPreferredUsername();
			}
			/*else {
				return principal.getName();
			}*/
		}
		return null;
	}
	
	@Override
	public String getRealm() {
		return "KeycloakRealm";
	}

}
