
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
