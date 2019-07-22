package tbrugz.queryon.auth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.shiro.subject.Subject;

import tbrugz.queryon.util.ShiroUtils;

public class ExtendedUserInfo extends UserInfo {
	
	static final String[] permissionsArr = { "SELECT_ANY", "INSERT_ANY", "UPDATE_ANY", "DELETE_ANY", "SQL_ANY", "MANAGE" };
	
	final Set<String> roles;
	final List<String> permissions;
	
	public ExtendedUserInfo(Subject subject, Set<String> modelIds) {
		super(subject);
		
		roles = ShiroUtils.getSubjectRoles(subject);
		
		List<String> permissionList = new ArrayList<String>();
		permissionList.addAll(Arrays.asList(permissionsArr));
		for(String mid: modelIds) {
			permissionList.add("TABLE:APPLYDIFF:"+mid); //XXX null _mid_?
		}
		
		permissions = new ArrayList<String>();
		for(String perm: permissionList) {
			if(ShiroUtils.isPermitted(subject, perm)) {
				permissions.add(perm);
			}
		}
	}

}
