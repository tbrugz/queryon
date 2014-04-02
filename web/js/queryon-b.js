
var tables;
var views;
var baseUrl;

var relationsHash = {};

function init(url, containerId) {
	baseUrl = url;
	$('#'+containerId).append('<option value="" selected>select object</option>');
	//console.log('baseUrl: '+baseUrl);
	$.ajax({
		url: baseUrl+'/table.json',
		success: function(data) {
			tables = data.table;
			if(tables) { console.log('Load was performed. '+tables.length+' tables loaded'); }
			writeTables(containerId);
		}
	});
	$.ajax({
		url: baseUrl+'/view.json',
		success: function(data) {
			views = data.view;
			if(views) { console.log('Load was performed. '+views.length+' views loaded'); }
			writeViews(containerId);
		}
	});
}

function getId(obj) {
	return (obj.schemaName!=null && obj.schemaName!="" && obj.schemaName!="null")?obj.schemaName+"."+obj.name:obj.name;
}

function writeTables(containerId) {
	if(!tables) { return; }
	for(var i=0;i<tables.length;i++) {
		var id = getId(tables[i]);
		//var id = tables[i].schemaName+'.'+tables[i].name;
		$(containerId).append("<option name='"+id+"'>"+id+" [T]</option>");
		relationsHash[id] = tables[i];
	}
}

function writeViews(containerId) {
	console.log('write views [#'+views.length+'] to '+containerId);
	if(!views) { return; }
	for(var i=0;i<views.length;i++) {
		var id = getId(views[i]);
		$('#'+containerId).append("<option value='"+id+"'>"+id+" [V]</option>");
		relationsHash[id] = views[i];
	}
}

function loadRelation(selectId, parametersId, containerId) {
	var select = document.getElementById(selectId);
	var id = select.options[select.selectedIndex].value;
	var params = relationsHash[id].parameterCount;
	console.log('selected: '+id+' ; params: '+params);
	if(params>0) {
		setParameters(parametersId, params);
	}
	//doRun()
}

function doRun(selectId, containerId, messagesId) {
	var select = document.getElementById(selectId);
	var id = select.options[select.selectedIndex].value;
	var params = document.querySelectorAll('.parameter');
	var paramsStr = '';
	for (var i = 0; i < params.length; ++i) {
		var item = params[i];
		//console.log(item);
		paramsStr += '/'+item.value;
	}
	$.ajax({
		url: baseUrl+'/'+id+paramsStr+'.htmlx',
		success: function(data) {
			$('#'+containerId).html(data);
			closeMessages(messagesId);
		},
		error: function(jqXHR, textStatus, errorThrown) {
			$('#'+messagesId).html(jqXHR.responseText+"<input type='button' class='errorbutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>");
			$('#'+messagesId).attr('class','error');
		}
	});
}

function setParameters(parametersId, numparams) {
	var params = document.querySelectorAll('.parameter');
	console.log('numparams: '+numparams+' ; params.length: '+params.length);
	if(numparams > params.length) {
		for(var i=params.length+1;i<=numparams;i++) {
			$("#"+parametersId).append("<label class='parameter-label'>p"+i+": <input type='text' class='parameter' id='param"+i+"' name='p"+i+"'/></label>");
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

function closeMessages(messagesId) {
	document.getElementById(messagesId).innerHTML = '';
}
