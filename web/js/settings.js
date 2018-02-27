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
