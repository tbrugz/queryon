
var modelsInfo = null;

function loadModels() {
	var oReq = new XMLHttpRequest();
	oReq.addEventListener("load", loadModelsContent);
	oReq.open("GET", "info/model.jsp");
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
	console.log('modelsInfo', modelsInfo);
	/*if(json.models.length>1) {
		document.getElementById('model').parentNode.style.display = 'inline-block';
	}
	for(var i=0;i<json.models.length;i++) {
		loadSchemas(json.models[i]);
	}
	updateSelectedQueryState();*/
}

document.addEventListener("DOMContentLoaded", loadModels);
