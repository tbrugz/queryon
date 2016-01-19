package tbrugz.queryon;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.exception.ForbiddenException;
import tbrugz.queryon.shiro.AuthorizationInfoInformer;

public class ShiroUtils {

	static final Log log = LogFactory.getLog(ShiroUtils.class);
	
	// user for (unit) test (for now, at least)
	static Map<Object, Set<String>> userRoles = new HashMap<Object, Set<String>>();
	
	public static Subject getSubject(Properties prop) {
		Subject currentUser = SecurityUtils.getSubject();
		if(currentUser.getPrincipal()==null) {
			//TODOne: get static info from properties...
			Object userIdentity = prop.getProperty(QueryOn.PROP_AUTH_ANONUSER, QueryOn.DEFAULT_AUTH_ANONUSER);
			String realmName = prop.getProperty(QueryOn.PROP_AUTH_ANONREALM, QueryOn.DEFAULT_AUTH_ANONREALM);
			PrincipalCollection principals = new SimplePrincipalCollection(userIdentity, realmName);
			currentUser = new Subject.Builder().principals(principals).buildSubject();
		}
		return currentUser;
	}
	
	public static void checkPermission(Subject subject, String permission, String object) {
		if(! isPermitted(subject, permission, object)) {
			if(object!=null) {
				object = object.replaceAll("\\.", ":");
			}
			log.warn("no permission '"+permission+"' for subject '"+subject.getPrincipal()+"' on object '"+object+"'"); // ; "+subject.getPrincipal()+"'");
			throw new ForbiddenException("["+permission+(object!=null?":"+object:"")+"]: authorization required");
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
		boolean permitted =  subject.isPermitted(permission);
		//log.info("checking permission '"+permission+"', subject = "+subject.getPrincipal()+" :: "+permitted);
		return permitted;
	}

	public static void checkPermissionAny(Subject subject, String[] permissionList) {
		for(String permission: permissionList) {
			if(subject.isPermitted(permission)) {
				return;
			}
		}
		String permissionsStr = Arrays.asList(permissionList).toString();
		log.warn("no permission '"+permissionsStr+"' for subject '"+subject+" ; "+subject.getPrincipal()+"'");
		throw new ForbiddenException("no authorization for any of "+permissionsStr);
	}
	
	public static Set<String> getSubjectRoles(Subject subject) {
		Set<String> roles = new HashSet<String>();
		Object principal = subject.getPrincipal();
		if(principal==null) {
			return roles;
		}
		
		Set<String> currentUserRoles = userRoles.get(principal);
		if(currentUserRoles!=null) {
			return currentUserRoles;
		}
		
		org.apache.shiro.mgt.SecurityManager sm = SecurityUtils.getSecurityManager();
		
		if(sm instanceof RealmSecurityManager) {
			RealmSecurityManager rsm = (RealmSecurityManager) sm;
			Collection<Realm> rs = rsm.getRealms();
			if(rs!=null) {
				log.debug("#realms = "+rs.size());
				for(Realm r: rs) {
					if(r instanceof AuthorizationInfoInformer) {
						AuthorizationInfoInformer ar = (AuthorizationInfoInformer) r;
						AuthorizationInfo ai = ar.getAuthorizationInfo(subject.getPrincipals()); 
						Collection<String> cr = ai.getRoles();
						if(cr!=null) {
							log.debug("roles:: "+cr);
							roles.addAll(cr);
						}
					}
				}
			}
		}
		return roles;
	}
	
	/* use carefully */
	public static void setUserRoles(Object principal, Set<String> roles) {
		userRoles.put(principal, roles);
	}

	public static void resetUserRoles(Object principal) {
		userRoles.remove(principal);
	}
	
}
