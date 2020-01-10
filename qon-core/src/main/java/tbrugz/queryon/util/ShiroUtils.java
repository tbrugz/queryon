package tbrugz.queryon.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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

import tbrugz.queryon.auth.provider.IdentityProvider;
import tbrugz.queryon.exception.ForbiddenException;
import tbrugz.queryon.shiro.AuthorizationInfoInformer;
import tbrugz.sqldump.util.Utils;

public class ShiroUtils {

	static final Log log = LogFactory.getLog(ShiroUtils.class);
	
	static final String PROP_AUTH_IDENTITY_PROVIDERS = "queryon.auth.identity-providers";
	
	static final String PROP_AUTH_ANONUSER = "queryon.auth.anon-username";
	static final String PROP_AUTH_ANONREALM = "queryon.auth.anon-realm";
	
	static final String DEFAULT_AUTH_ANONUSER = "anonymous";
	static final String DEFAULT_AUTH_ANONREALM = "anonRealm";
	
	static final List<IdentityProvider> identityProviders = new ArrayList<IdentityProvider>();
	
	// user for (unit) test (for now, at least)
	static Map<Object, Set<String>> userRoles = new HashMap<Object, Set<String>>();
	
	public static void init(Properties prop) {
		List<String> providers = Utils.getStringListFromProp(prop, PROP_AUTH_IDENTITY_PROVIDERS, ",");
		if(providers!=null) {
			for(String s: providers) {
				try {
					//Class<?> c = Utils.getClassWithinPackages(s);
					Class<?> c = Class.forName(s);
					IdentityProvider ip = (IdentityProvider) Utils.getClassInstance(c);
					identityProviders.add(ip);
					log.debug("Loaded identy provider '"+ip.getClass().getName()+"'");
				}
				catch(RuntimeException e) {
					log.warn("Error loading identy provider '"+s+"': "+e);
					log.debug("Error loading identy provider '"+s+"'", e);
				}
				catch (ClassNotFoundException e) {
					log.warn("Error loading identy provider '"+s+"': "+e);
					log.debug("Error loading identy provider '"+s+"'", e);
				}
			}
		}
		if(identityProviders.size()==0) {
			log.debug("No identy provider loaded");
		}
	}
	
	static Subject getSubject(Properties prop) {
		return getSubject(prop, null);
	}
	
	public static Subject getSubject(Properties prop, HttpServletRequest request) {
		Subject currentUser = null;
		
		try {
			currentUser = SecurityUtils.getSubject();
		}
		catch(UnavailableSecurityManagerException e) {
			if(log.isDebugEnabled()) {
				log.debug("getSubject: UnavailableSecurityManagerException: "+e.getMessage());
			}
			return null;
		}
		if(currentUser.getPrincipal()==null) {
			Object userIdentity = null;
			String realmName = null;
			boolean authenticated = false;
			
			if(request!=null) {
				for(IdentityProvider ip: identityProviders) {
					ip.setInfo(request, prop);
					if(ip.isAuthenticated()) {
						authenticated = true;
						userIdentity = ip.getIdentity();
						realmName = ip.getRealm();
						//realmName = prop.getProperty(PROP_AUTH_HTTPREALM, DEFAULT_AUTH_HTTPREALM);
						log.debug("getSubject: Subject authenticated with provider '"+ip.getClass().getName()+"' [userIdentity="+userIdentity+"; realmName="+realmName+"]");
						break;
					}
				}
				/*if(log.isTraceEnabled()) {
					log.trace("getSubject: Subject will be built from request.getUserPrincipal() [userIdentity="+userIdentity+"; realmName="+realmName+"]");
				}*/
			}
			
			if(userIdentity == null) {
				// Adds principal do anonymous user so that we may check their perissions
				// see: https://issues.apache.org/jira/browse/SHIRO-526
				//XXX add property to set (or not) userIdentity/principal to anonymous user?
				userIdentity = prop.getProperty(PROP_AUTH_ANONUSER, DEFAULT_AUTH_ANONUSER);
				realmName = prop.getProperty(PROP_AUTH_ANONREALM, DEFAULT_AUTH_ANONREALM);
				if(log.isTraceEnabled()) {
					log.trace("getSubject: Anonymous Subject will be built [userIdentity="+userIdentity+"; realmName="+realmName+"]");
				}
			}
			
			PrincipalCollection principals = new SimplePrincipalCollection(userIdentity, realmName);
			currentUser = new Subject.Builder().principals(principals).authenticated(authenticated).session(currentUser.getSession()).buildSubject();
			if(log.isDebugEnabled()) {
				log.debug("getSubject: Subject built [userIdentity="+userIdentity+"; realmName="+realmName+"; authenticated="+authenticated+
						"; session="+(currentUser.getSession()!=null?currentUser.getSession().getId():null)+"]");
			}
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
