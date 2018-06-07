<%@page import="tbrugz.queryon.util.MiscUtils"%>
<%@page import="tbrugz.queryon.util.WebUtils"%>
<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@page import="java.util.*"%>
<%@page import="tbrugz.queryon.util.ShiroUtils"%>
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
<%@page import="tbrugz.queryon.util.SchemaModelUtils"%><%
Properties prop = (Properties) application.getAttribute(QueryOn.ATTR_PROP);
Subject currentUser = ShiroUtils.getSubject(prop, request);
if(!currentUser.isPermitted("SELECT_ANY")) {
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
	<script src="js/settings.js"></script>
	<script type="text/javascript" src="js/table.js"></script>
<script type="text/javascript">
	var responseType = "htmlx";
	var queryOnUrl = 'q';
	//var processorUrl = 'processor';
	var qonUrl = 'q/QON_QUERIES';
	var isQuerySaved = false;
	var modelId = <%= (modelId==null?"null":"'"+modelId+"'")%>;
	var rolesInfo = {};
	var queryAltered = false;
	var lastExecutedQuery = null;
	var utf8par = "utf8=✓";
	
	function onLoad() {
		$.ajax({
			dataType: 'text',
			url: 'allroles.json',
			success: function(data) {
				rolesInfo = JSON.parse(data);
				//console.log(rolesInfo);
				
				var qname = byId('name').value;
				if(!qname) {
					// new query
					var r = byId('roles');
					if(rolesInfo.defaultRolesForNewQuery) {
						var rolesStr = rolesInfo.defaultRolesForNewQuery.join("|");
						if(rolesStr) {
							console.log("setting default roles:", rolesStr)
							r.value = rolesStr;
						}
					}
				}
				
				refreshRolesInfo();
			}
		});
		loadSettings(function() {
			var table = settings['queryon.qon-queries.table'];
			if(table) {
				qonUrl = 'q/'+table;
			}
			console.log("qonUrl: ", qonUrl);
			/*var modelId = getParameterByName('model', location.search);
			if(modelId) {
				byId('modelLabel').style.display = 'inline-block';
				byId('model').value = modelId;
			}*/
			//updateUI();
		});
		updateUI();
	}

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
		console.log("params:", params);
		
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
			byId('queryResult').innerHTML = "<input type='button' class='closebutton' onclick='closeResults()' value='X' style='position: fixed;'/>"+data;
			showRunStatusInfo('queryResult', 'status-container', startTimeMilis, completedTimeMilis);
			
			closeMessages('messages');
			lastExecutedQuery = reqData;
			doTableOnLoad();
			updateUI();
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			btnActionStop('btnRun');
			console.log(jqXHR);
			errorMessage(jqXHR.responseText
					+(jqXHR.status==403?" (invalid session?)":"")
					);
			closeResults();
			updateUI();
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
		request.done(function(data, textStatus, jqXHR) {
			btnActionStop('btnValidate');
			var paramCount = jqXHR.getResponseHeader('X-Validate-ParameterCount');
			console.log('#params = '+paramCount);
			//var container = document.getElementById('sqlparams');
			setParameters(paramCount);
			makeHrefs();
			
			byId('queryResult').innerHTML = "<input type='button' class='closebutton' onclick='closeResults()' value='X' style='position: fixed;'/>"+data;
			
			if(usingSelected) {
				infoMessage('selected text from query <b>'+document.getElementById('name').value+'</b> successfully validated');
			}
			else {
				infoMessage('query <b>'+document.getElementById('name').value+'</b> successfully validated');
			}
			updateUI();
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			btnActionStop('btnValidate');
			console.log(jqXHR);
			errorMessage(jqXHR.responseText
					+(jqXHR.status==403?" (invalid session?)":"")
					);
			closeResults();
			//alert("Request failed ["+textStatus+"]: "+jqXHR.responseText);
			updateUI();
		});
	}

	function doExplain() {
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
		
		var params = document.querySelectorAll('.parameter');
		console.log(params);
		for (var i = 0; i < params.length; ++i) {
			var item = params[i];
			reqData[item.name] = item.value;
		}
		
		var request = $.ajax({
			url : queryOnUrl+"/ExplainAny",
			type : "POST",
			data : reqData,
			dataType : "html"
		});
		
		btnActionStart('btnExplain');
		request.done(function(data, textStatus, jqXHR) {
			btnActionStop('btnExplain');
			//var paramCount = jqXHR.getResponseHeader('X-Validate-ParameterCount');
			//console.log('#params = '+paramCount);
			//var container = document.getElementById('sqlparams');
			//setParameters(paramCount);
			//makeHrefs();
			
			byId('queryResult').innerHTML = "<input type='button' class='closebutton' onclick='closeResults()' value='X' style='position: fixed;'/><div class='explain'>"+data+"</div>";
			
			if(usingSelected) {
				infoMessage('selected text from query <b>'+document.getElementById('name').value+'</b> successfully explained');
			}
			else {
				infoMessage('query <b>'+document.getElementById('name').value+'</b> successfully explained');
			}
			updateUI();
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			btnActionStop('btnExplain');
			console.log(jqXHR);
			errorMessage(jqXHR.responseText
					+(jqXHR.status==403?" (invalid session?)":"")
					);
			closeResults();
			//alert("Request failed ["+textStatus+"]: "+jqXHR.responseText);
			updateUI();
		});
	}
	
	function doSave() {
		var schema = document.getElementById('schema').value;
		var name = document.getElementById('name').value;
		var remarks = document.getElementById('remarks').value;
		var roles = document.getElementById('roles').value;
		var username = byId('username').innerHTML;
		var now = new Date();
		var optimisticlock = byId('optimisticlock').value;
		var version_seq = isInteger(optimisticlock)? 1+parseInt(optimisticlock) : 1;

		if(name==null || name=="") {
			errorMessage('query <b>name</b> cannot be null...');
			updateUI();
			return;
		}
		
		var reqData = {
			"v:QUERY": editor.getValue(),
			"v:REMARKS": remarks,
			"v:ROLES_FILTER": roles,
			//"queryon.qon-queries.limit.insert.exact": isQuerySaved?0:1,
			//"queryon.qon-queries.limit.update.exact": isQuerySaved?1:0,
			"optimisticlock": encodeURIComponent(optimisticlock),
			"v:VERSION_SEQ": encodeURIComponent(version_seq)
		}
		if(modelId!=null) {
			reqData.model = document.getElementById('model').value; // or modelId
		}
		
		var method = "POST"; //post: insert ; patch: update
		if(isQuerySaved) { // update
			//saveUrl += "p1="+encodeURIComponent(id);
			//method = "PATCH";
			reqData["p1"] = name;
			reqData["_method"] = "PATCH";
			reqData["v:SCHEMA_NAME"] = schema, //needed by QOnQueries
			reqData["v:NAME"] = name, //needed by QOnQueries
			reqData["v:UPDATED_BY"] = encodeURIComponent(username);
			reqData["v:UPDATED_AT"] = encodeURIComponent(now.toISOString());
		}
		else {
			reqData["v:SCHEMA_NAME"] = schema,
			reqData["v:NAME"] = name,
			reqData["v:CREATED_BY"] = encodeURIComponent(username);
			reqData["v:CREATED_AT"] = encodeURIComponent(now.toISOString());
		}
		
		var request = $.ajax({
			url : qonUrl,
			type : method,
			data : reqData,
			dataType : "html"
		});

		//console.log("optimisticlock", optimisticlock, "version_seq", version_seq);
		btnActionStart('btnSave');
		request.done(function(data) {
			btnActionStop('btnSave');
			console.log(data);
			closeMessages('messages');
			byId('optimisticlock').value = version_seq;
			infoMessage('query <b>'+name+'</b> successfully saved');
			//XXX: reload query after save?
			validateEditComponents(true);
			history.replaceState(null, null, "?" + (schema!=''?"schema="+schema+"&":"") + "name=" + name + (modelId!=null?"&model="+modelId:"") + ("&" + utf8par) );
			queryAltered = false;
			updateUI();
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			btnActionStop('btnSave');
			console.log(jqXHR);
			errorMessage('error saving query: '+jqXHR.responseText
					+(jqXHR.status==403?" (invalid session?)":"")
					);
			//alert("Request failed ["+textStatus+"]: "+jqXHR.responseText);
			updateUI();
		});
	}

	function doRemove() {
		var data = {
			"_method": "DELETE",
			"p1": document.getElementById('name').value
			//"queryon.qon-queries.action": "remove",
			//"queryon.qon-queries.querynames": document.getElementById('name').value,
		};
		if(modelId!=null) {
			data["model"] = modelId;
		}
		
		var request = $.ajax({
			url : qonUrl,
			type : "POST",
			data : data,
			dataType : "html"
		});

		request.done(function(data) {
			console.log(data);
			closeMessages('messages');
			infoMessage('query <b>'+document.getElementById('name').value+'</b> successfully removed');
			validateEditComponents(false);
			history.replaceState(null, null, "qon-editor.jsp" +(modelId!=null?"?model="+modelId:""));
		});

		request.fail(function(jqXHR, textStatus, errorThrown) {
			console.log(jqXHR);
			errorMessage('error removing query: '+jqXHR.responseText
					+(jqXHR.status==403?" (invalid session?)":"")
					);
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
		$('#messages').html("<div class='error message'>"+message+"<input type='button' class='closebutton' onclick=\"javascript:closeMessages('messages')\" value='x' float='right'/></div>");
	}

	function infoMessage(message) {
		// XXX encode 'message'?
		$('#messages').html("<div class='info message'>"+message+"<input type='button' class='closebutton' onclick=\"javascript:closeMessages('messages')\" value='x' float='right'/></div>");
	}
	
	function closeMessages(elemId) {
		document.getElementById(elemId).innerHTML = '';
		updateUI();
	}
	
	function closeResults() {
		document.getElementById("queryResult").innerHTML = "";
		updateUI();
	}
	
	function onTextFieldChange() {
		queryAltered = true;
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
		document.getElementById('resultContainer').style.top = (document.getElementById('spec').offsetHeight - 2) + 'px';
		if(editor) { editor.resize(); }
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
			//console.log('multi-roles');
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
		rdc.innerHTML = '';
		for(var i=0;i<rolesInfo.roles.length;i++) {
			var checked = false;
			var indexOf = rolesValues.indexOf(rolesInfo.roles[i]);
			if(indexOf >= 0) {
				checked = true;
				rolesValues.splice(indexOf, 1);
			}
			rdc.innerHTML += '<div><input name="roleName" class="rolesCheck" type="checkbox" value="'+rolesInfo.roles[i]+'"'
				+(checked?" checked":"")
				+'/>'+rolesInfo.roles[i]+'</div>\n';
		}
		for(var i=0;i<rolesValues.length;i++) {
			var role = rolesValues[i];
			if(role) {
				rdc.innerHTML += '<div class="disabled"><input name="roleName" class="rolesCheck" type="checkbox" disabled="true" value="'+role+'"'
					+'/>'+role+'</div>\n';
			}
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

		//var rdc=document.getElementById('rolesListDialogContent');
		//rdc.innerHTML = '';
	}
	
	// http://stackoverflow.com/questions/2229942/how-to-block-users-from-closing-a-window-in-javascript
	// https://developer.mozilla.org/en-US/docs/Web/API/WindowEventHandlers/onbeforeunload
	function confirmExit(event) {
		if (queryAltered) {
			var dialogText = "Current query has been altered. Are you sure you want to close?";
			event.returnValue = dialogText;
			return dialogText;
		}
	}
	
	function downloadBlob(qs) {
		var params = document.querySelectorAll('.parameter');
		
		for (var i = 0; i < params.length; ++i) {
			var item = params[i];
			lastExecutedQuery[item.name] = item.value;
		}
		console.log("downloadBlob", qs, lastExecutedQuery, params);
		
		btnActionStart('btnDownload');
		post(queryOnUrl+"/QueryAny?"+qs, lastExecutedQuery, function () {
			console.log('blob-downloading...'); 
			btnActionStop('btnDownload');
		}, "_blank");
	}
	
	window.addEventListener('load', function() {
		if(modelId==null) {
			//document.getElementById('modelLabel').style.display = 'none';
			var ml = document.getElementById('modelLabel');
			ml.innerHTML = '';
			ml.style.display = 'none';
		}
		
	});
	
	window.addEventListener("beforeunload", confirmExit);
	
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
<body onload="onLoad();">
<%!
SchemaModel model = null;
String modelId = null;
String schemaName = null;
String queryName = null;
String query = "";
String remarks = "";
String roles = "";
String versionSeq = null;
int numOfParameters = 0;
boolean queryLoaded = false;
%>

<%
model = SchemaModelUtils.getModel(application, modelId);
boolean convert2utf8 = WebUtils.convertParametersToUtf8(request);
schemaName = request.getParameter("schema");
queryName = request.getParameter("name");
query = "";
remarks = "";
roles = "";

if(convert2utf8) {
	schemaName = MiscUtils.latin1ToUtf8(schemaName);
	queryName = MiscUtils.latin1ToUtf8(queryName);
}

numOfParameters = 0;
queryLoaded = false;

if(queryName!=null) {
	Map<String, String> qmap = QOnQueries.readQueryFromDatabase(prop, modelId, schemaName, queryName);
	if(qmap!=null) {
		schemaName = qmap.get("schema_name");
		query = qmap.get("query");
		remarks = qmap.get("remarks");
		roles = qmap.get("roles_filter");
		if(roles==null) { roles = ""; }
		versionSeq = qmap.get("version_seq");
		queryLoaded = true;
	}
	else {
		query = "-- query not found";
	}
	/*
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
			query = "select * from "+v.getFinalQualifiedName();
		}
		
		schemaName = v.getSchemaName();
		remarks = v.getRemarks();
		roles = QOnQueries.getGrantsStr(v.getGrants());
		if(roles==null) { roles = ""; }
		queryLoaded = true;
		if(v.getParameterCount()!=null) {
			numOfParameters = v.getParameterCount();
		}
	}
	else {
		Table t = DBIdentifiable.getDBIdentifiableBySchemaAndName(model.getTables(), schemaName, queryName);
		if(t != null) {
			query = "select * from "+t.getFinalQualifiedName();
			schemaName = t.getSchemaName();
			remarks = t.getRemarks();
		}
		else {
			query = "-- query not found";
		}
	}
	*/
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
	<span id="logo">Q<span style="color: #ff8a47;">On</span> <code>Editor</code></span>
	<label>schema: <input type="text" id="schema" name="schema" value="<%= schemaName %>" onchange="onTextFieldChange();makeHrefs()"/></label>
	<label>name: <input type="text" id="name" name="name" value="<%= queryName %>" onchange="onTextFieldChange();makeHrefs()"/></label>
	<label>remarks: <input type="text" id="remarks" name="remarks" value="<%= remarks %>" size="60" onchange="onTextFieldChange();"/></label>
	<label id="rolesLabel">
		<span id="rolesLabelText">roles:</span>
		<input type="text" id="roles" name="roles" value="<%= roles %>"/>
		<span id="rolesCount" title="# of current allowed roles"></span>
		<input type="button" id="rolesBtn" value="+/- roles" title="Add/Remove roles" onclick="showRolesDialog()"/>
	</label>
	<label id="modelLabel">model: <input type="text" id="model" name="model" readonly="readonly" value="<%= modelId %>"/></label>
	
	<span id="xtra-actions">
	<span id="actions-container">
		<a id="url-reload" href="" title="Reload query">reload</a>
		<a id="url-permalink" href="" target="_blank">permalink</a>
		<a id="removebutton" href="#" onclick="if(window.confirm('Do you really want to remove query '+document.getElementById('name').value+'?')){doRemove();}" title="Remove Query">remove</a>
	</span>
	<span id="help"><a href="reader.html#doc/queries.md" title="Editor's help" target="_blank">?</a></span>
	<span id="username"><%= currentUser.getPrincipal() %></span>
	</span>
</div>

<div id="editor"><%= DataDumpUtils.xmlEscapeText( query ) %></div>

<div class="container">
	<div id="sqlparams">
	</div>
	
	<input type="hidden" id="optimisticlock" value="<%= versionSeq!=null?String.valueOf(versionSeq):"" %>" />
	
	<div id="button-container">
		<input type="button" id="btnValidate" value="validate" onclick="javascript:doValidate();" title="Validate Query (F8)">
		<input type="button" id="btnExplain" value="explain" onclick="javascript:doExplain();" title="Explain Query Plan">
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

<div class="container" id="resultContainer">
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
	editor.setOptions({
		fontSize: "11pt"
	});
	// http://stackoverflow.com/questions/27534263/making-ace-editor-resizable
	// https://github.com/ajaxorg/ace/wiki/Configuring-Ace
	editor.setAutoScrollEditorIntoView(true);
	editor.getSession().on('change', onTextFieldChange);
	
	setParameters(<%= numOfParameters %>);
</script>
<script type="text/javascript">
	makeHrefs();
	validateEditComponents(<%= queryLoaded %>);
	
	window.onresize = updateUI;
	
	// see: http://stackoverflow.com/questions/19329530/onresize-for-div-elements
	//byId('editor').onresize = function() { console.log('editor.onresize..'); updateUI(); };
	byId('editor').onclick = function() {
		//console.log('editor.onclick'); 
		updateUI();
	};
</script>

</body>
