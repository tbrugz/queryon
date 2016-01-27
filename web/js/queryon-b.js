
var baseUrl;

var relationsHash = {};

function init(url, containerId, callback) {
	baseUrl = url;
	callback = typeof callback !== 'undefined' ? callback : writeRelations;
	$('#'+containerId).append('<option value="" selected>select object</option>');
	//console.log('baseUrl: '+baseUrl);
	$.ajax({
		url: baseUrl+'/relation.json',
		success: function(data) {
			var rels = data.relation;
			if(rels) { console.log('Load was performed. '+rels.length+' relations loaded'); }
			callback(containerId, rels);
		}
	});
}

function getId(obj) {
	return (obj.schemaName!=null && obj.schemaName!="" && obj.schemaName!="null")?obj.schemaName+"."+obj.name:obj.name;
}

function getDescription(obj) {
	return ((obj.schemaName!=null && obj.schemaName!="" && obj.schemaName!="null")?obj.schemaName+"."+obj.name:obj.name)
		+ ((obj.remarks!=null && obj.remarks!="" && obj.remarks!="null")?" - "+obj.remarks:"");
}

function writeRelations(containerId, relations) {
	if(!relations) { return; }
	console.log('write relations [#'+relations.length+'] to '+containerId);
	for(var i=0;i<relations.length;i++) {
		var id = getId(relations[i]);
		$('#'+containerId).append("<option value='"+id+"'>"+getDescription(relations[i])+"</option>");
		relationsHash[id] = relations[i];
	}
}

function loadRelation(selectId, parametersId, containerId) {
	var select = document.getElementById(selectId);
	var id = select.options[select.selectedIndex].value;
	var params = relationsHash[id] ? relationsHash[id].parameterCount : null;
	console.log('selected: '+id+' ; params: '+params);
	if(params==null || params=="") {
		params = 0;
	}
	setParameters(parametersId, params);
}

function getQueryUrl(selectId, syntax, baseUrlParam) {
	var select = document.getElementById(selectId);
	var id = select.options[select.selectedIndex].value;
	var params = document.querySelectorAll('.parameter');
	var paramsStr = '';
	for (var i = 0; i < params.length; ++i) {
		var item = params[i];
		//console.log(item);
		var str = item.value;
		if(str=='') { str = '-'; } 
		paramsStr += '/'+str;
	}
	
	var queryString = '';
	
	var filters = document.querySelectorAll('.filter');
	for (var i = 0; i < filters.length; ++i) {
		var item = filters[i];
		//console.log(item);
		queryString += '&'+item.name+"="+encodeURIComponent(item.value);
	}
	
	var order = document.getElementById('order');
	if(order) {
		order = order.value;
		if(order!=null && order!='') {
			queryString += '&order='+order;
		}
	}
	
	var offset = document.getElementById('offset');
	if(offset) { offset = offset.value; };
	if(offset!=null && offset>0) {
		queryString += '&offset='+offset;
	}
	
	//console.log('query-string: '+queryString);
	var returl = (baseUrlParam?baseUrlParam:baseUrl)
		+'/'+id+paramsStr
		+(syntax?'.'+syntax:'')
		+(queryString?'?'+queryString:'');
	
	console.log('url: '+returl);
	return returl;
}

function doRun(selectId, containerId, messagesId, callback) {
	var finalUrl = getQueryUrl(selectId, 'htmlx');
	
	btnActionStart('go-button');
	var startTimeMilis = Date.now();
	var order = document.getElementById('order').value;
	$.ajax({
		url: finalUrl,
		dataType: "html",
		success: function(data, textStatus, request) {
			btnActionStop('go-button');
			var completedTimeMilis = Date.now();
			$('#'+containerId).html(data);
			console.log('X-ResultSet-Limit',request.getResponseHeader('X-ResultSet-Limit'));
			closeMessages(messagesId);
			addSortHrefs(containerId, order);
			showRunStatusInfo(containerId, 'status-container', startTimeMilis, completedTimeMilis);
			if(callback) { callback(request); }
		},
		error: function(jqXHR, textStatus, errorThrown) {
			btnActionStop('go-button');
			showErrorMessages(messagesId, jqXHR.responseText);
			//$('#'+messagesId).html(jqXHR.responseText+"<input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>");
			//$('#'+messagesId).attr('class','error');
		}
	});
}

function addSortHrefs(containerId, order) {
	//var content = document.getElementById(containerId);
	//var headers = content.getElementsByTagName('th');
	var headers = document.querySelectorAll('#'+containerId+' > table > tbody > tr > th');
	//console.log('headers.length: '+headers.length);
	for(var i=0;i<headers.length;i++) {
		var elem = headers[i];
		var colname = elem.innerHTML;
		var idx = colname.indexOf('<');
		if(idx>0) {
			colname = colname.substring(0, idx);
		}
		//console.log('colname['+i+']: '+colname+" ; order = ",order);
		
		var elemValue = colname
			+ '<span class="orderbutton-container">';
		if(order==colname) {
			elemValue += '<input type=button class="orderbutton button-selected" onclick="javascript:sortByNone();" value="&#9650;" title="remove order"/>'
		}
		else {
			elemValue += '<input type=button class="orderbutton" onclick="javascript:sortBy(\''+colname+'\', 1);" value="&#9650;" title="order ASC"/>'
		}
		
		if(order=="-"+colname) {
			elemValue += '<input type=button class="orderbutton button-selected" onclick="javascript:sortByNone();" value="&#9660;" title="remove order"/>'
		}
		else {
			elemValue += '<input type=button class="orderbutton" onclick="javascript:sortBy(\''+colname+'\', 2);" value="&#9660;" title="order DESC"/>'
		}
		elemValue += '</span>';
		
		elem.innerHTML = elemValue;
	}
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

function setParameters(parametersId, numparams) {
	var params = document.querySelectorAll('.parameter');
	console.log('numparams: '+numparams+' ; params.length: '+params.length);
	if(numparams > params.length) {
		for(var i=params.length+1;i<=numparams;i++) {
			$("#"+parametersId).append("<label class='parameter-label'>p"+i+": <input type='text' class='parameter' id='param"+i+"' name='p"+i+"' onchange='onParameterChange("+i+");'/></label>");
		}
	}
	else if(numparams < params.length) {
		for (var i = params.length; i > numparams; --i) {
			var item = params[i-1];
			//console.log(item);
			item = item.parentNode;
			item.parentNode.removeChild(item);
		}
	}
}

function setParametersValues(values) {
	var params = document.querySelectorAll('.parameter');
	console.log('setParametersValues: ', params, values);
	for(var i=0;i<params.length;i++) {
		if(values.length>i) {
			params[i].value = values[i];
		}
	}
}

function getParameters() {
	var params = document.querySelectorAll('.parameter');
	var paramsStr = '';
	for (var i = 0; i < params.length; ++i) {
		var item = params[i];
		//console.log(item);
		var value = item.value;
		if(value=='') { value = '-'; }
		paramsStr += '/'+value;
	}
	return paramsStr;
}

function showInfoMessages(messagesId, text) {
	$('#'+messagesId).html("<span>"+text+"</span><input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>");
	$('#'+messagesId).attr('class','info');
	updateUI();
}

function showWarnMessages(messagesId, text) {
	$('#'+messagesId).html("<span>"+text+"</span><input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>");
	$('#'+messagesId).attr('class','warn');
	updateUI();
}

function showErrorMessages(messagesId, text) {
	$('#'+messagesId).html("<span>"+text+"</span><input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>");
	$('#'+messagesId).attr('class','error');
	//$('#'+messagesId).addClass('error'); //when to remove?
	updateUI();
}

function appendMessages(messagesId, text) {
	$('#'+messagesId+" span").append(text);
	updateUI();
}

function changeMessagesClass(messagesId, clazz) {
	$('#'+messagesId).attr('class',clazz);
}

function closeMessages(messagesId) {
	document.getElementById(messagesId).innerHTML = '';
	updateUI();
}

// see: http://stackoverflow.com/questions/827368/using-the-get-parameter-of-a-url-in-javascript
function getQueryVariable(variable) {
	var query = window.location.search.substring(1);
	var vars = query.split("&");
	for (var i = 0; i < vars.length; i++) {
		var pair = vars[i].split("=");
		if (pair[0] == variable) {
			return pair[1];
		}
	}
	//console.log('Query Variable ' + variable + ' not found');
}

function getColumnsFromRelation(relation) {
	console.log("getColumnsFromRelation",relation);
	var colsStr = relation.columnNames;
	if(! colsStr) { return null; }
	var cols = colsStr.split(",");

	for(var i=cols.length-1;i>=0;i--) {
		cols[i] = cols[i].replace("[","").replace("]","").trim();
		if(cols[i]=="") { cols.pop(i); }
	}
	return cols;
}

function getColumnsFromContainer(containerId) {
	var cols = [];
	var content = document.getElementById(containerId);
	if(!content) {
		return cols;
	}
	var headers = content.getElementsByTagName('th');
	//console.log('headers.length: '+headers.length);
	for(var i=0;i<headers.length;i++) {
		var elem = headers[i];
		var colname = elem.innerHTML;
		var idx = colname.indexOf('<');
		if(idx>0) {
			colname = colname.substring(0, idx);
		}
		cols.push(colname);
	}
	return cols;
}

function getColumnsTypesFromContainer(containerId) {
	var colTypes = [];
	var content = document.getElementById(containerId);
	if(!content) {
		return colTypes;
	}
	var cols = content.querySelectorAll('table > colgroup > col');
	//console.log('cols.length: '+cols.length);
	for(var i=0;i<cols.length;i++) {
		var elem = cols[i];
		var ct = elem.getAttribute('type');
		colTypes.push(ct);
	}
	return colTypes;
}

function getColumnsRemarks() {
	var rel = getCurrentRelation('objects');
	var arr = rel.columnRemarks.substring(1, rel.columnRemarks.length-1).split(",");
	var ret = [];
	for(var i=0;i<arr.length;i++) {
		ret.push(arr[i].trim());
	}
	return ret;
}

function getColumnsTypesFromHash() {
	var rel = getCurrentRelation('objects');
	var arr = rel.columnTypes.substring(1, rel.columnTypes.length-1).split(",");
	var ret = [];
	for(var i=0;i<arr.length;i++) {
		ret.push(arr[i].trim());
	}
	return ret;
}

function getValuesFromColumn(containerId, columnName) {
	var cols = getColumnsFromContainer(containerId);
	var colPos = cols.indexOf(columnName);
	if(colPos==-1) {
		console.log("column "+columnName+" not found");
		return [];
	}
	
	var content = document.getElementById(containerId);
	var rows = content.getElementsByTagName('tr');
	
	//console.log(colPos);
	//var colValues = new Set();
	var colValues = [];
	
	for(var i=0;i<rows.length;i++) {
		var elem = rows[i];
		/*if(!elem.children[colPos]) {
			console.log("column "+columnName)
			continue;
		}*/
		if(elem.children[colPos].tagName!="TD") continue;
		
		var value = elem.children[colPos].innerHTML;
		//colValues.add(value);
		if(colValues.indexOf(value)==-1) {
			colValues.push(value);
		}
	}
	
	//console.log(colValues);
	return colValues;
}

function getQueryUpdateUrl(selectId, key, queryString, syntax) {
	var select = document.getElementById(selectId);
	var id = select.options[select.selectedIndex].value;
	
	var returl = baseUrl+'/'+id
		+(key?'/'+key:'')
		+(syntax?'.'+syntax:'')
		+(queryString?'?'+queryString:'');
	
	console.log('getQueryUpdateUrl: '+returl);
	return returl;
}

function getQueryUrlById(id, key, queryString, syntax) {
	var returl = baseUrl+'/'+id
		+(key?'/'+key:'')
		+(syntax?'.'+syntax:'')
		+(queryString?'?'+queryString:'');
	
	return returl;
}

function doDelete(selectId, key, containerId, messagesId, callback, callbackError) {
	var finalUrl = getQueryUpdateUrl(selectId, key, 'updatemax=1&updatemin=1', 'json');
	
	btnActionStart('go-button');
	var startTimeMilis = Date.now();
	$.ajax({
		url: finalUrl,
		method: 'DELETE',
		dataType: "text",
		success: function(data, textStatus, jqXHR) {
			btnActionStop('go-button');
			var completedTimeMilis = Date.now();
			showInfoMessages(messagesId, jqXHR.responseText+" [please refresh page]");
			//showRunStatusInfo(containerId, 'status-container', startTimeMilis, completedTimeMilis);
			if(callback) { callback(jqXHR); }
		},
		error: function(jqXHR, textStatus, errorThrown) {
			btnActionStop('go-button');
			showErrorMessages(messagesId, jqXHR.responseText);
			if(callbackError) { callbackError(jqXHR); }
		}
	});
}

function getCurrentRelation(selectId) {
	var select = document.getElementById(selectId);
	var id = select.options[select.selectedIndex].value;
	return relationsHash[id];
}

function getPkCols(selectId) {
	var select = document.getElementById(selectId);
	var id = select.options[select.selectedIndex].value;
	var rel = relationsHash[id];
	var re = /\[[PU]K:[A-Za-z0-9_ ]*:([A-Za-z0-9_, ]+)\]/g;
	var match = re.exec(rel.constraints);
	var pkcols = null;
	//while(match!=null) {
	if(match!=null) {
		//console.log(match[1]);
		return match[1].split(',');
		//match = re.exec(rel.constraints);
	}
	return null;
}

function hasGrantOfType(grant, column) {
	//roles
	/*
	var grants = 'UPDATE';
	var column = '';
	 */
	var select = document.getElementById('objects');
	var id = select.options[select.selectedIndex].value;
	var rel = relationsHash[id];
	return relationHasGrantOfType(rel, grant, column);
}

function relationHasGrantOfType(relation, grant, column) {
	//authInfo.roles
	var roles = authInfo.roles.join("|");
	var restr = "\\["+relation.name+";priv="+grant+";to:("+roles+");"
		+(column?"col="+column:"")
		+";\\]";
	var re = new RegExp(restr);
	//var match = re.exec(rel.grants);
	var match = relation.grants.match(re);
	//console.log('roles== ',roles,'; restr==',restr,'; relation.grants==',relation.grants,'; re==',re,'; match==',match);
	return match;
}

function relationHasGrantOfTypeAnyColumn(relation, grant) {
	//authInfo.roles
	var roles = authInfo.roles.join("|");
	var restr = "\\["+relation.name+";priv="+grant+";to:("+roles+");"
		+"(col=.+)?"
		+";\\]";
	var re = new RegExp(restr);
	//console.log('roles== ',roles,'; restr==',restr,'; relation.grants==',relation.grants,'; re==',re);
	//var match = re.exec(rel.grants);
	var match = relation.grants.match(re);
	return match;
}
