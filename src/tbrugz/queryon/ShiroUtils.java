package tbrugz.queryon;

import java.util.Arrays;
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;

public class ShiroUtils {

	static final Log log = LogFactory.getLog(ShiroUtils.class);
	
	public static Subject getSubject(Properties prop) {
		Subject currentUser = SecurityUtils.getSubject();
		if(currentUser.getPrincipal()==null) {
			//TODOne: get static info from properties...
			Object userIdentity = prop.getProperty(QueryOn.PROP_AUTH_ANONUSER);
			String realmName = prop.getProperty(QueryOn.PROP_AUTH_ANONREALM);
			PrincipalCollection principals = new SimplePrincipalCollection(userIdentity, realmName);
			currentUser = new Subject.Builder().principals(principals).buildSubject();
		}
		return currentUser;
	}
	
	public static void checkPermission(Subject subject, String permission, String object) {
		if(object!=null) {
			object = object.replaceAll("\\.", ":");
			permission += ":"+object;
		}
		//log.info("checking permission '"+permission+"', subject = "+subject);
		if(! subject.isPermitted(permission)) {
			log.warn("no permission '"+permission+"' for subject '"+subject+" ; "+subject.getPrincipal()+"'");
			throw new BadRequestException(permission+": authorization required", HttpServletResponse.SC_FORBIDDEN);
		}
		/*else {
			log.info("checked permission '"+permission+"' OK, subject = "+subject+" ; "+subject.getPrincipal());
		}*/
	}

	public static void checkPermissionAny(Subject subject, String[] permissionList) {
		for(String permission: permissionList) {
			if(subject.isPermitted(permission)) {
				return;
			}
		}
		String permissionsStr = Arrays.asList(permissionList).toString();
		log.warn("no permission '"+permissionsStr+"' for subject '"+subject+" ; "+subject.getPrincipal()+"'");
		throw new BadRequestException(permissionsStr+": authorization required", HttpServletResponse.SC_FORBIDDEN);
	}
	
}
