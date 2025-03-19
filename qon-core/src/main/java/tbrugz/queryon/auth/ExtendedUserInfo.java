package tbrugz.queryon.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.shiro.subject.Subject;

import tbrugz.queryon.QueryOn.ActionType;
import tbrugz.queryon.util.ShiroUtils;

public class ExtendedUserInfo extends UserInfo {
	
	static final ActionType[] permissionsArr = {
		ActionType.SELECT_ANY,
		ActionType.INSERT_ANY, ActionType.UPDATE_ANY, ActionType.DELETE_ANY,
		ActionType.VALIDATE_ANY, ActionType.SQL_ANY, ActionType.MANAGE
	};
	
	static final String[] permissionsStrArr = {
		"PLUGIN:QOnQueries:readQuery",
	};

	final Set<String> roles;
	final List<String> permissions;
	
	public ExtendedUserInfo(Subject subject, Set<String> modelIds) {
		super(subject);
		
		roles = ShiroUtils.getSubjectRoles(subject);
		
		List<String> permissionList = new ArrayList<String>();
		for(ActionType perm: permissionsArr) {
			permissionList.add(perm.name());
		}
		for(String perm: permissionsStrArr) {
			permissionList.add(perm);
		}
		// XXX add APPLYDIFF when instance has only 1 model?
		if(modelIds!=null) {
			for(String mid: modelIds) {
				permissionList.add("TABLE:APPLYDIFF:"+mid); //XXX null _mid_?
			}
		}
		
		permissions = new ArrayList<String>();
		for(String perm: permissionList) {
			if(ShiroUtils.isPermitted(subject, perm)) {
				permissions.add(perm);
			}
		}
	}

}
