package tbrugz.queryon.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
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
	
	static final String PROP_AUTH_HTTPREALM = "queryon.auth.http-realm";

	static final String PROP_AUTH_USERNAME_PATTERN = "queryon.auth.http-username-pattern";
	
	static final String PROP_AUTH_ANONUSER = "queryon.auth.anon-username";
	static final String PROP_AUTH_ANONREALM = "queryon.auth.anon-realm";
	
	static final String DEFAULT_AUTH_HTTPREALM = "httpRealm";
	static final String DEFAULT_AUTH_ANONUSER = "anonymous";
	static final String DEFAULT_AUTH_ANONREALM = "anonRealm";
	
	// user for (unit) test (for now, at least)
	static Map<Object, Set<String>> userRoles = new HashMap<Object, Set<String>>();
	
	static Subject getSubject(Properties prop) {
		return getSubject(prop, null);
	}
	
	public static Subject getSubject(Properties prop, HttpServletRequest request) {
		Subject currentUser = null;
		try {
			currentUser = SecurityUtils.getSubject();
		}
		catch(UnavailableSecurityManagerException e) {
			return null;
		}
		if(currentUser.getPrincipal()==null) {
			Object userIdentity = null;
			String realmName = null;
			boolean authenticated = false;
			if(request!=null && request.getRemoteUser()!=null) {
				userIdentity = request.getRemoteUser();
				String usernamePattern = prop.getProperty(PROP_AUTH_USERNAME_PATTERN);
				if(usernamePattern!=null) {
					Matcher m = Pattern.compile(usernamePattern).matcher(String.valueOf(userIdentity));
					if(m.find()) {
						try {
							userIdentity = m.group(1);
						}
						catch(IllegalStateException e) {
							log.warn("getSubject: Exception: "+e+" [pattern: "+usernamePattern+"]");
						}
						catch(IndexOutOfBoundsException e) {
							log.warn("getSubject: Exception: "+e+" [pattern: "+usernamePattern+"]");
						}
					}
					/*else {
						log.debug("userIdentity ["+userIdentity+"] does not match http pattern [pattern: "+usernamePattern+"]");
					}*/
				}
				realmName = prop.getProperty(PROP_AUTH_HTTPREALM, DEFAULT_AUTH_HTTPREALM);
				authenticated = true;
			}
			else {
				//TODOne: get static info from properties...
				userIdentity = prop.getProperty(PROP_AUTH_ANONUSER, DEFAULT_AUTH_ANONUSER);
				realmName = prop.getProperty(PROP_AUTH_ANONREALM, DEFAULT_AUTH_ANONREALM);
			}
			PrincipalCollection principals = new SimplePrincipalCollection(userIdentity, realmName);
			currentUser = new Subject.Builder().principals(principals).authenticated(authenticated).session(currentUser.getSession()).buildSubject();
		}
		return currentUser;
	}
	
	public static void checkPermission(Subject subject, String permission, String object) {
		if(! isPermitted(subject, permission, object)) {
			throwPermissionException(subject, permission, object);
			/*
			if(object!=null) {
				object = object.replaceAll("\\.", ":");
			}
			log.warn("no permission '"+permission+"' for subject '"+ (subject!=null ? subject.getPrincipal() : null) +"' on object '"+object+"'"); // ; "+subject.getPrincipal()+"'");
			throw new ForbiddenException("["+permission+(object!=null?":"+object:"")+"]: authorization required", isAuthenticated(subject));
			*/
		}
	}
	
	public static boolean throwPermissionException(Subject subject, String permission, String object) {
		if(object!=null) {
			object = object.replaceAll("\\.", ":");
		}
		log.warn("no permission '"+permission+"' for subject " + (subject!=null ? "'" + subject.getPrincipal() + "'" : "*null*") + " on object '"+object+"'"); // ; "+subject.getPrincipal()+"'");
		throw new ForbiddenException("["+permission+(object!=null?":"+object:"")+"]: authorization required", isAuthenticated(subject));
	}
	
	public static boolean isAuthenticated(Subject subject) {
		if(subject==null) { return false; }
		return subject.isAuthenticated();
	}

	public static boolean isPermitted(Subject subject, String permission) {
		return isPermitted(subject, permission, null);
	}
	
	public static boolean isPermitted(Subject subject, String permission, String object) {
		/*// null subject: shiro not enabled... SELECT & STATUS allowed
		if(subject == null && (ActionType.SELECT.toString().equals(permission) || ActionType.STATUS.toString().equals(permission))) {
			return true;
		}*/
		if(subject == null) {
			log.debug("isPermitted: null subject");
			return false;
		}
		if(object!=null) {
			object = object.replaceAll("\\.", ":");
			permission += ":"+object;
		}
		boolean permitted =  subject.isPermitted(permission);
		//log.info("checking permission '"+permission+"', subject = "+subject.getPrincipal()+" :: "+permitted);
		return permitted;
	}

	public static boolean isPermitted(Subject subject, String permission, String object, String parameter) {
		if(object!=null) {
			object = object.replaceAll("\\.", ":");
			permission += ":"+object;
		}
		if(parameter!=null) {
			parameter = parameter.replaceAll("\\.", ":");
			permission += ":"+parameter;
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
		throw new ForbiddenException("no authorization for any of "+permissionsStr, subject.isAuthenticated());
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
					AuthorizationInfo ai = getAuthorizationInfo(r, subject);
					if(ai!=null) {
						log.debug("AuthorizationInfo:: "+ai.getClass());
						Collection<String> cr = ai.getRoles();
						if(cr!=null) {
							log.debug("roles:: "+cr+" [realm="+r.getName()+"]");
							roles.addAll(cr);
						}
					}
					else {
						log.debug("null AuthorizationInfo: Realm/AuthorizationInfoInformer="+r.getClass());
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
	
	public static void authenticate(Subject currentUser, String username, String password) {
		AuthenticationToken token = new UsernamePasswordToken(username, password);
		currentUser.login(token);
	}
	
	static AuthorizationInfo getAuthorizationInfo(Realm r, Subject subject) {
		if(r instanceof AuthorizationInfoInformer) {
			AuthorizationInfoInformer ar = (AuthorizationInfoInformer) r;
			AuthorizationInfo ai = ar.getAuthorizationInfo(subject.getPrincipals());
			return ai;
		}
		log.debug("AuthorizationInfo (not an AuthorizationInfoInformer class - will use reflection):: "+r.getClass()+" / "+r.getName());
		Object o = ReflectionUtil.callProtectedMethodNoException(r, "getAuthorizationInfo", new Class<?>[]{ PrincipalCollection.class }, subject.getPrincipals());
		//log.info("AuthorizationInfo:: "+o);
		return (AuthorizationInfo) o;
	}
	
	public static boolean isShiroEnabled() {
		try {
			//org.apache.shiro.mgt.SecurityManager sm =
			SecurityUtils.getSecurityManager();
		}
		catch (UnavailableSecurityManagerException e) {
			log.debug("isShiroEnabled: UnavailableSecurityManagerException: "+e.getMessage());
			return false;
		}
		return true;
	}
	
	/*
	// shiro with no realms is not possible (shiro initialization error occurs)
	public static boolean isShiroRealmsAvaiable() {
		try {
			org.apache.shiro.mgt.SecurityManager sm = SecurityUtils.getSecurityManager();
			if(sm instanceof RealmSecurityManager) {
				RealmSecurityManager rsm = (RealmSecurityManager) sm;
				Collection<Realm> rs = rsm.getRealms();
				if(rs!=null) {
					return true;
				}
			}
		}
		catch (UnavailableSecurityManagerException e) {
			log.info("isShiroRealmsAvaiable: UnavailableSecurityManagerException: "+e);
			return false;
		}
		return false;
	}
	*/
	
}
