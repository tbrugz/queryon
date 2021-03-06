<%@page import="tbrugz.queryon.util.SchemaModelUtils"%>
<%@page import="tbrugz.queryon.util.QOnContextUtils"%>
<%@page import="tbrugz.queryon.processor.UpdatePluginUtils"%>
<%@page import="tbrugz.queryon.processor.QOnExecs"%>
<%@page import="tbrugz.queryon.processor.QOnTables"%>
<%@page import="tbrugz.queryon.processor.QOnQueries"%>
<%@page import="tbrugz.queryon.ResponseSpec"%>
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
response.setContentType(ResponseSpec.MIME_TYPE_JSON);
Properties prop = QOnContextUtils.getProperties(application);
Subject currentUser = ShiroUtils.getSubject(prop, request);
Gson gson = new Gson();
boolean permitted = ShiroUtils.isPermitted(currentUser, "MANAGE");

out.write(sqd.get("permitted")+": "+permitted+",\n");

%>
"models-info": {
<%
//XXX: allow (or not) "dburl" value? see QOnModelUtils.setModelMetadata
//XXX: for each model: add known object types...!?!
Map<String, SchemaModel> models = SchemaModelUtils.getModels(getServletContext());
int i = 0;
if(models!=null && models.entrySet()!=null) {
	if(permitted) {
		for(Map.Entry<String, SchemaModel> entry: models.entrySet()) {
			if(i>0) { out.write(",\n"); }
			String modelId = entry.getKey()!=null?entry.getKey():"null";
			//out.write(sqd.get(entry.getKey()!=null?entry.getKey():"null")+": "+sqd.get(String.valueOf(entry.getValue().getMetadata())));
			//XXX: filter properties if user not logged...
			out.write(sqd.get(modelId)+": "+gson.toJson(entry.getValue().getMetadata()));
			
			//qon-tables-warnings
			Map<String, String> tWarnings = (Map<String, String>) application.getAttribute(QOnTables.ATTR_TABLES_WARNINGS_PREFIX+"."+modelId);
			if(tWarnings!=null && tWarnings.size()>0) {
				out.write(",\n");
				out.write(sqd.get(modelId+".tables-warnings")+": "+gson.toJson(tWarnings));
				i++;
			}
			
			//qon-queries-warnings
			Map<String, String> qWarnings = (Map<String, String>) application.getAttribute(QOnQueries.ATTR_QUERIES_WARNINGS_PREFIX+"."+modelId);
			if(qWarnings!=null && qWarnings.size()>0) {
				out.write(",\n");
				out.write(sqd.get(modelId+".queries-warnings")+": "+gson.toJson(qWarnings));
				i++;
			}

			//qon-execs-warnings
			Map<String, String> eWarnings = (Map<String, String>) application.getAttribute(QOnExecs.ATTR_EXECS_WARNINGS_PREFIX+"."+modelId);
			if(eWarnings!=null && eWarnings.size()>0) {
				out.write(",\n");
				out.write(sqd.get(modelId+".execs-warnings")+": "+gson.toJson(eWarnings));
				i++;
			}

			//qon-init-warnings
			Map<String, String> iWarnings = (Map<String, String>) application.getAttribute(UpdatePluginUtils.ATTR_INIT_WARNINGS_PREFIX+"."+modelId);
			if(iWarnings!=null && iWarnings.size()>0) {
				out.write(",\n");
				out.write(sqd.get(modelId+".init-warnings")+": "+gson.toJson(iWarnings));
				i++;
			}
			
			i++;
		}
	}
}

Throwable initError = (Throwable) application.getAttribute(QueryOn.ATTR_INIT_ERROR);
if(initError!=null) {
	if(i>0) { out.write(",\n"); }
	out.write(sqd.get("init-error")+": "+gson.toJson(initError.toString()));
	i++;
}

%>
}
}
