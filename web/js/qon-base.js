/*
 * should not depend on anything...
 */

function getQonData(qonData) {
	var keys = Object.keys(qonData);
	var index = 0;
	if(keys[index].startsWith("$") || keys[index].startsWith("@")) { index = 1; }
	return qonData[keys[index]];
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
