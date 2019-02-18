
var modelsInfo = null;

function loadModels(callback) {
	var oReq = new XMLHttpRequest();
	oReq.addEventListener("load", callback?callback:loadModelsContent);
	oReq.open("GET", "info/env.jsp");
	oReq.send();
}

function loadModelsContent(oEvent) {
	var txt = oEvent.target.responseText;
	var json = JSON.parse(txt);
	//console.log(json);
	if(json.models) {
		modelsInfo = json.models;
		//loadSelect(json.models, 'model');
	}
	//console.log('modelsInfo', modelsInfo);
	/*if(json.models.length>1) {
		document.getElementById('model').parentNode.style.display = 'inline-block';
	}
	for(var i=0;i<json.models.length;i++) {
		loadSchemas(json.models[i]);
	}
	updateSelectedQueryState();*/
	
	if(typeof loadModelsContentCallback === 'function') {
		loadModelsContentCallback();
	}
}

//--- ui functions

function getCurrentModelId() {
	var elem = document.getElementById('model');
	if(!elem) {
		elem = document.getElementById('modelTarget'); // diff<*>.html
		if(!elem) {
			console.warn("getCurrentModelId: element 'model' undefined...");
			return;
		}
	}
	var model = elem.value;
	return model=="null"?null:model;
}

function getModelCount() {
	return modelsInfo? modelsInfo.length: 1;
}

function isMultiModel() {
	return getModelCount()>1;
}

getModel = function() {}

//document.addEventListener("DOMContentLoaded", loadModels);
