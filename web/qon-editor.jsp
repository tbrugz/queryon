<%@page import="org.apache.shiro.SecurityUtils"%>
<%@page import="org.apache.shiro.subject.Subject"%>
<%@page import="tbrugz.sqldump.datadump.DataDumpUtils"%>
<%@page import="tbrugz.sqldump.dbmodel.Table"%>
<%@page import="tbrugz.sqldump.dbmodel.Query"%>
<%@page import="tbrugz.sqldump.dbmodel.View"%>
<%@page import="tbrugz.queryon.QueryOn"%>
<%@page import="tbrugz.sqldump.dbmodel.SchemaModel"%>
<%@page import="tbrugz.sqldump.dbmodel.DBIdentifiable"%><%
Subject currentUser = SecurityUtils.getSubject();
if(!currentUser.isPermitted("SELECT_ANY:SELECT_ANY")) {
	response.setStatus(HttpServletResponse.SC_FORBIDDEN);
	out.write("permission denied");
	return;
}
%>
<!DOCTYPE html>
<html>
<head>
	<title>QueryOn - query editor</title>
	<link href="css/queryon.css" rel="stylesheet">
	<link href="css/qon-editor.css" rel="stylesheet">
	<meta name="viewport" content="width=device-width, initial-scale=1.0">
	<meta name="description" content="">
	<meta name="author" content="">
	<link rel="icon" type="image/png" href="favicon.png" />
	<style type="text/css">
	#objectid-container {
	}
	#editor {
	}
	#spec {
		/*position: fixed;
		top: 0px;
		left: 0px;
		right: 0px;*/
	}
	#button-container input[type=button] {
		border: 1px solid #999;
		margin: 2px;
	}
	</style>
	<script type="text/javascript" src="js/jquery-2.1.0.min.js"></script>
	<!-- see: https://github.com/oscargodson/jkey -->
	<script type="text/javascript" src="js/jquery.jkey.js"></script>
<script type="text/javascript">
	var responseType = "htmlx";
	var queryOnUrl = 'q';
	var processorUrl = 'processor';
	var isQuerySaved = false;

	function doRun() {
		var sqlString = editor.getSelectedText();
		if(!sqlString) { sqlString = editor.getValue(); }
		
		var reqData = {
			schema : document.getElementById('schema').value,
			name : document.getElementById('name').value,
			sql: sqlString,
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
		
		var startTimeMilis = Date.now();
		
		var request = $.ajax({
			url : queryOnUrl+"/QueryAny."+responseType,
			//url : "q/QueryAny"+paramsStr+"."+responseType,
			type : "POST",
			data: reqData,
			dataType : "html"
		});
		
		request.done(function(data) {
			var completedTimeMilis = Date.now();
			//XXX option to handle different response types (html, json, csv, xml)?
			//'close' style:: position: relative, float: right?
			$("#queryResult").html("<input type='button' class='closebutton' onclick='closeResults()' value='X'/>");
			$("#queryResult").append(data);
			showRunStatusInfo('queryResult', 'status-container', startTimeMilis, completedTimeMilis);
			closeMessages('messages');
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
		var sqlString = editor.getSelectedText();
		var usingSelected = true;
		if(!sqlString) {
			sqlString = editor.getValue();
			usingSelected = false;
		}
		
		var request = $.ajax({
			url : queryOnUrl+"/ValidateAny",
			type : "POST",
			data : {
				schema : document.getElementById('schema').value,
				name : document.getElementById('name').value,
				sql: sqlString,
			},
			dataType : "html"
		});
		
		request.done(function(data) {
			console.log('#params = '+data);
			//var container = document.getElementById('sqlparams');
			setParameters(data);
			makeHrefs();
			if(usingSelected) {
				infoMessage('selected text from query '+document.getElementById('name').value+' sucessfully validated');
			}
			else {
				infoMessage('query '+document.getElementById('name').value+' sucessfully validated');
			}
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
				"queryon.qon-queries.limit.insert.exact": isQuerySaved?0:1,
				"queryon.qon-queries.limit.update.exact": isQuerySaved?1:0,
			},
			dataType : "html"
		});

		request.done(function(data) {
			console.log(data);
			closeMessages('messages');
			infoMessage('query '+document.getElementById('name').value+' sucessfully saved');
			//XXX: reload query after save?
			validateEditComponents();
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			console.log(jqXHR);
			errorMessage('error saving query: '+jqXHR.responseText);
			//alert("Request failed ["+textStatus+"]: "+jqXHR.responseText);
		});
	}

	function doRemove() {
		var request = $.ajax({
			url : processorUrl+"/queryon.processor.QOnQueries",
			type : "POST",
			data : {
				"queryon.qon-queries.action": "remove",
				"queryon.qon-queries.querynames": document.getElementById('name').value,
			},
			dataType : "html"
		});

		request.done(function(data) {
			console.log(data);
			closeMessages('messages');
			infoMessage('query '+document.getElementById('name').value+' sucessfully removed');
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			console.log(jqXHR);
			errorMessage('error removing query: '+jqXHR.responseText);
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
	
	function validateEditComponents() {
		var qname = document.getElementById('name').value;
		//var removebutton = document.getElementById('removebutton');
		//var reloadbutton = document.getElementById('url-reload');
		var container = document.getElementById('actions-container');
		
		if((qname != null) && (qname != '')) {
			document.getElementById('schema').disabled = true;
			document.getElementById('name').disabled = true;
			container.style.display = 'inline';
			isQuerySaved = true;
		}
		else {
			container.style.display = 'none';
			isQuerySaved = false;
		}
	}
	
	function errorMessage(message) {
		// XXX encode 'message'?
		$('#messages').html("<div class='error'>"+message+"<input type='button' class='errorbutton' onclick=\"javascript:closeMessages('messages')\" value='x' float='right'/></div>");
	}

	function infoMessage(message) {
		// XXX encode 'message'?
		$('#messages').html("<div class='info'>"+message+"<input type='button' class='infobutton' onclick=\"javascript:closeMessages('messages')\" value='x' float='right'/></div>");
	}
	
	function closeMessages(elemId) {
		document.getElementById(elemId).innerHTML = '';
	}
	
	function closeResults() {
		document.getElementById("queryResult").innerHTML = "";
	}
	
	function makeHrefs() {
		var urlr = document.getElementById("url-reload");
		urlr.href = "?name="+document.getElementById("name").value;
		
		var numparameters = document.getElementById("sqlparams").children.length;
		var urlpl = document.getElementById("url-permalink");
		urlpl.href = queryOnUrl+"/"+document.getElementById("name").value;
		for(var i=0;i<numparameters;i++) {
			urlpl.href += "/-";
		}
		//urlr.href = "?name="+document.getElementById("name").value;
	}
	
	function showRunStatusInfo(containerId, messagesId, startTimeMilis, completedTimeMilis) {
		//var renderedTimeMilis = Date.now();
		
		var content = document.getElementById(containerId);
		var messages = document.getElementById(messagesId);
		
		var numOfRows = content.getElementsByTagName('tr').length-1; // 1st is header
		//messages.innerHTML = 'rows = '+numOfRows+' ; time in millis: server = '+(completedTimeMilis-startTimeMilis)+' ; render = '+(renderedTimeMilis-completedTimeMilis)
		messages.innerHTML = 'rows = '+numOfRows+' ; time: = '+(completedTimeMilis-startTimeMilis)+'ms '
			+"<input type='button' class='statusbutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>";
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
query = "";
remarks = "";
numOfParameters = 0;
if(queryName!=null) {
	View v = DBIdentifiable.getDBIdentifiableBySchemaAndName(model.getViews(), schemaName, queryName);
	if(v == null) {
		v = DBIdentifiable.getDBIdentifiableByName(model.getViews(), queryName);
	}
	if(v != null) {
		if(v instanceof Query) {
			//XXX: check permission for q.getQuery()? add new action on QueryOn servlet? 
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
	query = "select * from <table>";
}
if(schemaName==null) { schemaName = ""; }
if(queryName==null) { queryName = ""; }
if(remarks==null) { remarks = ""; }
//System.out.println("qon-editor.jsp: name: "+queryName+" ; query: "+query);
%>

<div id="spec">
<div class="container" id="objectid-container">
	<label>schema: <input type="text" id="schema" name="schema" value="<%= schemaName %>" onchange="makeHrefs()"/></label>
	<label>name: <input type="text" id="name" name="name" value="<%= queryName %>" onchange="makeHrefs()"/></label>
	<label>remarks: <input type="text" id="remarks" name="remarks" value="<%= remarks %>" size="60"/></label>
	<div id="actions-container">
		<a id="url-reload" href="" title="Reload query">reload</a>
		<a id="url-permalink" href="" target="_blank">permalink</a>
		<a id="removebutton" href="#" onclick="if(window.confirm('Do you really want to remove query '+document.getElementById('name').value+'?')){doRemove();}" title="Remove Query">remove</a>
	</div>
</div>

<div id="editor"><%= DataDumpUtils.xmlEscapeText( query ) %></div>

<div class="container">
	<div id="sqlparams">
	</div>
	
	<div id="button-container">
		<input type="button" value="validate" onclick="javascript:doValidate();" title="Validate Query (F8)">
		<input type="button" value="run" onclick="javascript:doRun();" title="Run Query (F9)">
		<input type="button" value="save" onclick="javascript:doSave();" title="Save Query (F10)">
	</div>
</div>

<div id="messages">
</div>
</div>

<div class="container">
	<div id="queryResult"></div>
</div>

<div id="status-container" class="status">
</div>

<!-- see http://ace.c9.io/ -->
<!-- style? see http://ace.c9.io/build/kitchen-sink.html -->
<script src="js/ace/ace.js" type="text/javascript" charset="utf-8"></script>
<script>
	var editor = ace.edit("editor");
	editor.setTheme("ace/theme/twilight"); //monokai,ambiance,twilight,,eclipse,github ?
	editor.getSession().setMode("ace/mode/sql");
	editor.getSelectedText = function() {
		//see: https://groups.google.com/forum/#!topic/ace-discuss/kxRy5g_Je2o
		return this.getSession().getTextRange(this.getSelectionRange());
	};
	
	setParameters(<%= numOfParameters %>);
</script>
<script type="text/javascript">
	makeHrefs();
	validateEditComponents();
</script>

</body>
