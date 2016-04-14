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

function showInfoMessages(messagesId, text) {
	var m = document.getElementById(messagesId);
	m.innerHTML = "<span>"+text+"</span><input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>";
	m.setAttribute('class', 'info');
	m.style.display = 'block';
	updateUI();
}

function showWarnMessages(messagesId, text) {
	var m = document.getElementById(messagesId);
	m.innerHTML = "<span>"+text+"</span><input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>";
	m.setAttribute('class', 'warn');
	m.style.display = 'block';
	updateUI();
}

function showErrorMessages(messagesId, text) {
	var m = document.getElementById(messagesId);
	m.innerHTML = "<span>"+text+"</span><input type='button' class='closebutton' onclick=\"javascript:closeMessages('"+messagesId+"')\" value='x' float='right'/>";
	m.setAttribute('class', 'error');
	m.style.display = 'block';
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
