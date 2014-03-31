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
	        /*position: absolute;
	        top: 0;
	        bottom: 0;
	        width: 100%;
	        right: 1ex;
	        left: 1ex;
	        margin: 2em;
	        */
	        height: 20em;
	        border: 1px solid black;
	    }
	    #objectid-container {
	    	/*background-color: #ddd;*/
	    }
	    label {
	    	background-color: #ddd;
	    	font-weight: bold;
	    }
	    .container {
	    	border: 1px solid #999;
	    	margin-top: 2px;
	    	margin-bottom: 2px;
	    }
	</style>
	<script type="text/javascript" src="https://code.jquery.com/jquery-2.1.0.min.js"></script>
<script type="text/javascript">
	var responseType = "htmlx";

	function doRun() {
		var request = $.ajax({
			url : "q/QueryAny."+responseType,
			type : "POST",
			data : {
				schema : document.getElementById('schema').value,
				name : document.getElementById('name').value,
				sql: editor.getValue(),
			},
			dataType : "html"
		});
		
		request.done(function(data) {
			//XXX option to handle different response types (html, json, csv, xml)?
			//'close' style:: position: relative, float: right?
			$("#queryResult").html("<input type='button' style='background-color: #444; position: absolute; right: 0px; font-weight: bold;' onclick='document.getElementById(\"queryResult\").innerHTML = \"\"' value='X'/>");
			$("#queryResult").append(data);
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			console.log(jqXHR);
			alert("Request failed ["+textStatus+"]: "+jqXHR.responseText);
		});
	}
	//type: 'POST' - https://api.jquery.com/jQuery.ajax/
</script>
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
if(schemaName==null) { schemaName = ""; }
if(queryName==null) { queryName = ""; }
%>

<div class="container" id="objectid-container">
	<label>schema: <input type="text" id="schema" name="schema" value="<%= schemaName %>"/></label>
	<label>name: <input type="text" id="name" name="name" value="<%= queryName %>"/></label>
</div>

<div id="editor"><%= query %></div>

<div class="container">
	<input type="button" value="validate">
	<input type="button" value="run" onclick="javascript:doRun();">
	<input type="button" value="save">
</div>

<div class="container">
	<div id="queryResult"></div>
</div>

<!-- see http://ace.c9.io/ -->
<!-- style? see http://ace.c9.io/build/kitchen-sink.html -->
<script src="js/ace/ace.js" type="text/javascript" charset="utf-8"></script>
<script>
    var editor = ace.edit("editor");
    editor.setTheme("ace/theme/eclipse"); //monokai,ambiance,twilight,,eclipse,github ?
    editor.getSession().setMode("ace/mode/sql");
</script>


</body>
