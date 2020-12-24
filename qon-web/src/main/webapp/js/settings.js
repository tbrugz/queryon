var settings = {};

function loadSettings(callbackOk) {
	var url = "qinfo/settings";
	var request = new XMLHttpRequest();
	request.open("GET", url, true);
	request.onload = function(oEvent) {
		var info = JSON.parse(oEvent.target.responseText);
		//console.log('settings',info);
		settings = info;
		if(callbackOk) { callbackOk(); }
	}
	request.send();
}

function getSetting(key) {
	return settings[key];
}

function getModelSetting(prefix, suffix, modelId) {
	var value = null;
	var key = null;
	if(modelId) {
		key = prefix + '@' + modelId + '.' + suffix;
		value = settings[key];
	}
	if(! value) {
		key = prefix + '.' + suffix;
		value = settings[key];
	}
	//console.log("getModelSetting [prefix=", prefix, "; suffix=", suffix, "; modelId=", modelId, "; key=", key, "] = ", value);
	return value;
}
