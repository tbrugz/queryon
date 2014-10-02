
function btnActionStart(btnId) {
	$('#'+btnId).addClass('onaction');
	//XXX add hourglass-like icon?
}

function btnActionStop(btnId) {
	$('#'+btnId).removeClass('onaction');
}
