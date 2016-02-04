/* 
 */

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
