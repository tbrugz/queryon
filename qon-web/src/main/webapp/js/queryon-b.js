
var baseUrl;

var relationsHash = {};

var defaultNullObjectOption = '<option value="" selected disabled>select object</option>';

var utf8par = "utf8=✓";

var messagesId = "messages";

function init(url, containerId, callback, modelId) {
	baseUrl = url;
	callback = typeof callback !== 'undefined' ? callback : writeRelations;
	byId(containerId).innerHTML = defaultNullObjectOption;
	//$('#'+containerId).append('<option value="" selected>select object</option>');
	var url = baseUrl+'/relation.json'+(modelId?'?model='+modelId:'');
	//console.log('url: ', url, 'baseUrl: ', baseUrl);
	$.ajax({
		url: url,
		dataType: "json",
		success: function(data) {
			var rels = getQonData(data); //data.relation;
			if(rels) {
				console.log('Load was performed. ', rels.length,' relations loaded');
			}
			else {
				console.log('Error loading relations', data);
			}
			callback(containerId, rels);
		},
		error: function(jqXHR, textStatus, errorThrown) {
			//console.log("jqXHR", jqXHR, "textStatus", textStatus, "errorThrown", errorThrown);
			var message = jqXHR.responseText;
			if(!message) {
				message = jqXHR.status + ": " + jqXHR.statusText;
			}
			showErrorMessages(messagesId, message);
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
		$('#'+containerId).append("<option value='"+id+"'"+
				//" class='schema_"+relations[i].schemaName+"'"+
				">"+getDescription(relations[i])+"</option>");
		relationsHash[id] = relations[i];
	}
}

function loadRelation(selectId, parametersId, containerId) {
	var select = document.getElementById(selectId);
	var id = select.options[select.selectedIndex].value;
	var params = relationsHash[id] ? relationsHash[id].parameterCount : null;
	//console.log('selected: '+id+' ; params: '+params);
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
	
	var modelElem = document.getElementById('model');
	if(modelElem) {
		var modelId = modelElem.value;
		if(modelId) {
			queryString += 'model='+modelId;
		}
	}
	
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
	queryString += "&" + utf8par;
	
	queryString = getPivotURL(queryString);

	//console.log('query-string: '+queryString);
	var returl = (baseUrlParam?baseUrlParam:baseUrl)
		+'/'+id+paramsStr
		+(syntax?'.'+syntax:'')
		+(queryString?'?'+queryString:'');
	
	console.log('url: '+returl);
	return returl;
}

function pivotQueryActive() {
	var pivots = byId('pivots');
	return (pivots && pivots.style.display!='none');
}

function getPivotURL(url) {
	if(pivotQueryActive()) {
		var oncols = byId('oncols').querySelectorAll('.col');
		var oncolsArr = getNodeListAttributeAsArray(oncols, 'data-value');
		
		var onrows = byId('onrows').querySelectorAll('.col');
		var onrowsArr = getNodeListAttributeAsArray(onrows, 'data-value');

		var measures = byId('measures').querySelectorAll('.col');
		var measuresArr = getNodeListAttributeAsArray(measures, 'data-value');

		var measuresAggs = byId('measures').querySelectorAll('.col select');
		var measuresAggsArr = []
		for(var i=0;i<measuresAggs.length;i++) {
			measuresAggsArr.push(measuresAggs[i].value);
		}
		
		//console.log(">> pivot: cols:", oncolsArr, "rows:", onrowsArr, "measures:", measuresArr, "measuresAggsArr:", measuresAggsArr);
		
		if(oncols.length>0 || onrows.length>0 || measures.length>0) {
			var measuresStr = "";
			for(var i=0;i<measuresArr.length;i++) {
				measuresStr += "&agg:"+measuresArr[i]+"="+measuresAggsArr[i];
			}
			var newUrl = url +
				(oncols.length>0?"&oncols="+oncolsArr.join():"") +
				(onrows.length>0?"&onrows="+onrowsArr.join():"") +
				(oncols.length>0 || onrows.length>0?"&groupbydims=true":"") +
				measuresStr;
			//console.log(">> pivot: newUrl:", newUrl);
			return newUrl;
		}
	}
	return url;
}

function getNodeListAttributeAsArray(nodelist, attr) {
	var arr = [];
	for(var i=0;i<nodelist.length;i++) {
		arr.push(nodelist[i].getAttribute(attr));
	}
	return arr;
}

function doRun(selectId, containerId, messagesId, callback, errorCallback) {
	var finalUrl = getQueryUrl(selectId, 'htmlx');
	
	btnActionStart('go-button');
	var startTimeMilis = Date.now();
	var order = document.getElementById('order').value;
	var container = document.getElementById(containerId);
	
	if(container.tagName=='IFRAME') {
		// http://stackoverflow.com/questions/3142837/capture-iframe-load-complete-event
		container.onload = function() {
			btnActionStop('go-button');
			var completedTimeMilis = Date.now();
			closeMessages(messagesId);
			//addSortHrefs(containerId, order);
			showRunStatusInfo(containerId, 'status-container', startTimeMilis, completedTimeMilis);
			if(callback) { callback(); }
		};
		container.src = finalUrl;
	}
	else {
	
	$.ajax({
		url: finalUrl,
		dataType: "html",
		success: function(data, textStatus, request) {
			btnActionStop('go-button');
			var completedTimeMilis = Date.now();
			container.innerHTML = data;
			//console.log('X-ResultSet-Limit',request.getResponseHeader('X-ResultSet-Limit'));
			closeMessages(messagesId);
			addSortHrefs(containerId, order);
			showRunStatusInfo(containerId, 'status-container', startTimeMilis, completedTimeMilis);
			if(callback) { callback(request); }
			//var doneTimeMilis = Date.now();
			//console.log('doRun: times: ', startTimeMilis, completedTimeMilis, doneTimeMilis);
		},
		error: function(jqXHR, textStatus, errorThrown) {
			btnActionStop('go-button');
			showErrorMessages(messagesId, jqXHR.responseText ? jqXHR.responseText : "No response from server");
			//if(! jqXHR.responseText) { console.warn("Error", jqXHR); }
			if(errorCallback) { errorCallback(errorThrown); }
			//$('#'+messagesId).html(jqXHR.responseText+"<input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>");
			//$('#'+messagesId).attr('class','error');
		}
	});
	
	}
}

function addSortHrefs(containerId, order) {
	//var content = document.getElementById(containerId);
	//var headers = content.getElementsByTagName('th');
	var headers = document.querySelectorAll('#'+containerId+' > table > tbody > tr > th');
	//console.log('headers.length: '+headers.length);
	for(var i=0;i<headers.length;i++) {
		var elem = headers[i];
		if(elem.classList.contains("blank")) { continue; }
		if(elem.getAttribute("measure")=="true" ||
				elem.parentNode.getAttribute("measuresrow")=="true" ||
				elem.parentNode.getAttribute("colname")!=null) { continue; } //XXX really needed?
		
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
		// see: http://stackoverflow.com/questions/15988373/how-do-i-add-a-font-awesome-icon-to-input-field
		//elemValue += '<span class="filterbutton-container"><i class="fa fa-filter" aria-hidden="true"></i></span>';
		elemValue += '<span class="filterbutton-container"><input type=button class="filterbutton" id="filter_col_'+colname+'" onclick="addFilterDialog(\''+colname+'\');" value="&#xf0b0;" title="filter by column"/></span>';

		elem.innerHTML = elemValue;
	}
}

function showRunStatusInfo(containerId, messagesId, startTimeMilis, completedTimeMilis) {
	//var renderedTimeMilis = Date.now();
	
	var content = document.getElementById(containerId);
	if(content.tagName=='IFRAME') { content = content.contentDocument; }
	var messages = document.getElementById(messagesId);
	
	var numOfRows = content.getElementsByTagName('tr').length-1; // 1st is header
	//messages.innerHTML = 'rows = '+numOfRows+' ; time in millis: server = '+(completedTimeMilis-startTimeMilis)+' ; render = '+(renderedTimeMilis-completedTimeMilis)
	messages.innerHTML = 'rows = '+numOfRows+' ; time = '+(completedTimeMilis-startTimeMilis)+'ms '
		+"<input type='button' class='statusbutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>";
	messages.style.display = '';
}

function setParameters(parametersId, numparams) {
	var params = document.querySelectorAll('.parameter');
	//console.log('numparams: '+numparams+' ; params.length: '+params.length);
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
	//console.log('setParametersValues: ', params, values);
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
		var value = item.type!='file' ? item.value : '';
		if(value=='') { value = '-'; }
		paramsStr += '/'+value;
	}
	return paramsStr;
}

/*
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
*/

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
	//console.log("getColumnsFromRelation",relation);
	var colsStr = relation.columnNames;
	if(! colsStr) { return null; }
	var cols = colsStr.split(",");
	//XXX: ignore *_STYLE, *_CLASS, *_TITLE & *_HREF" ??

	for(var i=cols.length-1;i>=0;i--) {
		cols[i] = cols[i].replace("[","").replace("]","").trim();
		if(cols[i]=="") { cols.pop(i); }
	}
	return cols;
}

// obsolete?
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

/* returns only htmlx visible column names */
function getColumnNamesFromColgroup(containerId) {
	var colNames = [];
	var content = document.getElementById(containerId);
	if(!content) {
		return colNames;
	}
	var cols = content.querySelectorAll('table > colgroup > col');
	//console.log('cols.length: '+cols.length);
	for(var i=0;i<cols.length;i++) {
		var elem = cols[i];
		var ct = elem.getAttribute('colname');
		colNames.push(ct);
	}
	return colNames;
}

function getColumnNames(containerId) {
	var cols = null;
	var relation = getCurrentRelation('objects');
	
	if(pivotQueryActive()) {
		// pivot query changes colgroup...
		cols = getColumnsFromRelation(relation);
	}
	if(cols==null || cols.length==0) {
		cols = getColumnNamesFromColgroup(containerId);
	}
	if(cols==null || cols.length==0) {
		cols = getColumnsFromRelation(relation);
	}
	if(cols==null || cols.length==0) {
		cols = getColumnsFromContainer('content');
	}
	return cols;
}

/* returns only htmlx visible column types */
function getColumnTypesFromColgroup(containerId) {
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

function getColumnsFromColgroup(containerId) {
	var colTypes = [];
	var content = document.getElementById(containerId);
	if(!content) {
		return colTypes;
	}
	return content.querySelectorAll('table > colgroup > col');
}

function getAttributeListFromObjectList(objs, attr) {
	var ret = [];
	for(var i=0;i<objs.length;i++) {
		var elem = objs[i];
		var atribute = elem.getAttribute(attr);
		ret.push(atribute);
	}
	return ret;
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
	var relation = getCurrentRelation('objects');
	var cols = getColumnNames(containerId);
	/*var cols = getColumnNamesFromColgroup(containerId);
	if(cols==null || cols.length==0) {
		cols = getColumnsFromRelation(relation);
	}*/
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
	
	console.log('getQueryUpdateUrl:', returl);
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
	var modelStr = getCurrentModelId()?"&model="+getCurrentModelId():"";
	var finalUrl = getQueryUpdateUrl(selectId, key, 'updatemax=1&updatemin=1'+modelStr, 'json');

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