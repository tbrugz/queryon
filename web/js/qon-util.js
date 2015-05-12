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

function append2url(url, append) {
	if(append==null || append=='') {
		return url;
	}
	
	if(url!=null && url.indexOf("?") > -1) {
		return url+"&"+append;
	}
	/*else if(append!=null && append.indexOf("?")==0) {
		return url+append;
	}*/
	else {
		return url+"?"+append;
	}
}
