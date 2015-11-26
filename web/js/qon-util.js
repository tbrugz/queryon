/* 
 * depends on jquery
 */

function btnActionStart(btnId) {
	$('#'+btnId).addClass('onaction');
	//XXX add hourglass-like icon? css transitions?
}

function btnActionStop(btnId) {
	$('#'+btnId).removeClass('onaction');
}
