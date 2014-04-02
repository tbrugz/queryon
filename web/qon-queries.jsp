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
    <link href="css/qon-queries.css" rel="stylesheet">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="">
    <meta name="author" content="">
	<script type="text/javascript" src="https://code.jquery.com/jquery-2.1.0.min.js"></script>
	<!-- see: https://github.com/oscargodson/jkey -->
	<script type="text/javascript" src="js/jquery.jkey.js"></script>
<script type="text/javascript">
	var responseType = "htmlx";
	var queryOnUrl = 'q';
	var processorUrl = 'processor';

	function doRun() {
		var reqData = {
			schema : document.getElementById('schema').value,
			name : document.getElementById('name').value,
			sql: editor.getValue(),
		};
		
		var params = document.querySelectorAll('.parameter');
		//var paramsStr = '';
		console.log(params);
		
		for (var i = 0; i < params.length; ++i) {
			var item = params[i];
			//console.log(item);
			reqData[item.name] = item.value;
			//paramsStr += '/'+item.value;
		}
		
		var request = $.ajax({
			url : queryOnUrl+"/QueryAny."+responseType,
			//url : "q/QueryAny"+paramsStr+"."+responseType,
			type : "POST",
			data: reqData,
			dataType : "html"
		});
		
		request.done(function(data) {
			//XXX option to handle different response types (html, json, csv, xml)?
			//'close' style:: position: relative, float: right?
			$("#queryResult").html("<input type='button' class='closebutton' onclick='closeResults()' value='X'/>");
			$("#queryResult").append(data);
			closeMessages();
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			console.log(jqXHR);
			errorMessage(jqXHR.responseText);
			closeResults();
			//alert("Request failed ["+textStatus+"]: "+jqXHR.responseText);
		});
	}
	//type: 'POST' - https://api.jquery.com/jQuery.ajax/

	function doValidate() {
		var request = $.ajax({
			url : queryOnUrl+"/ValidateAny",
			type : "POST",
			data : {
				schema : document.getElementById('schema').value,
				name : document.getElementById('name').value,
				sql: editor.getValue(),
			},
			dataType : "html"
		});
		
		request.done(function(data) {
			console.log('#params = '+data);
			//var container = document.getElementById('sqlparams');
			setParameters(data);
			infoMessage('query '+document.getElementById('name').value+' sucessfully validated');
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			console.log(jqXHR);
			errorMessage(jqXHR.responseText);
			closeResults();
			//alert("Request failed ["+textStatus+"]: "+jqXHR.responseText);
		});
	}
	
	function doSave() {
		var request = $.ajax({
			//url : processorUrl+"/SQLQueries",
			url : processorUrl+"/SQLQueries,queryon.processor.QOnQueries",
			type : "POST",
			data : {
				"sqldump.queries.addtomodel": "true",
				"sqldump.queries.runqueries": "false",
				"sqldump.queries.grabcolsinfofrommetadata": "true",
				"sqldump.queries": "q1",
				"sqldump.query.q1.schemaname": document.getElementById('schema').value,
				"sqldump.query.q1.name": document.getElementById('name').value,
				"sqldump.query.q1.sql": editor.getValue(),
				"sqldump.query.q1.remarks": document.getElementById('remarks').value,
				"queryon.qon-queries.action": "write",
				"queryon.qon-queries.querynames": document.getElementById('name').value,
			},
			dataType : "html"
		});

		request.done(function(data) {
			console.log(data);
			closeMessages();
			infoMessage('query '+document.getElementById('name').value+' sucessfully saved');
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			console.log(jqXHR);
			alert("Request failed ["+textStatus+"]: "+jqXHR.responseText);
		});
	}
	
	function setParameters(numparams) {
		var params = document.querySelectorAll('.parameter');
		//$("#sqlparams").html('');
		console.log('numparams: '+numparams+' ; params.length: '+params.length);
		if(numparams > params.length) {
			for(var i=params.length+1;i<=numparams;i++) {
				$("#sqlparams").append("<label class='parameter-label'>p"+i+": <input type='text' class='parameter' id='param"+i+"' name='p"+i+"'/></label>");
			}
		}
		else if(numparams < params.length) {
			for (var i = params.length; i > numparams; --i) {
				var item = params[i-1];
				console.log(item);
				item = item.parentNode;
				item.parentNode.removeChild(item);
			}
		}
	
	}
	
	function errorMessage(message) {
		// XXX encode 'message'?
		$('#messages').html("<div class='error'>"+message+"<input type='button' class='errorbutton' onclick=\"javascript:closeMessages()\" value='x' float='right'/></div>");
	}

	function infoMessage(message) {
		// XXX encode 'message'?
		$('#messages').html("<div class='info'>"+message+"<input type='button' class='infobutton' onclick=\"javascript:closeMessages()\" value='x' float='right'/></div>");
	}
	
	function closeMessages() {
		document.getElementById('messages').innerHTML = '';		
	}
	
	function closeResults() {
		document.getElementById("queryResult").innerHTML = "";
	}
</script>
<script type="text/javascript">
	$(document).jkey('f8',function(){
		console.log('f8 pressed: doValidate()');
		doValidate();
	});
	$(document).jkey('f9',function(){
		console.log('f9 pressed: doRun()');
		doRun();
	});
	$(document).jkey('f10',function(){
		console.log('f10 pressed: doSave()');
		doSave();
	});
</script>
</head>
<body>
<%!
SchemaModel model = null;
String schemaName = null;
String queryName = null;
String query = "";
String remarks = "";
int numOfParameters = 0;
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
			query = q.getQuery();
		}
		else {
			query = "select * from "+v.getFinalName(true);
		}
		
		schemaName = v.getSchemaName();
		remarks = v.getRemarks();
		if(v.getParameterCount()!=null) {
			numOfParameters = v.getParameterCount();
		}
	}
	else {
		Table t = DBIdentifiable.getDBIdentifiableBySchemaAndName(model.getTables(), schemaName, queryName);
		if(t != null) {
			query = "select * from "+t.getFinalName(true);
			schemaName = t.getSchemaName();
			remarks = t.getRemarks();
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
if(remarks==null) { remarks = ""; }
%>

<div class="container" id="objectid-container">
	<label>schema: <input type="text" id="schema" name="schema" value="<%= schemaName %>"/></label>
	<label>name: <input type="text" id="name" name="name" value="<%= queryName %>"/></label>
	<label>remarks: <input type="text" id="remarks" name="remarks" value="<%= remarks %>" size="40"/></label>
</div>

<div id="editor"><%= query %></div>

<div class="container">
	<div id="sqlparams">
	</div>
	
	<div id="button-cotntainer">
		<input type="button" value="validate" onclick="javascript:doValidate();">
		<input type="button" value="run" onclick="javascript:doRun();">
		<input type="button" value="save" onclick="javascript:doSave();">
	</div>
</div>

<div id="messages">
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
    
    setParameters(<%= numOfParameters %>);
</script>

</body>
