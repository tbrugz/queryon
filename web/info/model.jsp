<%@page import="tbrugz.queryon.ResponseSpec"%>
<%@page import="java.util.Set"
%><%@page import="java.util.Arrays"
%><%@page import="tbrugz.sqldump.dbmodel.DBObjectType"
%><%@page import="tbrugz.sqldump.util.StringDecorator.StringQuoterDecorator"
%><%@page import="tbrugz.sqldump.util.Utils"
%><%@page import="tbrugz.queryon.util.*"
%><%!
	StringQuoterDecorator sqd = new StringQuoterDecorator("\"");
%><%
	String models = null;
	Set<String> modelSet = SchemaModelUtils.getModelIds(application);
	//System.out.println(modelSet);
	if(modelSet!=null && modelSet.size()>0 && modelSet.iterator().next()!=null) {
		models = Utils.join(modelSet, ", ", sqd);
	}
	response.setContentType(ResponseSpec.MIME_TYPE_JSON);
%>{
	"models": [<%= models %>]
	<% //, "types": [< %//= Utils.join(Arrays.asList(DBObjectType.values()), ", ", sqd) % >] %>
}
