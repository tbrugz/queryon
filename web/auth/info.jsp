<%@page import="org.apache.shiro.SecurityUtils"
%><%@page import="org.apache.shiro.subject.Subject"
%>{<%
	Subject currentUser = SecurityUtils.getSubject();
	boolean authenticated= currentUser.isAuthenticated();
	out.write("\n\t\"authenticated\": "+authenticated);
	if(authenticated) {
		out.write(",\n\t\"username\": \""+currentUser.getPrincipal()+"\"");
		out.write(",\n\t\"roles\": [");
		String[] roles = new String[]{"user", "admin"};
		boolean is1st = true;
		for(String role: roles) {
			if(currentUser.hasRole(role)) {
				if(is1st) {
					is1st = false;
				}
				else {
					out.write(", ");
				}
				out.write("\""+role+"\"");
			}
		}
		out.write("]");
	}
%>
}