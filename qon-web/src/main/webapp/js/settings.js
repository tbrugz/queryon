var settings = {};

function loadSettings(callbackOk) {
	$.ajax({
		url: 'info/settings.jsp',
		dataType: "text",
		success: function(data) {
			var info = JSON.parse(data);
			//console.log('settings',info);
			settings = info;
			if(callbackOk) { callbackOk(); }
		}
	});
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
