package tbrugz.queryon;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.realm.Realm;
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
		if(! isPermitted(subject, permission, object)) {
			log.warn("no permission '"+permission+"' for subject '"+subject+" ; "+subject.getPrincipal()+"'");
			throw new BadRequestException(permission+": authorization required", HttpServletResponse.SC_FORBIDDEN);
		}
	}

	public static boolean isPermitted(Subject subject, String permission) {
		return isPermitted(subject, permission, null);
	}
	
	public static boolean isPermitted(Subject subject, String permission, String object) {
		if(object!=null) {
			object = object.replaceAll("\\.", ":");
			permission += ":"+object;
		}
		//log.info("checking permission '"+permission+"', subject = "+subject);
		return subject.isPermitted(permission);
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
		throw new BadRequestException("no authorization for any of "+permissionsStr, HttpServletResponse.SC_FORBIDDEN);
	}
	
	public static Set<String> getCurrentUserRoles() {
		Set<String> roles = new HashSet<String>();
		org.apache.shiro.mgt.SecurityManager sm = SecurityUtils.getSecurityManager();
		
		if(sm instanceof RealmSecurityManager) {
			RealmSecurityManager rsm = (RealmSecurityManager) sm;
			Collection<Realm> rs = rsm.getRealms();
			if(rs!=null) {
				//out.write("<br>#realms = "+rs.size());
				for(Realm r: rs) {
					if(r instanceof AuthorizingRealm) {
						//out.write("<br><b>authorizing realm:: "+r.getName()+" / "+r.getClass().getSimpleName()+"</b> ");
						AuthorizingRealm ar = (AuthorizingRealm) r;
						//ar.doGetAuthorizationInfo(currentUser.getPrincipals());
						//ar.getAuthorizationInfo(pc);
						
						Cache<Object, AuthorizationInfo> cache = ar.getAuthorizationCache();
						if(cache!=null) {
							Collection<AuthorizationInfo> ais = cache.values();
							if(ais!=null) {
								for(AuthorizationInfo ai: ais) {
									//out.write("<ul><li>roles:: "+ai.getRoles()+"</li>");
									Collection<String> cr = ai.getRoles();
									if(cr!=null) {
										roles.addAll(cr);
									}
								}
							}
						}
					}
				}
			}
		}
		return roles;
	}
	
}
