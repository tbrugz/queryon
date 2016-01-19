/* 
 */

function btnActionStart(btnId) {
	document.getElementById(btnId).classList.add("onaction");
	//XXX add hourglass-like icon? css transitions?
}

function btnActionStop(btnId) {
	document.getElementById(btnId).classList.remove("onaction");
}
