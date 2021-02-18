
var modelsInfo = null;
var servicesInfo = null;
var updatePluginsInfo = null;

function loadModels(callback) {
	var oReq = new XMLHttpRequest();
	oReq.addEventListener("load", callback?callback:loadModelsContent);
	oReq.open("GET", "qinfo/env");
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
	if(json.services) {
		servicesInfo = json.services;
	}
	if(json["update-plugins"]) {
		updatePluginsInfo = json["update-plugins"];
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

function isServiceActive(service) {
	return servicesInfo && servicesInfo[service];
}

function getCurrentModelUpdatePlugins() {
	if( updatePluginsInfo ) {
		var keys = Object.keys(updatePluginsInfo);
		if( keys.length == 0) {
			return [];
		}
		if( keys.length == 1) {
			return updatePluginsInfo[keys[0]];
		}
		return updatePluginsInfo[getCurrentModelId()];
	}
	return [];
}

function isQonQueriesPluginActive() {
	return getCurrentModelUpdatePlugins().includes("QOnQueries");
}

function getModel() {}

//document.addEventListener("DOMContentLoaded", loadModels);
