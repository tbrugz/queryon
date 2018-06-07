<%@page import="tbrugz.queryon.QueryOn"%>
<%@page import="tbrugz.queryon.util.ShiroUtils"%>
<%@page import="org.apache.shiro.subject.Subject"%>
<%@page import="java.util.Properties"%>
<%@page import="org.apache.shiro.SecurityUtils"%>
<%@ taglib prefix="shiro" uri="http://shiro.apache.org/tags" %>
<!DOCTYPE html>
<html>
<head>
	<title>QueryOn - logout</title>
	<link href="../css/queryon.css" rel="stylesheet">
	<link href="../css/qon-login.css" rel="stylesheet">
	<link rel="icon" type="image/png" href="../favicon.png" />
</head>
<body>
<%
String returnUrl = request.getParameter("return");

Properties prop = (Properties) application.getAttribute(QueryOn.ATTR_PROP);
Subject currentUser = ShiroUtils.getSubject(prop, request);

currentUser.logout();
//session.invalidate();

if(returnUrl!=null) {
	out.write("<script>window.location.href = '"+returnUrl+"';</script>");
}
%>
You have succesfully logged out.

<br><a href="../">home</a>
<br><a href="login.jsp">login</a>
</body>
