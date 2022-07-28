package tbrugz.queryon.shiro;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.filter.authc.AuthenticationFilter;
import org.apache.shiro.web.subject.WebSubject;

import tbrugz.queryon.auth.provider.KeycloakUtils;

public class KeycloakAuthFilter extends AuthenticationFilter {

	static final Log log = LogFactory.getLog(KeycloakAuthFilter.class);
	
	static final String DEFAULT_REALM_NAME = "KeycloakRealm";

	@Override
	protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
		if(!(request instanceof HttpServletRequest)) {
			//log.warn("not instanceof HttpServletRequest: "+request);
			return false;
		}

		Subject currentUser = SecurityUtils.getSubject();
		if(currentUser.getPrincipal()!=null) {
			//log.debug("currentUser = "+currentUser);
			return true;
		}

		HttpServletRequest req = (HttpServletRequest) request;
		//log.debug("request.getPathInfo(): "+req.getPathInfo());
		
		boolean authenticated = KeycloakUtils.isAuthenticated(req);
		String identity = KeycloakUtils.getPreferredUsername(req);
		if(!authenticated) {
			log.debug("not authenticated ; identity = "+identity);
			return false;
		}
		
		log.debug("authenticated: identity = "+identity);
		/*
		Set<String> roles = KeycloakUtils.getRoles(req);
		log.debug("roles = "+roles+" ; identity = "+identity);
		*/

		Session currentSession = currentUser.getSession(); //XXX: false?
		
		PrincipalCollection principals = new SimplePrincipalCollection(identity, DEFAULT_REALM_NAME);
		Subject.Builder sbuilder = new WebSubject.Builder(request, response).principals(principals).authenticated(authenticated);
		if(currentSession!=null) {
			sbuilder = sbuilder.session(currentSession);
		}
		Subject subject = sbuilder.buildSubject();
		ThreadContext.bind(subject);
		
		return true;
	}
	
	@Override
	protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
		return false;
	}

}
