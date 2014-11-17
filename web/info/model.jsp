<%@page import="java.util.Arrays"
%><%@page import="tbrugz.sqldump.dbmodel.DBObjectType"
%><%@page import="tbrugz.sqldump.util.StringDecorator.StringQuoterDecorator"
%><%@page import="tbrugz.sqldump.util.Utils"
%><%@page import="tbrugz.queryon.SchemaModelUtils"
%><%!
	StringQuoterDecorator sqd = new StringQuoterDecorator("\"");
%>{
	"models": [<%= String.valueOf(Utils.join(SchemaModelUtils.getModelIds(application), ", ", sqd)) %>]
	<% //, "types": [< %//= Utils.join(Arrays.asList(DBObjectType.values()), ", ", sqd) % >] %>
}