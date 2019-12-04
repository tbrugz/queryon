
var modelStatus = null;

function loadModelStatus(callback) {
	//console.log("loadModelStatus", callback);
	var oReq = new XMLHttpRequest();
	oReq.addEventListener("load", callback?callback:loadModelStatusContent);
	oReq.open("GET", "info/status.jsp");
	oReq.send();
}

function loadModelStatusContent(oEvent) {
	//console.log("loadModelStatusContent");
	var txt = oEvent.target.responseText;
	var json = JSON.parse(txt);
	//console.log(json);
	if(json["models-info"]) {
		modelStatus = json["models-info"];
	}
	//console.log('models-info', modelStatus);
	
	if(typeof loadModelStatusContentCallback === 'function') {
		loadModelStatusContentCallback();
	}
}

function statusGetWarnings(modelId, type) {
	if(modelId=="") { modelId = "null"; }
	var types = ["init", "queries", "tables", "execs"];
	if(types.indexOf(type)<0) {
		console.warn("type [",type,"] must be one of [",types,"]");
	}
	return modelStatus ? modelStatus[modelId+"."+type+"-warnings"] : null;
}

function statusWarnings2Text(warnings) {
	var ret = "";
	var keys = Object.keys(warnings);
	for(var i=0;i<keys.length;i++) {
		ret += escapeXML(keys[i]+": "+warnings[keys[i]]+"\n\n");
	}
	return ret;
}
