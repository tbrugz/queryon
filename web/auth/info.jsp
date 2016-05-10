<%@page import="tbrugz.queryon.SchemaModelUtils"%>
<%@page import="tbrugz.queryon.QueryOn"%>
<%@page import="tbrugz.sqldump.util.StringDecorator"%>
<%@page import="tbrugz.sqldump.util.Utils"%>
<%@page import="java.util.*"%>
<%@page import="tbrugz.queryon.ShiroUtils"%>
<%@page import="org.apache.shiro.SecurityUtils"
%><%@page import="org.apache.shiro.subject.Subject"
%>{<%
	StringDecorator quoter = new StringDecorator.StringQuoterDecorator("\"");
	Properties prop = (Properties) application.getAttribute(QueryOn.ATTR_PROP);
	Subject currentUser = ShiroUtils.getSubject(prop);
	boolean authenticated = currentUser.isAuthenticated();
	out.write("\n\t\"authenticated\": "+authenticated);
	//if(authenticated) {
		// username
		Object principal = currentUser.getPrincipal();
		out.write(",\n\t\"username\": "+ ( principal!=null?quoter.get(String.valueOf(principal)):null ) );
	//}
	
	//XXX: anonymous user may have roles or permissions?
			
		// roles
		out.write(",\n\t\"roles\": [");
		Set<String> roles = ShiroUtils.getSubjectRoles(currentUser);
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
	
	// permissions
	// * fixed list of permissions
	String[] permissionsArr = { "SELECT_ANY", "INSERT_ANY", "UPDATE_ANY", "DELETE_ANY", "MANAGE" };
	List<String> permissionList = new ArrayList<String>();
	permissionList.addAll(Arrays.asList(permissionsArr));
	// * apply permissions
	Set<String> mids = SchemaModelUtils.getModelIds(application);
	for(String mid: mids) {
		permissionList.add("TABLE:APPLYDIFF:"+mid);
	}
	
	List<String> userPerms = new ArrayList<String>();
	for(String perm: permissionList) {
		if(ShiroUtils.isPermitted(currentUser, perm)) {
			userPerms.add(perm);
		}
	}
	out.write(",\n\t\"permissions\": ["+Utils.join(userPerms, ",", quoter)+"]");
%>
}