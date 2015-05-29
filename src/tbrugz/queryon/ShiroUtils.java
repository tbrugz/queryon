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
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.shiro.AuthorizationInfoInformer;

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
			log.warn("no permission '"+permission+"' for subject '"+subject.getPrincipal()+" on object '"+object+"'"); // ; "+subject.getPrincipal()+"'");
			throw new BadRequestException(permission+(object!=null?":"+object:"")+": authorization required", HttpServletResponse.SC_FORBIDDEN);
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
	
	public static Set<String> getSubjectRoles(Subject subject) {
		Set<String> roles = new HashSet<String>();
		Object principal = subject.getPrincipal();
		if(principal==null) {
			return roles;
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
	
	/*static Set<String> oldGetSubjectRoles(Subject subject) {
		
		Set<String> roles = new HashSet<String>();
		Object principal = subject.getPrincipal();
		if(principal==null) {
			return roles;
		}
		org.apache.shiro.mgt.SecurityManager sm = SecurityUtils.getSecurityManager();
		
		if(sm instanceof RealmSecurityManager) {
			RealmSecurityManager rsm = (RealmSecurityManager) sm;
			Collection<Realm> rs = rsm.getRealms();
			if(rs!=null) {
				log.info("#realms = "+rs.size());
				for(Realm r: rs) {
					if(r instanceof AuthorizingRealm) {
						log.info("authorizing realm:: "+r.getName()+" / "+r.getClass().getSimpleName());
						AuthorizingRealm ar = (AuthorizingRealm) r;
						//ar.doGetAuthorizationInfo(currentUser.getPrincipals());
						//ar.getAuthorizationInfo(pc);
						
						Cache<Object, AuthorizationInfo> cache = ar.getAuthorizationCache();
						if(cache!=null) {
							log.info("cache:: "+cache+" "+principal+" "+principal.getClass());
							AuthorizationInfo ai = cache.get(principal);
							if(ai!=null) {
								log.info("AuthorizationInfo:: "+ai);
								Collection<String> cr = ai.getRoles();
								if(cr!=null) {
									log.info("roles:: "+cr);
									roles.addAll(cr);
								}
							}
							else {
								log.info("cache-keys:: "+cache.keys());
							}
							/*Set<Object> cacheKeys = cache.keys();
							if(cacheKeys!=null) {
								for(Object key: cacheKeys) {
									AuthorizationInfo ai = cache.get(key);
									//out.write("<ul><li>roles:: "+ai.getRoles()+"</li>");
									Collection<String> cr = ai.getRoles();
									if(cr!=null) {
										roles.addAll(cr);
									}
								}
							}* /
						}
					}
				}
			}
		}
		return roles;
	}*/
	
}
