package tbrugz.queryon.shiro;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.subject.WebSubject;

import tbrugz.queryon.auth.provider.KeycloakUtils;

public class KeycloakShiroRealm extends AuthorizingRealm {

	static final Log log = LogFactory.getLog(KeycloakShiroRealm.class);

	@Override
	protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
		Subject currentUser = SecurityUtils.getSubject();
		//log.debug("currentUser = "+currentUser);
		if(currentUser instanceof WebSubject) {
			WebSubject ws = (WebSubject) currentUser;
			HttpServletRequest req = (HttpServletRequest) ws.getServletRequest();

			Set<String> roles = KeycloakUtils.getRoles(req);
			if(roles==null && req instanceof HttpServletRequestWrapper) {
				log.debug("roles are null, unwrapping "+req);
				HttpServletRequest req2 = unwrapHttpServletRequest((HttpServletRequestWrapper) req);
				if(req2!=null) {
					boolean authenticated = KeycloakUtils.isAuthenticated(req2);
					if(!authenticated) {
						log.warn("not authenticated (Keycloak)...");
					}
					roles = KeycloakUtils.getRoles(req2);
				}
			}
			if(roles==null) {
				log.warn("roles are null");
				return null;
			}
			SimpleAuthorizationInfo info = new SimpleAuthorizationInfo(roles);
			//info.setStringPermissions(null);
			return info;
		}
		
		log.warn("not a WebSubject: " + currentUser);
		return null;
	}

	/*
	@Override
	public boolean supports(AuthenticationToken token) {
		log.debug("supports [false]: " + token);
		return false;
	}
	*/

	@Override
	protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
		//log.debug("doGetAuthenticationInfo: " + token);
		return null;
	}

	@Override
	public AuthorizationInfo getAuthorizationInfo(PrincipalCollection principals) {
		return super.getAuthorizationInfo(principals);
	}
	
	protected HttpServletRequest unwrapHttpServletRequest(HttpServletRequestWrapper req) {
		try {
			Method m = HttpServletRequestWrapper.class.getDeclaredMethod("_getHttpServletRequest");
			m.setAccessible(true);
			return (HttpServletRequest) m.invoke(req);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			log.warn("Error unwrapping: " + e);
			return null;
		}
	}

}
