console.log("table.js loaded");

function getQueryString() {
	var urlpl = document.getElementById("url-permalink");
	var href = urlpl ? urlpl.href : location.search;
	return href.indexOf("?")==-1?"":href.substr(href.indexOf("?"));
}

//see: http://stackoverflow.com/questions/901115/how-can-i-get-query-string-values-in-javascript
function getParameterByName(name, queryString) {
	name = name.replace(/[\[]/, "\\[").replace(/[\]]/, "\\]");
	var regex = new RegExp("[\\?&]" + name + "=([^&#]*)"),
		results = regex.exec(queryString);
	return results === null ? "" : decodeURIComponent(results[1].replace(/\+/g," "));
}

function removeLimitOffset(queryString) {
	var re = /(limit|offset)=\d+/g;
	queryString = queryString.replace(re, '');
	var re2 = /\?&/;
	queryString = queryString.replace(re2, '?');
	var re3 = /&+/;
	queryString = queryString.replace(re3, '&');
	if(queryString=='?') { return ''; }
	return queryString;
}

function createBlobLinks() {
	var content = document.getElementsByTagName('table')[0];
	var tableName = content.getAttribute("class");
	if(!tableName) { tableName = "table"; }
	var cols = content.querySelectorAll('colgroup > col');
	var blobNames = [];
	var blobIndexes = [];
	for(var i=0;i<cols.length;i++) {
		if(cols[i].getAttribute("type")=="Blob") {
			blobNames.push(cols[i].getAttribute("colname"));
			blobIndexes.push(i);
		}
	}
	console.log("table.js: tableName",tableName,"cols",cols,"blobNames",blobNames,"blobIndexes",blobIndexes);
	
	if(blobIndexes.length>0) {
		//XXX get current offset!!!
		var queryString = getQueryString();
		var currentOffset = getParameterByName("offset", queryString);
		currentOffset = parseInt(currentOffset);
		if(!currentOffset) { currentOffset = 0; }
		queryString = removeLimitOffset(queryString);
		//if(!Number.isInteger(currentOffset)) { currentOffset = 0; }
		
		var rows = content.querySelectorAll('tr');
		var urlPrepend = location.pathname + queryString;
		if(typeof(getCurrentRelation) == "function") {
			urlPrepend = queryOnUrl + "/" + getId(getCurrentRelation('objects')) + queryString;
		}
		console.log("table.js: currentOffset", currentOffset, "queryString", queryString, "urlPrepend", urlPrepend);
		for(var i=1;i<rows.length;i++) {
			var row = rows[i];
			for(var ci=0;ci<blobIndexes.length;ci++) {
				if(row.children[blobIndexes[ci]].getAttribute("null")==null) {
					var currval = row.children[blobIndexes[ci]].innerHTML;
					var offset = (i-1+currentOffset);
					rows[i].children[blobIndexes[ci]].innerHTML = "<a href=\""+urlPrepend
						+ (queryString?"&":"?")
						+"limit=1&offset="+offset+"&valuefield="+blobNames[ci]
						+"&filename=queryon_"+tableName+"_"+blobNames[ci]+"_"+(offset+1)+".blob"
						+"\">"+currval+"</a>";
				}
			}
		}
	}

};

// http://stackoverflow.com/questions/9457891/how-to-detect-if-domcontentloaded-was-fired
if (document.readyState == "complete" || document.readyState == "loaded") {
	createBlobLinks();
}
else {
	document.addEventListener("DOMContentLoaded", createBlobLinks);
}
