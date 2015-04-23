<%@page import="tbrugz.queryon.processor.QOnQueries"%>
<%@page import="org.apache.shiro.SecurityUtils"%>
<%@page import="org.apache.shiro.subject.Subject"%>
<%@page import="tbrugz.sqldump.datadump.DataDumpUtils"%>
<%@page import="tbrugz.sqldump.dbmodel.DBIdentifiable"%>
<%@page import="tbrugz.sqldump.dbmodel.Table"%>
<%@page import="tbrugz.sqldump.dbmodel.Query"%>
<%@page import="tbrugz.sqldump.dbmodel.View"%>
<%@page import="tbrugz.sqldump.dbmodel.SchemaModel"%>
<%@page import="tbrugz.queryon.QueryOn"%>
<%@page import="tbrugz.queryon.SchemaModelUtils"%><%
Subject currentUser = SecurityUtils.getSubject();
if(!currentUser.isPermitted("SELECT_ANY:SELECT_ANY")) {
	response.setStatus(HttpServletResponse.SC_FORBIDDEN);
	out.write("permission denied");
	return;
}
modelId = SchemaModelUtils.getModelId(request);
//System.out.println("modelId: "+modelId+" ; "+(modelId==null));
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
	</style>
	<script type="text/javascript" src="js/jquery-2.1.0.min.js"></script>
	<!-- see: https://github.com/oscargodson/jkey -->
	<script type="text/javascript" src="js/jquery.jkey.js"></script>
	<script type="text/javascript" src="js/http-post.js"></script>
	<script type="text/javascript" src="js/qon-util.js"></script>
<script type="text/javascript">
	var responseType = "htmlx";
	var queryOnUrl = 'q';
	var processorUrl = 'processor';
	var isQuerySaved = false;
	var modelId = <%= (modelId==null?"null":"'"+modelId+"'")%>;
	var rolesInfo = {};
	
	$.ajax({
		dataType: 'text',
		url: 'allroles.json',
		success: function(data) {
			rolesInfo = JSON.parse(data);
			//console.log(rolesInfo);
			refreshRolesInfo();
		}
	});

	function doRun() {
		var sqlString = editor.getSelectedText();
		if(!sqlString) { sqlString = editor.getValue(); }
		
		var reqData = {
			schema : document.getElementById('schema').value,
			name : document.getElementById('name').value,
			sql: sqlString,
		};
		if(modelId!=null) {
			reqData.model = document.getElementById('model').value; // or modelId
		}
		
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
		
		btnActionStart('btnRun');
		request.done(function(data) {
			btnActionStop('btnRun');
			var completedTimeMilis = Date.now();
			//XXX option to handle different response types (html, json, csv, xml)?
			//'close' style:: position: relative, float: right?
			$("#queryResult").html("<input type='button' class='closebutton' onclick='closeResults()' value='X' style='position: fixed;'/>");
			$("#queryResult").append(data);
			showRunStatusInfo('queryResult', 'status-container', startTimeMilis, completedTimeMilis);
			closeMessages('messages');
			updateUI();
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			btnActionStop('btnRun');
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

		var reqData = {
			schema : document.getElementById('schema').value,
			name : document.getElementById('name').value,
			sql: sqlString
		}
		if(modelId!=null) {
			reqData.model = document.getElementById('model').value; // or modelId
		}
		
		var request = $.ajax({
			url : queryOnUrl+"/ValidateAny",
			type : "POST",
			data : reqData,
			dataType : "html"
		});
		
		btnActionStart('btnValidate');
		request.done(function(data) {
			btnActionStop('btnValidate');
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
			btnActionStop('btnValidate');
			console.log(jqXHR);
			errorMessage(jqXHR.responseText);
			closeResults();
			//alert("Request failed ["+textStatus+"]: "+jqXHR.responseText);
		});
	}
	
	function doSave() {
		var schema = document.getElementById('schema').value;
		var name = document.getElementById('name').value;
		
		var reqData = {
			/*"sqldump.queries.addtomodel": "true",
			"sqldump.queries.runqueries": "false",
			"sqldump.queries.grabcolsinfofrommetadata": "true",*/
			"sqldump.queries": "q1",
			"sqldump.query.q1.schemaname": schema,
			"sqldump.query.q1.name": name,
			"sqldump.query.q1.sql": editor.getValue(),
			"sqldump.query.q1.remarks": document.getElementById('remarks').value,
			"sqldump.query.q1.roles": document.getElementById('roles').value,
			//"model": document.getElementById('model').value,
			"queryon.qon-queries.action": "write",
			"queryon.qon-queries.querynames": name,
			"queryon.qon-queries.limit.insert.exact": isQuerySaved?0:1,
			"queryon.qon-queries.limit.update.exact": isQuerySaved?1:0,
		}
		if(modelId!=null) {
			reqData.model = document.getElementById('model').value; // or modelId
		}
		
		var request = $.ajax({
			url : processorUrl+"/queryon.processor.QOnQueries",
			type : "POST",
			data : reqData,
			dataType : "html"
		});

		btnActionStart('btnSave');
		request.done(function(data) {
			btnActionStop('btnSave');
			console.log(data);
			closeMessages('messages');
			infoMessage('query '+name+' sucessfully saved');
			//XXX: reload query after save?
			validateEditComponents(true);
			history.replaceState(null, null, "?name="+name+(schema!=''?"&schema="+schema:""));
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			btnActionStop('btnSave');
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
			validateEditComponents(false);
			history.replaceState(null, null, "qon-editor.jsp");
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			console.log(jqXHR);
			errorMessage('error removing query: '+jqXHR.responseText);
		});
	}
	
	function doDownload() {
		var sqlString = editor.getSelectedText();
		if(!sqlString) { sqlString = editor.getValue(); }
		
		var reqData = {
			schema : document.getElementById('schema').value,
			name : document.getElementById('name').value,
			sql: sqlString,
		};
		
		var params = document.querySelectorAll('.parameter');
		console.log(params);
		
		for (var i = 0; i < params.length; ++i) {
			var item = params[i];
			reqData[item.name] = item.value;
		}
		
		//var startTimeMilis = Date.now();
		
		btnActionStart('btnDownload');
		post(queryOnUrl+"/QueryAny.csv", reqData, function () {
			//console.log('downloading...'); 
			btnActionStop('btnDownload');
		});
		
		//var completedTimeMilis = Date.now();
		//showRunStatusInfo('queryResult', 'status-container', startTimeMilis, completedTimeMilis);
		
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
	
	function validateEditComponents(saved) {
		var qname = document.getElementById('name').value;
		//var removebutton = document.getElementById('removebutton');
		//var reloadbutton = document.getElementById('url-reload');
		var container = document.getElementById('actions-container');
		
		if(saved) { // }(qname != null) && (qname != '')) {
			document.getElementById('schema').disabled = true;
			document.getElementById('name').disabled = true;
			container.style.display = 'initial';
			isQuerySaved = true;
		}
		else {
			document.getElementById('schema').disabled = false;
			document.getElementById('name').disabled = false;
			container.style.display = 'none';
			isQuerySaved = false;
		}
	}
	
	function errorMessage(message) {
		// XXX encode 'message'?
		$('#messages').html("<div class='error message'>"+message+"<input type='button' class='errorbutton' onclick=\"javascript:closeMessages('messages')\" value='x' float='right'/></div>");
	}

	function infoMessage(message) {
		// XXX encode 'message'?
		$('#messages').html("<div class='info message'>"+message+"<input type='button' class='infobutton' onclick=\"javascript:closeMessages('messages')\" value='x' float='right'/></div>");
	}
	
	function closeMessages(elemId) {
		document.getElementById(elemId).innerHTML = '';
	}
	
	function closeResults() {
		document.getElementById("queryResult").innerHTML = "";
	}
	
	function makeHrefs() {
		var schema = document.getElementById('schema').value;
		var name = document.getElementById('name').value;

		var urlr = document.getElementById("url-reload");
		urlr.href = "?name="+name+(schema!=''?"&schema="+schema:"");
		
		var numparameters = document.getElementById("sqlparams").children.length;
		var urlpl = document.getElementById("url-permalink");
		urlpl.href = queryOnUrl+"/"+name;
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
		messages.innerHTML = 'rows = '+numOfRows+' ; time = '+(completedTimeMilis-startTimeMilis)+'ms '
			+"<input type='button' class='statusbutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>";
	}
	
	function updateUI() {
		document.getElementById('queryResult').style.top = document.getElementById('spec').offsetHeight + 'px';
	}
	
	function refreshRolesInfo() {
		var r = document.getElementById('roles');
		var rl = document.getElementById('rolesLabel');
		
		if(! rolesInfo.useRoles) {
			r.disabled = true;
			r.readOnly = true;
			rl.style.display='none';
			return;
		}
		
		rl.style.display='initial';
		if(rolesInfo.roles && rolesInfo.roles.length>0) {
			console.log('multi-roles');
			refreshRolesCount();
			var rc = document.getElementById('rolesCount');
			var rolesBtn = document.getElementById('rolesBtn');
			r.style.display='none';
			rc.style.display='initial';
			rolesBtn.style.display='initial';
			var rtext = document.getElementById('rolesLabelText');
			rtext.innerHTML = 'roles#';
		}
	}

	function refreshRolesCount() {
		var r = document.getElementById('roles');
		var rc = document.getElementById('rolesCount');
		//console.log(rc.innerHTML);
		if(!r.value) {
			rc.innerHTML = '(public)';
		}
		else {
			rc.innerHTML = r.value.split('|').length;
		}
	}
	
	function showRolesDialog() {
		var rd=document.getElementById('rolesListDialogContainer');
		rd.style.display='block';
		
		var r = document.getElementById('roles');
		var rolesValues = r.value.split('|');
		//console.log(rolesInfo);
		
		var rdc=document.getElementById('rolesListDialogContent');
		for(var i=0;i<rolesInfo.roles.length;i++) {
			rdc.innerHTML += '<div><input name="roleName" class="rolesCheck" type="checkbox" value="'+rolesInfo.roles[i]+'"'
				+(rolesValues.indexOf(rolesInfo.roles[i])>=0?" checked":"")
				+'/>'+rolesInfo.roles[i]+'</div>\n';
		}
	}
	
	function updateRoles() {
		var cboxes = document.getElementsByName('roleName');
		var len = cboxes.length;
		var rolesStr = '';
		for (var i=0; i<len; i++) {
			if(cboxes[i].checked) {
				if(rolesStr) { rolesStr += '|'; }
				rolesStr += cboxes[i].value;
			}
		}
		var r=document.getElementById('roles');
		r.value = rolesStr;
		refreshRolesCount();
		
		closeRolesDialog();
	}
	
	function closeRolesDialog() {
		var rcont=document.getElementById('rolesListDialogContainer');
		rcont.style.display='none';

		var rdc=document.getElementById('rolesListDialogContent');
		rdc.innerHTML = '';
	}
	
	window.addEventListener('load', function() {
		if(modelId==null) {
			//document.getElementById('modelLabel').style.display = 'none';
			var ml = document.getElementById('modelLabel');
			ml.innerHTML = '';
			ml.style.display = 'none';
		}
		
	});
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
<body onload="updateUI();">
<%!
SchemaModel model = null;
String modelId = null;
String schemaName = null;
String queryName = null;
String query = "";
String remarks = "";
String roles = "";
int numOfParameters = 0;
boolean queryLoaded = false;
%>

<%
model = SchemaModelUtils.getModel(application, modelId);
schemaName = request.getParameter("schema");
queryName = request.getParameter("name");
query = "";
remarks = "";
roles = "";

numOfParameters = 0;
queryLoaded = false;

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
		roles = QOnQueries.getGrantsStr(v.getGrants());
		queryLoaded = true;
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
		else {
			query = "-- query not found";
		}
	}
}
if(query==null || query.equals("")) {
	query = "select * from table";
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
	<label id="rolesLabel">
		<span id="rolesLabelText">roles:</span>
		<input type="text" id="roles" name="roles" value="<%= roles %>"/>
		<span id="rolesCount"></span>
		<input type="button" id="rolesBtn" value="+/- roles" onclick="showRolesDialog()"/>
	</label>
	<label id="modelLabel">model: <input type="text" id="model" name="model" readonly="readonly" value="<%= modelId %>"/></label>
	
	<span id="xtra-actions">
	<span id="actions-container">
		<a id="url-reload" href="" title="Reload query">reload</a>
		<a id="url-permalink" href="" target="_blank">permalink</a>
		<a id="removebutton" href="#" onclick="if(window.confirm('Do you really want to remove query '+document.getElementById('name').value+'?')){doRemove();}" title="Remove Query">remove</a>
	</span>
	<span id="username"><%= currentUser.getPrincipal() %></span>
	</span>
</div>

<div id="editor"><%= DataDumpUtils.xmlEscapeText( query ) %></div>

<div class="container">
	<div id="sqlparams">
	</div>
	
	<div id="button-container">
		<input type="button" id="btnValidate" value="validate" onclick="javascript:doValidate();" title="Validate Query (F8)">
		<input type="button" id="btnRun" value="run" onclick="javascript:doRun();" title="Run Query (F9)">
		<input type="button" id="btnSave" value="save" onclick="javascript:doSave();" title="Save Query (F10)">
		<input type="button" id="btnDownload" value="download" onclick="doDownload();" title="Download CSV" style="float: right;"/>
	</div>
	
</div>

<div id="messages"></div>
</div>

<div id="rolesListDialogContainer">
	<div id="rolesListDialog">
		<form id="rolesForm">
		<div id="rolesListDialogContent"></div>
		<div id="rolesListDialogBtns">
			<input type="button" value="change roles" onclick="updateRoles();"/>
			<input type="button" value="close" onclick="closeRolesDialog();"/>
		</div>
		</form>
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
	validateEditComponents(<%= queryLoaded %>);
</script>

</body>
