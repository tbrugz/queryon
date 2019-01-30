/*
 * License: http://www.apache.org/licenses/LICENSE-2.0
 */
package tbrugz.queryon.shiro;

import java.util.Collection;

import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.authz.SimpleRole;
import org.apache.shiro.authz.permission.RolePermissionResolver;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.subject.PrincipalCollection;

public class IniRealmRolePermissionResolver extends IniRealm implements RolePermissionResolver, AuthorizationInfoInformer {

	@Override
	public Collection<Permission> resolvePermissionsInRole(String roleString) {
		SimpleRole sr = getRole(roleString);
		if(sr==null) { return null; }
		return sr.getPermissions();
	}
	
	@Override
	public RolePermissionResolver getRolePermissionResolver() {
		RolePermissionResolver rpr = super.getRolePermissionResolver();
		if(rpr==null) {
			return this;
		}
		return rpr;
	}
	
	/**
	 * Making 'getAuthorizationInfo' public ;)
	 */
	@Override
	public AuthorizationInfo getAuthorizationInfo(PrincipalCollection principals) {
		return super.getAuthorizationInfo(principals);
	}
	
}
