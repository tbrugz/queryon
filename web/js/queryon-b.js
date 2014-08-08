
var baseUrl;

var relationsHash = {};

function init(url, containerId) {
	baseUrl = url;
	$('#'+containerId).append('<option value="" selected disabled>select object</option>');
	//console.log('baseUrl: '+baseUrl);
	$.ajax({
		url: baseUrl+'/relation.json',
		success: function(data) {
			var rels = data.relation;
			if(rels) { console.log('Load was performed. '+rels.length+' relations loaded'); }
			writeRelations(containerId, rels);
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
	var params = relationsHash[id].parameterCount;
	console.log('selected: '+id+' ; params: '+params);
	if(params==null || params=="") {
		params = 0;
	}
	setParameters(parametersId, params);
}

function doRun(selectId, containerId, messagesId) {
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
		queryString += '&'+item.name+"="+item.value;
	}
	
	var order = document.getElementById('order').value;
	if(order!=null && order!='') {
		queryString += '&order='+order;
	}
	
	//console.log('query-string: '+queryString);
	console.log('url: '+baseUrl+'/'+id+paramsStr+'.htmlx?'+queryString);
	
	var startTimeMilis = Date.now();
	$.ajax({
		url: baseUrl+'/'+id+paramsStr+'.htmlx?'+queryString,
		success: function(data) {
			var completedTimeMilis = Date.now();
			$('#'+containerId).html(data);
			closeMessages(messagesId);
			addSortHrefs(containerId, order);
			showRunStatusInfo(containerId, 'status-container', startTimeMilis, completedTimeMilis);
		},
		error: function(jqXHR, textStatus, errorThrown) {
			$('#'+messagesId).html(jqXHR.responseText+"<input type='button' class='errorbutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>");
			$('#'+messagesId).attr('class','error');
		}
	});
}

function addSortHrefs(containerId, order) {
	var content = document.getElementById(containerId);
	var headers = content.getElementsByTagName('th');
	//console.log('headers.length: '+headers.length);
	for(var i=0;i<headers.length;i++) {
		var elem = headers[i];
		var colname = elem.innerHTML;
		var idx = colname.indexOf('<');
		if(idx>0) {
			colname = colname.substring(0, idx);
		}
		//console.log('colname['+i+']: '+colname);
		
		var elemValue = colname
			+ '<div class="orderbutton-container">';
		if(order==colname) {
			elemValue += '<input type=button class="orderbutton button-selected" value="A"/>'
		}
		else {
			elemValue += '<input type=button class="orderbutton" onclick="javascript:sortBy(\''+colname+'\', 1);" value="A"/>'
		}
		
		if(order=="-"+colname) {
			elemValue += '<input type=button class="orderbutton button-selected" value="D"/>'
		}
		else {
			elemValue += '<input type=button class="orderbutton" onclick="javascript:sortBy(\''+colname+'\', 2);" value="D"/>'
		}
		elemValue += '</div>';
		
		elem.innerHTML = elemValue;
	}
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

function setParameters(parametersId, numparams) {
	var params = document.querySelectorAll('.parameter');
	console.log('numparams: '+numparams+' ; params.length: '+params.length);
	if(numparams > params.length) {
		for(var i=params.length+1;i<=numparams;i++) {
			$("#"+parametersId).append("<label class='parameter-label'>p"+i+": <input type='text' class='parameter' id='param"+i+"' name='p"+i+"' onchange='makeHrefs();'/></label>");
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

function closeMessages(messagesId) {
	document.getElementById(messagesId).innerHTML = '';
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

function getColumns(id) {
	var colsStr = relationsHash[id].columnNames;
	var cols = colsStr.split(",");

	for(var i=0;i<cols.length;i++) {
		cols[i] = cols[i].replace("[","").replace("]","").trim();
	}
	return cols;
}
