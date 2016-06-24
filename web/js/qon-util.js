/* 
 */

var byId = function (id) { return document.getElementById(id); }

function btnActionStart(btnId) {
	document.getElementById(btnId).classList.add("onaction");
	//XXX add hourglass-like icon? css transitions?
}

function btnActionStop(btnId) {
	document.getElementById(btnId).classList.remove("onaction");
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

// ---------- messages ----------

function showInfoMessages(messagesId, text) {
	var m = document.getElementById(messagesId);
	m.innerHTML = "<span>"+text+"</span><input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>";
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
	m.innerHTML = "<span>"+text+"</span><input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>";
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
	m.innerHTML = "<span>"+text+"</span><input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>";
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
