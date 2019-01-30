<%@page import="tbrugz.queryon.util.WebUtils"%>
<%@page import="tbrugz.queryon.QueryOn"%>
<%@page import="java.util.Properties"%>
<%@page import="org.apache.shiro.authc.AuthenticationException"%>
<%@page import="tbrugz.queryon.util.ShiroUtils"%>
<%@page import="java.io.PrintWriter"%>
<%@page import="java.io.StringWriter"%>
<%@page import="org.apache.shiro.ShiroException"%>
<%@page import="org.apache.shiro.authc.IncorrectCredentialsException"%>
<%@page import="org.apache.shiro.authc.UnknownAccountException"%>
<%@page import="org.apache.shiro.authc.UsernamePasswordToken"%>
<%@page import="org.apache.shiro.subject.Subject"%>
<%@page import="org.apache.shiro.SecurityUtils"%>
<%@page import="org.apache.shiro.authc.AuthenticationToken"%>
<!-- %@ taglib prefix="shiro" uri="http://shiro.apache.org/tags" %> -->
<!DOCTYPE html>
<html>
<head>
	<title>QueryOn - login</title>
	<link href="../css/queryon.css" rel="stylesheet">
	<link href="../css/qon-login.css" rel="stylesheet">
	<link rel="icon" type="image/png" href="../favicon.png" />
</head>
<body>
<%

String defaultAppname = "QueryOn";
Properties prop = (Properties) application.getAttribute(QueryOn.ATTR_PROP);
String appname = prop.getProperty(WebUtils.PROP_WEB_APPNAME, defaultAppname);
String loginMessage = prop.getProperty(WebUtils.PROP_WEB_LOGINMESSAGE);

String username = request.getParameter("username");
String password = request.getParameter("password");
String returnUrl = request.getParameter("return");

Subject currentUser = SecurityUtils.getSubject();
String loginError = null;
int clientErrorCode = 400;
int otherErrorCode = 400;

//System.out.println("-[login.jsp ] currentUser.getSession().getId()/req.getId()=="+currentUser.getSession().getId()+"/"+request.getSession().getId());

if(username!=null) {
	try {
		ShiroUtils.authenticate(currentUser, username, password);
		//System.out.println("login.jsp: auth: "+username);
		//System.out.println("-[login.jsp] currentUser.getPrincipal()=="+currentUser.getPrincipal());
		if(returnUrl!=null) {
			out.write("<script>window.location.href = '"+returnUrl+"';</script>");
		}
	}
	catch(UnknownAccountException e) {
		//loginWarning = "<em class='warning'>Unknown account</em><br/>";
		loginError = "Unknown account";
		response.setStatus(clientErrorCode);
	}
	catch(IncorrectCredentialsException e) {
		loginError = "Incorrect password";
		response.setStatus(clientErrorCode);
	}
	catch(AuthenticationException e) {
		loginError = "<b>Authentication Exception</b>: "+e;
		response.setStatus(clientErrorCode);
	}
	catch(ShiroException e) {
		loginError = "<b>Shiro Exception:</b> "+e.getMessage();
		response.setStatus(otherErrorCode);
		/*out.write("<pre>");
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		out.write(sw.toString());
		out.write("</pre>");*/
	}
}
else {
	//System.out.println("login.jsp: auth: username is null");
}

%>
<!-- <h3>Hi <shiro:guest>Guest</shiro:guest><shiro:user>
<%= currentUser.getPrincipal() %>
</shiro:user>!
</h3> -->

<div class="container">
<div>

	<div class="title">Login to <%= appname %></div>
	<hr/>
	<% if(loginError!=null) { %>
	<em class='warning'><%= loginError %></em>
	<hr/>
	<% } %>
	<% if(loginMessage!=null) { %>
	<div><%= loginMessage %></div>
	<hr/>
	<% } %>
	<form method="post">
		<label>Username: <input type="text" name="username"/></label>
		<label>Password: <input type="password" name="password"/></label>
		<!-- <input type="checkbox" name="rememberMe" value="true"/>Remember Me? <br/> -->
		<hr/>
		<input type="submit">
	</form>
	
</div>
<!-- 
<h3>Roles you have:</h3>

<p>
    <shiro:hasRole name="admin"><li>admin<br/></shiro:hasRole>
    <shiro:hasRole name="user"><li>user<br/></shiro:hasRole>
</p>
-->
</div>

<!-- <br><a href="../">home</a>
<br><a href="logout.jsp">logout</a> -->
</body>
