<!DOCTYPE html>
<%@page import="tbrugz.sqldump.dbmodel.Table"%>
<%@page import="tbrugz.sqldump.dbmodel.Query"%>
<%@page import="tbrugz.sqldump.dbmodel.View"%>
<%@page import="tbrugz.queryon.QueryOn"%>
<%@page import="tbrugz.sqldump.dbmodel.SchemaModel"%>
<%@page import="tbrugz.sqldump.dbmodel.DBIdentifiable"%>
<%@page import="tbrugz.queryon.SchemaModelUtils"%>
<html>
<head>
    <title>QueryOn - query editor</title>
    <link href="css/queryon.css" rel="stylesheet">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="">
    <meta name="author" content="">
	<style type="text/css" media="screen">
	    #editor { 
	        position: absolute;
	        top: 0;
	        right: 0;
	        bottom: 0;
	        left: 0;
	    }
	</style>
</head>
<body>
<%!
SchemaModel model = null;
String schemaName = null;
String queryName = null;
String query = "";
%>

<%
model = (SchemaModel) application.getAttribute(QueryOn.ATTR_MODEL);
schemaName = request.getParameter("schema");
queryName = request.getParameter("name");
if(queryName!=null) {
	View v = DBIdentifiable.getDBIdentifiableBySchemaAndName(model.getViews(), schemaName, queryName);
	if(v == null) {
		v = DBIdentifiable.getDBIdentifiableByName(model.getViews(), queryName);
	}
	if(v != null) {
		if(v instanceof Query) {
			Query q = (Query) v;
			query = q.query;
		}
		else {
			query = "select * from "+v.getFinalName(true);
		}
	}
	else {
		Table t = DBIdentifiable.getDBIdentifiableBySchemaAndName(model.getTables(), schemaName, queryName);
		if(t != null) {
			query = "select * from "+t.getFinalName(true);
		}
		/*else {
			query = "select * from xxx";
		}*/
	}
}
if(query==null || query.equals("")) {
	query = "select * from xxx";
}
%>

<div id="editor"><%= query %></div>
    
<!-- see http://ace.c9.io/ -->
<!-- style? see http://ace.c9.io/build/kitchen-sink.html -->
<script src="js/ace/ace.js" type="text/javascript" charset="utf-8"></script>
<script>
    var editor = ace.edit("editor");
    editor.setTheme("ace/theme/eclipse"); //monokai,ambiance,twilight,,eclipse,github ?
    editor.getSession().setMode("ace/mode/sql");
</script>
</body>
