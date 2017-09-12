
var modelStatus = null;

function loadModelStatus(callback) {
	var oReq = new XMLHttpRequest();
	oReq.addEventListener("load", callback?callback:loadModelStatusContent);
	oReq.open("GET", "info/status.jsp");
	oReq.send();
}

function loadModelStatusContent(oEvent) {
	var txt = oEvent.target.responseText;
	var json = JSON.parse(txt);
	//console.log(json);
	if(json["models-info"]) {
		modelStatus = json["models-info"];
	}
	console.log('models-info', modelStatus);
	
	if(typeof loadModelStatusContentCallback === 'function') {
		loadModelStatusContentCallback();
	}
}

function statusWarnings2Text(warnings) {
	var ret = "";
	var keys = Object.keys(warnings);
	for(var i=0;i<keys.length;i++) {
		ret += escapeXML(keys[i]+": "+warnings[keys[i]]+"\n\n");
	}
	//console.warn(ret);
	return ret;
}
