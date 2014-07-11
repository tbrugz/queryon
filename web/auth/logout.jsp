<%@page import="org.apache.shiro.SecurityUtils"%>
<%@ taglib prefix="shiro" uri="http://shiro.apache.org/tags" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html>
<head>
	<title>QueryOn</title>
	<link href="../css/queryon.css" rel="stylesheet">
	<link href="../css/qon-login.css" rel="stylesheet">
</head>
<body>
<%

SecurityUtils.getSubject().logout();

%>
You have succesfully logged out.

<br><a href="../">home</a>
<br><a href="login.jsp">login</a>
</body>
