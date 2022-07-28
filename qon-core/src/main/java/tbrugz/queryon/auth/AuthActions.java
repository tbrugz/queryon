package tbrugz.queryon.auth;

import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.queryon.util.ShiroUtils;

public class AuthActions {
	
	private static final Log log = LogFactory.getLog(AuthActions.class);

	final Properties prop;
	
	public AuthActions(Properties prop) {
		this.prop = prop;
	}
	
	public UserInfo getCurrentUser(HttpServletRequest req) {
		UserInfo ui = new UserInfo(ShiroUtils.getSubject(prop, req));
		return ui;
	}

	public ExtendedUserInfo getCurrentUserXtra(HttpServletRequest req) {
		Subject subject = ShiroUtils.getSubject(prop, req);
		/*
		log.info("subject: "+subject+
			" ; principals: "+subject.getPrincipals()+"/"+subject.getPrincipals().getClass().getName()+
			" ; realmNames: "+subject.getPrincipals().getRealmNames());
		*/
		ExtendedUserInfo ui = new ExtendedUserInfo(
				subject,
				SchemaModelUtils.getModelIds(req.getServletContext())
				);
		return ui;
	}
	
	public UserInfo doLogin(Map<String, String> parameterMap) {
		Subject currentUser = SecurityUtils.getSubject();
		String username = parameterMap.get("username");
		String password = parameterMap.get("password");
		
		ShiroUtils.authenticate(currentUser, username, password);

		UserInfo ui = new UserInfo(currentUser);
		return ui;
	}
	
	public UserInfo doLogout() {
		Subject currentUser = SecurityUtils.getSubject();

		currentUser.logout();

		UserInfo ui = new UserInfo(currentUser);
		return ui;
	}
	
}
