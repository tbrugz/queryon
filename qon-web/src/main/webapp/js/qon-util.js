/* 
 */

var byId = function (id) { return document.getElementById(id); }

function btnActionStart(btnId) {
	var elem = byId(btnId);
	if(elem) {
		elem.classList.add("onaction");
	}
	//XXX add hourglass-like icon? css transitions?
}

function btnActionStop(btnId) {
	var elem = byId(btnId);
	if(elem) {
		elem.classList.remove("onaction");
	}
}

// http://stackoverflow.com/a/7918944/616413
function escapeXML(str) {
	return str
			.replace(/&/g, '&amp;')
			.replace(/</g, '&lt;')
			.replace(/>/g, '&gt;')
			.replace(/"/g, '&quot;')
			.replace(/'/g, '&apos;');
}

// see: http://stackoverflow.com/questions/5499078/fastest-method-to-escape-html-tags-as-html-entities
function safeTags(str) {
	return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') ;
}

/*function getQueryString() {
	var href = location.search;
	return href.indexOf("?")==-1?"":href.substr(href.indexOf("?"));
}*/

//see: http://stackoverflow.com/questions/901115/how-can-i-get-query-string-values-in-javascript
function getParameterByName(name, queryString) {
	name = name.replace(/\[/, "\\[").replace(/[\]]/, "\\]");
	var regex = new RegExp("[\\?&]" + name + "=([^&#]*)"),
		results = regex.exec(queryString);
	return results === null ? "" : decodeURIComponent(results[1].replace(/\+/g," "));
}

function getParametersByName(name, queryString) {
	name = name.replace(/\[/, "\\[").replace(/[\]]/, "\\]");
	var regex = new RegExp("[\\?&]" + name + "=([^&#]*)", "g");
	var results = regex.exec(queryString);
	var ret = [];
	if(results === null) { return ret; }
	while(results !== null) {
		ret.push( decodeURIComponent(results[1].replace(/\+/g," ")) );
		results = regex.exec(queryString);
	}
	//console.log("getParametersByName", ret);
	return ret;
}

function getParameterNamesStartWith(startWithRegex, queryString) {
	startWithRegex = startWithRegex.replace(/\[/, "\\[").replace(/[\]]/, "\\]");
	var regex = new RegExp("[\\?&](" + startWithRegex + ".*?)=([^&#]*)", "g");
	var results;
	var ret = [];
	//https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/exec
	while ((results = regex.exec(queryString)) !== null) {
		//ret.push(results[1]);
		ret.push( decodeURIComponent(results[1].replace(/\+/g," ")) );
	}
	//return results === null ? "" : decodeURIComponent(results[1].replace(/\+/g," "));
	return ret;
	// test: getParameterNamesStartWith('',document.location.href)
}

function getParameterArrayStartWith(startWithRegex, queryString) {
	startWithRegex = startWithRegex.replace(/\[/, "\\[").replace(/[\]]/, "\\]");
	var regex = new RegExp("[\\?&](" + startWithRegex + ".*?)=([^&#]*)", "g");
	var results;
	var ret = [];
	//https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/exec
	while ((results = regex.exec(queryString)) !== null) {
		//ret.push(results[1]);
		var key = decodeURIComponent(results[1].replace(/\+/g," "));
		var value = decodeURIComponent(results[2].replace(/\+/g," "));
		var obj = {} ; obj[key] = value;
		ret.push( obj );
	}
	//return results === null ? "" : decodeURIComponent(results[1].replace(/\+/g," "));
	return ret;
	// test: getParameterNamesStartWith('',document.location.href)
}

// https://stackoverflow.com/a/44583973
function removeParameter(name, queryString) {
	const params = new URLSearchParams(queryString);
	//console.log("removeParameter[0]:", params.toString());
	params.delete(name);
	//console.log("removeParameter[1]:", params.toString());
	return params.toString();
}

// intersect 2 arrays: keey order of 1st array
function arrayIntersect(a, b) {
	var ret = [];
	for(var i=0;i<a.length;i++) {
		if(b.indexOf(a[i])>=0) { ret.push(a[i]); }
	}
	return ret;
}

function arrayContainsRetLabels(a, b, lContains, lNoContains) {
	var ret = [];
	if(typeof lContains == 'undefined') { lContains = true; }
	if(typeof lNoContains == 'undefined') { lNoContains = false; }
	
	for(var i=0;i<a.length;i++) {
		if(b.indexOf(a[i])>=0) {
			ret.push(lContains);
		}
		else {
			ret.push(lNoContains);
		}
	}
	return ret;
}

// https://stackoverflow.com/a/14794066/616413
function isInteger(value) {
	//return x % 1 === 0;
	return !isNaN(value) && 
		parseInt(Number(value)) == value && 
		!isNaN(parseInt(value, 10));	
}

// https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toISOString
function formatDate(date) {
	function pad(number) {
		if (number < 10) {
			return '0' + number;
		}
		return number;
	}
	
	return date.getFullYear() +
		'-' + pad(date.getMonth() + 1) +
		'-' + pad(date.getDate()) +
		' ' + pad(date.getHours()) +
		':' + pad(date.getMinutes()) +
		':' + pad(date.getSeconds());
}

// https://stackoverflow.com/questions/8511281/check-if-a-value-is-an-object-in-javascript
function isObject(obj) {
	return obj === Object(obj);
}

// ---------- messages ----------

function getTextHeader(text) {
	if(! text) { return "[null]"; }
	var idx = text.indexOf("\n\n");
	if(idx>0) { return text.substring(0, idx); }
	return text;
}

function showInfoMessages(messagesId, text) {
	var m = document.getElementById(messagesId);
	m.innerHTML = "<span>"+getTextHeader(text)+"</span><input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x'/>";
	m.classList.add('info');
	m.classList.remove('warn');
	m.classList.remove('error');
	//m.setAttribute('class', 'info');
	var display = 'block';
	if(m.tagName=='SPAN') { display = 'inline-block'; }
	m.style.display = display;
	updateUI();
}

function showWarnMessages(messagesId, text) {
	var m = document.getElementById(messagesId);
	m.innerHTML = "<span>"+getTextHeader(text)+"</span><input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x'/>";
	if(m.classList) {
		m.classList.remove('info');
		m.classList.add('warn');
		m.classList.remove('error');
	}
	var display = 'block';
	if(m.tagName=='SPAN') { display = 'inline-block'; }
	m.style.display = display;
	updateUI();
}

function showErrorMessages(messagesId, text) {
	var m = document.getElementById(messagesId);
	m.innerHTML = "<span>"+getTextHeader(text)+"</span><input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x'/>";
	m.classList.remove('info');
	m.classList.remove('warn');
	m.classList.add('error');
	//var display = 'initial';
	var display = 'block';
	if(m.tagName=='SPAN') { display = 'inline-block'; }
	m.style.display = display;
	updateUI();
}

function appendMessages(messagesId, text) {
	//var m = document.getElementById(messagesId);
	var span = document.querySelector('#'+messagesId+" span");
	span.innerHTML += text;
	updateUI();
}

function changeMessagesClass(messagesId, clazz) {
	var m = document.getElementById(messagesId);
	m.setAttribute('class', clazz);
}

function closeMessages(messagesId) {
	var m = document.getElementById(messagesId);
	m.innerHTML = '';
	m.style.display = 'none';
	updateUI();
}

function showDialogMessage(text) {
	var dialogCont = document.getElementById('dialog-container');
	dialogCont.style.display = 'block';
	var dialog = document.getElementById('dialog');
	dialog.style.display = 'block';
	dialog.innerHTML = "<div style='font-size: large'> " +
		text +
		"<input type='button' value='X' class='simplebutton' style='float: right;' onclick='closeDialogMessage();'/></div>";
	updateUI();
}

function closeDialogMessage() {
	document.getElementById('dialog-container').style.display = 'none';
}

// ---------- cookie functions ----------

// Set a cookie
function setCookie(name, value, days, path = "/") {
	if (!name) { return; }
	let expires = "";
	if (typeof days === "number" && Number.isFinite(days)) {
		const date = new Date(Date.now() + days * 24 * 60 * 60 * 1000);
		expires = "; expires=" + date.toUTCString();
	}
	const secure = (typeof location !== "undefined" && location.protocol === "https:") ? "; Secure" : "";
	const encodedValue = encodeURIComponent(value == null ? "" : value);
	document.cookie = `${encodeURIComponent(name)}=${encodedValue}${expires}; path=${path}; SameSite=Lax${secure}`;
	//console.log('setCookie: name=', name, '; value=', encodedValue, '; expires=', expires, '; secure=', secure);
}

// Get a cookie by name
function getCookie(name) {
	if (!name || !document.cookie) { return null; }
	const nameEQ = encodeURIComponent(name) + "=";
	const cookies = document.cookie.split(";");
	for (let cookie of cookies) {
		cookie = cookie.trim();
		if (cookie.startsWith(nameEQ)) {
			const rawValue = cookie.substring(nameEQ.length);
			try {
				return decodeURIComponent(rawValue);
			} catch {
				return rawValue;
			}
		}
	}
	return null;
}

// Delete a cookie
function deleteCookie(name, path = "/") {
	if (!name) { return; }
	const secure = (typeof location !== "undefined" && location.protocol === "https:") ? "; Secure" : "";
	document.cookie = `${encodeURIComponent(name)}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; path=${path}; SameSite=Lax${secure}`;
}

// ---------- select's functions ----------

function loadSelect(data, selectId, callbackName, callbackValue, dataClasses) {
	if(!callbackName) { callbackName = function(obj) { return obj; } }
	if(!callbackValue) { callbackValue = function(obj) { return obj; } }
	var sel = document.getElementById(selectId);
	var prevOptionValue = sel.value;
	sel.innerHTML = '';
	if(data==null) { console.log('null data; selectId: ',selectId); return; }
	for(var i=0;i<data.length;i++) {
		var name = callbackName(data[i]);
		if(name==null) { continue; }
		var optionValue = callbackValue(data[i]);
		var optionClass = dataClasses&&dataClasses[i]?dataClasses[i]:null;
		//console.log('loadSelect:: ',selectId,optionValue,prevOptionValue);
		sel.insertAdjacentHTML('beforeend', "<option value='"+optionValue+"'"+
				(optionClass?" class='"+optionClass+"'":"")+
				(optionValue==prevOptionValue?" selected":"")+
				">"+name+"</option>");
	}
}

function updateSelectValue(select, value) {
	//console.log("updateSelectValue", select, value, select.selectedIndex);
	var prevIdx = select.selectedIndex;
	for(var i=0;i<select.options.length;i++) {
		if(select.options[i].value == value) {
			select.selectedIndex = i;
			if(prevIdx!=i) { return true; }
			return false;
		}
	}
	return false;
}

function getSelectMultiOptions(selectId) {
	var select = document.getElementById(selectId);
	var schemaOptions = select.selectedOptions;
	var schemas = [];
	for(var i=0; i<schemaOptions.length ; i++) {
		schemas.push(schemaOptions[i].value); 
	}
	return schemas;
}

function setSelectMultiOptions(select, values) {
	for(var i=0 ; i<select.options.length ; i++) {
		for(var j=0 ; j<values.length ; j++) {
			if(select.options[i] && values[j] && (select.options[i].value==values[j]) ) {
				select.options[i].selected = true;
				//console.log('opt match! ', i, j, select.options[i], '/', values[j]);
			}
		}
	}
}
