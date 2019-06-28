package tbrugz.queryon.model;

import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.util.ShiroUtils;

public class BeanActions {
	
	final Properties prop;
	
	public BeanActions(Properties prop) {
		this.prop = prop;
	}
	
	public UserInfo getCurrentUser(HttpServletRequest req) {
		UserInfo ui = new UserInfo(ShiroUtils.getSubject(prop, req));
		return ui;
	}
	
	public UserInfo doLogin(RequestSpec reqspec) {
		Subject currentUser = SecurityUtils.getSubject();
		String username = reqspec.getParameterMapUniqueValues().get("username");
		String password = reqspec.getParameterMapUniqueValues().get("password");
		
		ShiroUtils.authenticate(currentUser, username, password);

		UserInfo ui = new UserInfo(currentUser);
		return ui;
	}
	
	public UserInfo doLogout(RequestSpec reqspec) {
		Subject currentUser = SecurityUtils.getSubject();

		currentUser.logout();

		UserInfo ui = new UserInfo(currentUser);
		return ui;
	}
	
}
