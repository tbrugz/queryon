<%@page import="org.apache.shiro.SecurityUtils"
%><%@page import="org.apache.shiro.subject.Subject"
%><%
String role = request.getParameter("role");
if(role==null) { response.setStatus(400); }
else {
	Subject currentUser = SecurityUtils.getSubject();
	out.write(currentUser.hasRole(role)?"1":"0");
}
%>