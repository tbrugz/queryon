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

/*function getQueryString() {
	var href = location.search;
	return href.indexOf("?")==-1?"":href.substr(href.indexOf("?"));
}*/

//see: http://stackoverflow.com/questions/901115/how-can-i-get-query-string-values-in-javascript
function getParameterByName(name, queryString) {
	name = name.replace(/[\[]/, "\\[").replace(/[\]]/, "\\]");
	var regex = new RegExp("[\\?&]" + name + "=([^&#]*)"),
		results = regex.exec(queryString);
	return results === null ? "" : decodeURIComponent(results[1].replace(/\+/g," "));
}

function getParametersByName(name, queryString) {
	name = name.replace(/[\[]/, "\\[").replace(/[\]]/, "\\]");
	var regex = new RegExp("[\\?&]" + name + "=([^&#]*)", "g");
	var results = regex.exec(queryString);
	if(results === null) { return ""; }
	var ret = [];
	while(results !== null) {
		ret.push( decodeURIComponent(results[1].replace(/\+/g," ")) );
		results = regex.exec(queryString);
	}
	//console.log("getParametersByName", ret);
	return ret;
}

function getParameterNamesStartWith(startWithRegex, queryString) {
	startWithRegex = startWithRegex.replace(/[\[]/, "\\[").replace(/[\]]/, "\\]");
	var regex = new RegExp("[\\?&](" + startWithRegex + ".*?)=([^&#]*)", "g");
	//var results = regex.exec(queryString);
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

// ---------- messages ----------

function getTextHeader(text) {
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
	m.classList.remove('info');
	m.classList.add('warn');
	m.classList.remove('error');
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

// ---------- select's functions ----------

loadSelect = function(data, selectId, callbackName, callbackValue, dataClasses) {
	if(!callbackName) { callbackName = function(obj) { return obj; } }
	if(!callbackValue) { callbackValue = function(obj) { return obj; } }
	var sel = document.getElementById(selectId);
	var prevOptionValue = sel.value;
	$('#'+selectId).empty();
	if(data==null) { console.log('null data; selectId: ',selectId); return; }
	for(var i=0;i<data.length;i++) {
		var name = callbackName(data[i]);
		if(name==null) { continue; }
		var optionValue = callbackValue(data[i]);
		var optionClass = dataClasses&&dataClasses[i]?dataClasses[i]:null;
		//console.log('loadSelect:: ',selectId,optionValue,prevOptionValue);
		$('#'+selectId).append("<option value='"+optionValue+"'"+
				(optionClass?" class='"+optionClass+"'":"")+
				(optionValue==prevOptionValue?" selected":"")+
				">"+name+"</option>");
	}
}

updateSelectValue = function(select, value) {
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
