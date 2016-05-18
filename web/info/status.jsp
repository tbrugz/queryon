<%@page import="tbrugz.queryon.util.ShiroUtils"%>
<%@page import="org.apache.shiro.subject.Subject"%>
<%@page import="com.google.gson.*"%>
<%@page import="tbrugz.sqldump.util.StringDecorator.StringQuoterDecorator"%>
<%@page import="tbrugz.sqldump.dbmodel.SchemaModel"%>
<%@page import="java.util.*, tbrugz.queryon.QueryOn"%>
<%!
	StringQuoterDecorator sqd = new StringQuoterDecorator("\"");
%>
{
<%
Properties prop = (Properties) application.getAttribute(QueryOn.ATTR_PROP);
Subject currentUser = ShiroUtils.getSubject(prop);
Gson gson = new Gson();
boolean permitted = ShiroUtils.isPermitted(currentUser, "MANAGE");

if(! permitted) {
	out.write(sqd.get("permitted")+": "+sqd.get("false")+",\n");
}

%>
"models-info": {
<%
//XXX: allow (or not) "dburl" value? see QOnModelUtils.setModelMetadata
//XXX: for each model: add known object types...!?!
Map<String, SchemaModel> models = (Map<String, SchemaModel>) application.getAttribute(QueryOn.ATTR_MODEL_MAP);
int i = 0;
if(models!=null && models.entrySet()!=null) {
	if(ShiroUtils.isPermitted(currentUser, "MANAGE")) {
		for(Map.Entry<String, SchemaModel> entry: models.entrySet()) {
			if(i>0) { out.write(",\n"); }
			//out.write(sqd.get(entry.getKey()!=null?entry.getKey():"null")+": "+sqd.get(String.valueOf(entry.getValue().getMetadata())));
			//XXX: filter properties if user not logged...
			out.write(sqd.get(entry.getKey()!=null?entry.getKey():"null")+": "+gson.toJson(entry.getValue().getMetadata()));
			i++;
		}
	}
}

Throwable initError = (Throwable) application.getAttribute(QueryOn.ATTR_INIT_ERROR);
if(initError!=null) {
	if(i>0) { out.write(",\n"); }
	out.write(sqd.get("init-error")+": "+gson.toJson(initError.toString()));
}

%>
}
}
