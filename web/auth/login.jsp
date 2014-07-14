<%@page import="java.io.PrintWriter"%>
<%@page import="java.io.StringWriter"%>
<%@page import="org.apache.shiro.ShiroException"%>
<%@page import="org.apache.shiro.authc.IncorrectCredentialsException"%>
<%@page import="org.apache.shiro.authc.UnknownAccountException"%>
<%@page import="org.apache.shiro.authc.UsernamePasswordToken"%>
<%@page import="org.apache.shiro.subject.Subject"%>
<%@page import="org.apache.shiro.SecurityUtils"%>
<%@page import="org.apache.shiro.authc.AuthenticationToken"%>
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

String username = request.getParameter("username");
String password = request.getParameter("password");
if(username!=null) {
	AuthenticationToken token = new UsernamePasswordToken(username, password);
	Subject currentUser = SecurityUtils.getSubject();
	try {
		currentUser.login(token);
		System.out.println("login.jsp: auth: "+username);
	}
	catch(UnknownAccountException e) {
		out.write("<em class='warning'>Unknown account</em><br/>");
	}
	catch(IncorrectCredentialsException e) {
		out.write("<em class='warning'>Incorrect password</em><br/>");
	}
	catch(ShiroException e) {
		out.write("<em class='warning'>Shiro Exception: "+e.getMessage()+"</em><br/><pre>");
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		out.write(sw.toString());
		out.write("</pre>");
	}
}
else {
	System.out.println("login.jsp: auth: username is null");
}

%>
<h3>Hi <shiro:guest>Guest</shiro:guest><shiro:user>
<%= org.apache.shiro.SecurityUtils.getSubject().getPrincipal() %>
</shiro:user>!
</h3>

<form method="post">
   Username: <input type="text" name="username"/> <br/>
   Password: <input type="password" name="password"/> <br/>
   <input type="checkbox" name="rememberMe" value="true"/>Remember Me? <br/>
   <input type="submit">
</form>

<h3>Roles you have:</h3>

<p>
    <shiro:hasRole name="admin"><li>admin<br/></shiro:hasRole>
    <shiro:hasRole name="user"><li>user<br/></shiro:hasRole>
</p>

<br><a href="../">home</a>
<br><a href="logout.jsp">logout</a>
</body>
