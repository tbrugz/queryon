<%@page import="tbrugz.sqldump.util.StringDecorator"%>
<%@page import="tbrugz.sqldump.util.Utils"%>
<%@page import="java.util.List"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.Set"%>
<%@page import="tbrugz.queryon.ShiroUtils"%>
<%@page import="org.apache.shiro.SecurityUtils"
%><%@page import="org.apache.shiro.subject.Subject"
%>{<%
	StringDecorator quoter = new StringDecorator.StringQuoterDecorator("\"");
	Subject currentUser = SecurityUtils.getSubject();
	boolean authenticated= currentUser.isAuthenticated();
	out.write("\n\t\"authenticated\": "+authenticated);
	//if(authenticated) {
		// username
		out.write(",\n\t\"username\": "+ (authenticated?quoter.get(currentUser.getPrincipal().toString()):null) );
	//}
	
	//XXX: anonymous user may have roles or permissions?
			
		// roles
		out.write(",\n\t\"roles\": [");
		Set<String> roles = ShiroUtils.getCurrentUserRoles();
		boolean is1st = true;
		for(String role: roles) {
			if(currentUser.hasRole(role)) {
				if(is1st) {
					is1st = false;
				}
				else {
					out.write(", ");
				}
				out.write(quoter.get(role));
			}
		}
		out.write("]");
	
	//permissions
	//XXX: fixed list of permissions...
	String[] permissionsArr = { "SELECT_ANY" };
	List<String> userPerms = new ArrayList<String>();
	for(String perm: permissionsArr) {
		if(ShiroUtils.isPermitted(currentUser, perm)) {
			userPerms.add(perm);
		}
	}
	out.write(",\n\t\"permissions\": ["+Utils.join(userPerms, ",", quoter)+"]");
%>
}