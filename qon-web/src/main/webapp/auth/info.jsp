<%@page import="tbrugz.queryon.ResponseSpec"%>
<%@page import="tbrugz.queryon.util.JsonDecorator"%>
<%@page import="tbrugz.queryon.util.SchemaModelUtils"%>
<%@page import="tbrugz.queryon.QueryOn"%>
<%@page import="tbrugz.sqldump.util.StringDecorator"%>
<%@page import="tbrugz.sqldump.util.Utils"%>
<%@page import="java.util.*"%>
<%@page import="tbrugz.queryon.util.ShiroUtils"%>
<%@page import="org.apache.shiro.SecurityUtils"%>
<%@page import="org.apache.shiro.subject.Subject"%>
<%@page import="com.google.gson.*"%>
{<%
	Gson gson = new Gson();

	StringDecorator quoter = new JsonDecorator();
	Properties prop = (Properties) application.getAttribute(QueryOn.ATTR_PROP);
	Subject currentUser = ShiroUtils.getSubject(prop, request);
	boolean responseWritten = false;
	
	//System.out.println("-[info.jsp  ] currentUser.getSession().getId()/req.getId()=="+currentUser.getSession().getId()+"/"+request.getSession().getId());
	
	// authenticated
	boolean authenticated = currentUser.isAuthenticated();
	out.write( (responseWritten?",":"") + "\n\t\"authenticated\": "+authenticated);
	
	// username
	Object principal = currentUser.getPrincipal();
	out.write(",\n\t\"username\": "+ ( principal!=null?quoter.get(String.valueOf(principal)):null ) );
	//out.write(",\n\t\"username\": "+ ( quoter.get(String.valueOf(principal)) ) );
	
	//out.write(",\n\t\"session-id\": "+ ( quoter.get(session.getId()) ) );
	
	List<String> exceptions = new ArrayList<String>();
	
	//XXX: anonymous user may have roles or permissions?
			
	// roles
	Set<String> roles = new HashSet<String>();
	try {
		roles = ShiroUtils.getSubjectRoles(currentUser);
	}
	catch(Exception e) {
		exceptions.add("getSubjectRoles: "+String.valueOf(e));
		//out.write(",\n\t\"exception.getSubjectRoles\": "+quoter.get(String.valueOf(e)));
	}
	
	out.write(",\n\t\"roles\": [");
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
	String[] permissionsArr = { "SELECT_ANY", "INSERT_ANY", "UPDATE_ANY", "DELETE_ANY", "SQL_ANY", "MANAGE" };
	List<String> permissionList = new ArrayList<String>();
	permissionList.addAll(Arrays.asList(permissionsArr));
	// * apply permissions
	Set<String> mids = SchemaModelUtils.getModelIds(application);
	for(String mid: mids) {
		permissionList.add("TABLE:APPLYDIFF:"+mid);
	}
	
	List<String> userPerms = new ArrayList<String>();
	try {
		for(String perm: permissionList) {
			if(ShiroUtils.isPermitted(currentUser, perm)) {
				userPerms.add(perm);
			}
		}
	}
	catch(Exception e) {
		exceptions.add("isPermitted: "+String.valueOf(e));
		//out.write(",\n\t\"exception.isPermitted\": "+quoter.get(String.valueOf(e)));
	}
	
	response.setContentType(ResponseSpec.MIME_TYPE_JSON);
	out.write(",\n\t\"permissions\": ["+Utils.join(userPerms, ",", quoter)+"]");
	
	if(exceptions.size()>0) {
		out.write(",\n\t\"exceptions\": "+gson.toJson(exceptions));
	}
%>
}
