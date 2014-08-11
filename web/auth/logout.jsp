<%@page import="org.apache.shiro.SecurityUtils"%>
<%@ taglib prefix="shiro" uri="http://shiro.apache.org/tags" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
	<title>QueryOn</title>
	<link href="../css/queryon.css" rel="stylesheet">
	<link href="../css/qon-login.css" rel="stylesheet">
	<link rel="icon" type="image/png" href="../favicon.png" />
</head>
<body>
<%
String returnUrl = request.getParameter("return");

SecurityUtils.getSubject().logout();

if(returnUrl!=null) {
	out.write("<script>window.location.href = '"+returnUrl+"';</script>");
}
%>
You have succesfully logged out.

<br><a href="../">home</a>
<br><a href="login.jsp">login</a>
</body>
