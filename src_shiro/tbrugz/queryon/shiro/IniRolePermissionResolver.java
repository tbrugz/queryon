/*
 * License: http://www.apache.org/licenses/LICENSE-2.0
 */
package tbrugz.queryon.shiro;

import java.util.Collection;

import org.apache.shiro.authz.Permission;
import org.apache.shiro.authz.permission.RolePermissionResolver;
import org.apache.shiro.realm.text.IniRealm;

public class IniRolePermissionResolver implements RolePermissionResolver {

	/*public Permission resolvePermission(String permissionString) {
		return iniRealm.getPermissionResolver().resolvePermission(permissionString);
	}*/
	
	IniRealm iniRealm;

	public IniRealm getIniRealm() {
		return iniRealm;
	}

	public void setIniRealm(IniRealm iniRealm) {
		System.out.println("IniRolePermissionResolver: set: "+iniRealm);
		this.iniRealm = iniRealm;
	}

	public Collection<Permission> resolvePermissionsInRole(String roleString) {
		if(iniRealm==null) {
			System.out.println("IniRolePermissionResolver: null resolver, role = "+roleString);
			return null;
		}
		RolePermissionResolver rpr = iniRealm.getRolePermissionResolver();
		if(rpr==null) {
			System.out.println("IniRolePermissionResolver: null rolePermissionResolver, role = "+roleString);
			return null;
		}
		return iniRealm.getRolePermissionResolver().resolvePermissionsInRole(roleString);
	}
	
}
