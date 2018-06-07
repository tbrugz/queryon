<%@page import="tbrugz.queryon.util.ShiroUtils"%>
<%@page import="tbrugz.queryon.QueryOn"%>
<%@page import="java.util.Properties"%>
<%@page import="org.apache.shiro.SecurityUtils"
%><%@page import="org.apache.shiro.subject.Subject"
%><%
String role = request.getParameter("role");
if(role==null) { response.setStatus(400); }
else {
	Properties prop = (Properties) application.getAttribute(QueryOn.ATTR_PROP);
	Subject currentUser = ShiroUtils.getSubject(prop, request);
	//Subject currentUser = SecurityUtils.getSubject();
	out.write(currentUser.hasRole(role)?"1":"0");
}
%>