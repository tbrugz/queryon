/*
 * License: http://www.apache.org/licenses/LICENSE-2.0
 */
package tbrugz.queryon.shiro;

import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.authz.permission.RolePermissionResolver;
import org.apache.shiro.realm.text.IniRealm;

public class IniRolePermissionResolver implements RolePermissionResolver {

	private static final Log log = LogFactory.getLog(IniRolePermissionResolver.class);

	/*public Permission resolvePermission(String permissionString) {
		return iniRealm.getPermissionResolver().resolvePermission(permissionString);
	}*/
	
	IniRealm iniRealm;

	public IniRealm getIniRealm() {
		return iniRealm;
	}

	public void setIniRealm(IniRealm iniRealm) {
		//System.out.println("IniRolePermissionResolver: set: "+iniRealm);
		this.iniRealm = iniRealm;
	}

	@Override
	public Collection<Permission> resolvePermissionsInRole(String roleString) {
		if(iniRealm==null) {
			log.debug("IniRolePermissionResolver: null resolver, role = "+roleString);
			return null;
		}
		RolePermissionResolver rpr = iniRealm.getRolePermissionResolver();
		if(rpr==null) {
			log.debug("IniRolePermissionResolver: null rolePermissionResolver, role = "+roleString);
			return null;
		}
		return iniRealm.getRolePermissionResolver().resolvePermissionsInRole(roleString);
	}
	
}
