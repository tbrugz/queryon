package tbrugz.queryon.shiro;

import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.subject.PrincipalCollection;

public interface AuthorizationInfoInformer {

	AuthorizationInfo getAuthorizationInfo(PrincipalCollection principals);

}
