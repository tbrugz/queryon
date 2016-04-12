<%@page import="java.util.Set"%>
<%@page import="tbrugz.queryon.shiro.QOnActiveDirectoryRealm"%>
<%@page import="org.apache.shiro.session.Session"%>
<%@page import="org.apache.shiro.cache.Cache"%>
<%@page import="org.apache.shiro.realm.activedirectory.ActiveDirectoryRealm"%>
<%@page import="org.apache.shiro.authc.AuthenticationInfo"%>
<%@page import="java.io.PrintWriter"%>
<%@page import="java.io.StringWriter"%>
<%@page import="org.apache.shiro.ShiroException"%>
<%@page import="org.apache.shiro.authz.AuthorizationInfo"%>
<%@page import="org.apache.shiro.realm.AuthorizingRealm"%>
<%@page import="java.util.List"%>
<%@page import="org.apache.shiro.subject.PrincipalCollection"%>
<%@page import="org.apache.shiro.realm.Realm"%>
<%@page import="java.util.Collection"%>
<%@page import="org.apache.shiro.mgt.RealmSecurityManager"%>
<%@page import="org.apache.shiro.authc.IncorrectCredentialsException"%>
<%@page import="org.apache.shiro.authc.UnknownAccountException"%>
<%@page import="org.apache.shiro.authc.UsernamePasswordToken"%>
<%@page import="org.apache.shiro.subject.Subject"%>
<%@page import="org.apache.shiro.SecurityUtils"%>
<%@page import="org.apache.shiro.authc.AuthenticationToken"%>
<%@ taglib prefix="shiro" uri="http://shiro.apache.org/tags" %>
<!DOCTYPE html>
<html>
<head>
	<title>QueryOn - login (debug)</title>
	<link href="../css/queryon.css" rel="stylesheet">
	<link href="../css/qon-login.css" rel="stylesheet">
	<link rel="icon" type="image/png" href="favicon.png" />
</head>
<body>
<%

String username = request.getParameter("username");
String password = request.getParameter("password");
org.apache.shiro.mgt.SecurityManager sm = SecurityUtils.getSecurityManager();
Subject currentUser = SecurityUtils.getSubject();
if(username!=null) {
	AuthenticationToken token = new UsernamePasswordToken(username, password);
	try {
		//AuthenticationInfo ai = sm.authenticate(token);
		//out.write("authenticated: "+username+" ;; "+ai);
		//System.out.println("login.jsp: auth: "+username+" class: "+currentUser.getClass().getName());
		currentUser.login(token);
		//System.out.println("login.jsp: auth: "+username);
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
	//System.out.println("login.jsp: auth: username is null");
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


<h3>Session info:</h3>
<%
Session ss = currentUser.getSession();

out.write("\n<br><b>id</b>: "+ss.getId());
out.write("\n<br><b>host</b>: "+ss.getHost());
out.write("\n<br><b>timeout</b>: "+ss.getTimeout()+"ms");
out.write("\n<br><b>startTimestamp</b>: "+ss.getStartTimestamp());
out.write("\n<br><b>lastAccessTime</b>: "+ss.getLastAccessTime());

Collection<Object> keys = ss.getAttributeKeys();
if(keys!=null) {
	out.write("\n<br><b>session attrs</b>::\n<ul>");
	for(Object o: keys) {
		out.write("\n<li><b>"+o+"</b>: "+ss.getAttribute(o));
	}
	out.write("\n</ul>");
}
%>
<h3>Roles you have:</h3>

<p>
    <shiro:hasRole name="admin"><li>admin<br/></shiro:hasRole>
    <shiro:hasRole name="developer"><li>developer<br/></shiro:hasRole>
    <shiro:hasRole name="user"><li>user<br/></shiro:hasRole>
    
<%
String[] roles = new String[]{"admin", "developer", "user"};
for(String s: roles) {
	out.write("<li>role '"+s+"': "+currentUser.hasRole(s));
}
%>
</p>

<h3>Permissions</h3>

<p>
<%
String[] perms = new String[]{"RELATION:STATUS", "TABLE:SELECT:ABC"};
for(String s: perms) {
	out.write("<li>permission '"+s+"': "+currentUser.isPermitted(s));
}

%>
</p>


<%
PrincipalCollection pc = currentUser.getPrincipals();
if(pc!=null) {
	List<?> l = pc.asList();
	out.write("User info [getPrincipals()]: <ul>");
	for(Object o: l) {
		out.write("<li>"+o.getClass().getSimpleName()+":: "+o.toString()+"\n");
	}
	out.write("</ul>");
	out.write("<br>int id? = "+pc.oneByType(Integer.class));
	//out.write("first name? = "+);
}
if(sm instanceof RealmSecurityManager) {
	RealmSecurityManager rsm = (RealmSecurityManager) sm;
	Collection<Realm> rs = rsm.getRealms();
	if(rs!=null) {
		out.write("\n<br><br><b>#realms</b> = "+rs.size());
		for(Realm r: rs) {
			if(r instanceof AuthorizingRealm) {
				out.write("<br><b>authorizing realm:: "+r.getName()+" / "+r.getClass().getSimpleName()+"</b> ");
				AuthorizingRealm ar = (AuthorizingRealm) r;
				//ar.doGetAuthorizationInfo(currentUser.getPrincipals());
				//ar.getAuthorizationInfo(pc);
				
				Cache<Object, AuthorizationInfo> cache = ar.getAuthorizationCache();
				if(cache!=null) {
					Set<Object> cacheKeys = cache.keys();
					if(cacheKeys!=null) {
						for(Object key: cacheKeys) {
							AuthorizationInfo ai = cache.get(key);
							//Collection<AuthorizationInfo> ais = cache.values();
							out.write("<ul><li>AuthorizationInfo:: "+key+" / "+ai.toString()+"</li>");
							out.write("<li>roles:: "+ai.getRoles()+"</li>");
							out.write("<li>permissions:: "+ai.getStringPermissions()+"</li></ul>");
						}
					}
				}
				
				if(ar instanceof ActiveDirectoryRealm) {
					//ActiveDirectoryRealm adr = (ActiveDirectoryRealm) ar;
					//adr.getAuthorizationInfo(pc);
					if(ar instanceof QOnActiveDirectoryRealm) {
						QOnActiveDirectoryRealm qadr = (QOnActiveDirectoryRealm) ar;
						AuthorizationInfo ai = qadr.getAuthorizationInfo(pc);
						if(ai!=null) {
							out.write("<br><b>QOnActiveDirectoryRealm roles</b>:: "+ai.getRoles()+"<br>");
						}
						else {
							out.write("<br><b>QOnActiveDirectoryRealm </b>:: null AuthorizationInfo<br>");
						}
					}
				}
			}
			else {
				out.write("<br>non-authorizing realm:: "+r.getName()+" / "+r.getClass().getSimpleName());
			}
		}
	}
}
%>
<br>
<br><a href="../">home</a>
<br><a href="logout.jsp">logout</a>
<br><a href="login.jsp">login</a>
<br><a href="login-debug.jsp">login-debug</a>
</body>
