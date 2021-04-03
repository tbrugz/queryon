
// from: http://stackoverflow.com/questions/133925/javascript-post-request-like-a-form-submit
function post(path, params, callback, target) {
	//method = method || "POST"; // Set method to post by default if not specified.
	var method = "POST";

	// The rest of this code assumes you are not using a library.
	// It can be made less wordy if you use one.
	var form = document.createElement("form");
	form.setAttribute("method", method);
	form.setAttribute("action", path);
	if(typeof target == "string") {
		form.setAttribute("target", target);
	}
	console.log('post: method=',method,' path= ',path," target= ",target);
	//console.log(params);

	for ( var key in params) {
		if (params.hasOwnProperty(key)) {
			var hiddenField = document.createElement("input");
			hiddenField.setAttribute("type", "hidden");
			hiddenField.setAttribute("name", key);
			hiddenField.setAttribute("value", params[key]);

			form.appendChild(hiddenField);
		}
	}

	document.body.appendChild(form);
	//if(callback) { form.addEventListener("readystatechange", callback); } // would be nice if worked...
	form.submit();
	if(callback) { callback(); }
}

// https://stackoverflow.com/questions/1714786/query-string-encoding-of-a-javascript-object
function obj2encodedUrl(obj) {
	var str = [];
	for (var p in obj)
		if (obj.hasOwnProperty(p)) {
		str.push(encodeURIComponent(p) + "=" + encodeURIComponent(obj[p]));
	}
	return str.join("&");
}

// https://stackoverflow.com/questions/22724070/prompt-file-download-with-xmlhttprequest/44435573#44435573
function saveBlob(content, fileName, contentType) {
	var a = document.createElement('a');
	blob = new File([content], fileName); //, { type: contentType });
	a.href = window.URL.createObjectURL(blob);
	a.download = fileName;
	a.dispatchEvent(new MouseEvent('click'));
}

function doHttpRequest(url, params, callbackOk, callbackError) {
	var request = new XMLHttpRequest();
	request.open("POST", url, true);
	request.onload = function(oEvent) {
		if (request.status >= 200 && request.status < 300) {
			//var updateCount = request.getResponseHeader("X-UpdateCount");
			var warnings = request.getResponseHeader("X-Warning");
			if(warnings) {
				console.log("warnings:", warnings);
			}
			if(callbackOk) {
				callbackOk(oEvent);
			}
			else {
				console.log("no callback?", oEvent);
			}
		}
		else if(callbackError) {
			callbackError(oEvent);
		}
		else {
			console.log("error[",request.status,"] - no callback?", oEvent);
		}
	}
	request.onerror = function(oEvent) {
		if(callbackError) {
			callbackError(oEvent);
		}
		else {
			console.log("onerror:", oEvent);
		}
	}
	request.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");	
	//request.overrideMimeType('text/html; charset=UTF-8');
	request.send(obj2encodedUrl(params));
}
