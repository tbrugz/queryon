<%@page import="tbrugz.sqldump.dbmodel.*"
%><%@page import="java.util.*"
%><%@page import="tbrugz.sqldump.dbmodel.DBObjectType"
%><%@page import="tbrugz.sqldump.util.StringDecorator.StringQuoterDecorator"
%><%@page import="tbrugz.sqldump.util.Utils"
%><%@page import="tbrugz.queryon.SchemaModelUtils"
%><%!
public String normalize(String s) {
	if(s==null) return "";
	return s;
}
%><%
	StringQuoterDecorator sqd = new StringQuoterDecorator("\"");
	SchemaModel sm = SchemaModelUtils.getModel(application, request.getParameter("model"));
	Set<String> names = new TreeSet<String>();
	
	Set<Table> ts = sm.getTables();
	for(Table t: ts) { names.add(normalize(t.getSchemaName())); }
	Set<View> vs = sm.getViews();
	for(View v: vs) { names.add(normalize(v.getSchemaName())); }
	Set<ExecutableObject> eos = sm.getExecutables();
	for(ExecutableObject eo: eos) { names.add(normalize(eo.getSchemaName())); }
	//XXX: add FKs, indexes, sequences, synonyms, triggers ?? 
%>{
	"schemas": [<%= String.valueOf(Utils.join(names, ", ", sqd)) %>]
}