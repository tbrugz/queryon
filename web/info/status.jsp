<%@page import="com.google.gson.*"%>
<%@page import="tbrugz.sqldump.util.StringDecorator.StringQuoterDecorator"%>
<%@page import="tbrugz.sqldump.dbmodel.SchemaModel"%>
<%@page import="java.util.*, tbrugz.queryon.QueryOn"%>
<%!
	StringQuoterDecorator sqd = new StringQuoterDecorator("\"");
%>
{
"models-info": {
<%
Gson gson = new Gson();
//XXX: allow (or not) "dburl" value? see QOnModelUtils.setModelMetadata
Map<String, SchemaModel> models = (Map<String, SchemaModel>) application.getAttribute(QueryOn.ATTR_MODEL_MAP);
int i = 0;
if(models!=null && models.entrySet()!=null) {
for(Map.Entry<String, SchemaModel> entry: models.entrySet()) {
	if(i>0) { out.write(",\n"); }
	//out.write(sqd.get(entry.getKey()!=null?entry.getKey():"null")+": "+sqd.get(String.valueOf(entry.getValue().getMetadata())));
	out.write(sqd.get(entry.getKey()!=null?entry.getKey():"null")+": "+gson.toJson(entry.getValue().getMetadata()));
	i++;
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
