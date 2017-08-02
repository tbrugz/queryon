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
	if(!content) {
		console.log('table.js: no table found...');
		return;
	}
	var tableName = content.getAttribute("class");
	if(!tableName) { tableName = "table"; }
	var cols = content.querySelectorAll('colgroup > col');
	var blobNames = [];
	var blobIndexes = [];
	var allColNames = [];
	for(var i=0;i<cols.length;i++) {
		if(cols[i].getAttribute("type")=="Blob") {
			blobNames.push(cols[i].getAttribute("colname"));
			blobIndexes.push(i);
		}
		allColNames.push(cols[i].getAttribute("colname"));
	}

	var blobFileExtIndex = [];
	for(var i=0;i<blobNames.length;i++) {
		var idx = allColNames.indexOf(blobNames[i]+"_FILEEXT");
		blobFileExtIndex.push(idx);
	}
	//XXX add blobNames[i]+"_MIMETYPE" ? blobNames[i]+"_FILENAME" ?
	//XXX style: 'display: none' to blobNames[i]+"_FILEEXT" columns?

	//console.log("table.js: tableName",tableName,"cols",cols,"blobNames",blobNames,"blobIndexes",blobIndexes,"blobFileExtIndex",blobFileExtIndex);
	
	if(blobIndexes.length>0) {
		//XXX get current offset!!!
		var queryString = getQueryString();
		var currentOffset = getParameterByName("offset", queryString);
		currentOffset = parseInt(currentOffset);
		if(!currentOffset) { currentOffset = 0; }
		queryString = removeLimitOffset(queryString);
		//if(!Number.isInteger(currentOffset)) { currentOffset = 0; }
		
		// ':scope'? http://stackoverflow.com/questions/3680876/using-queryselectorall-to-retrieve-direct-children
		var rows = content.querySelectorAll('tr');
		//var rows = content.getElementsByTagName('tr');
		var urlPrepend = location.pathname + queryString;
		if(typeof(getCurrentRelation) == "function") {
			urlPrepend = queryOnUrl + "/" + getId(getCurrentRelation('objects')) + getParameters() + queryString;
		}
		console.log("table.js: currentOffset", currentOffset, "queryString", queryString, "urlPrepend", urlPrepend);
		var rownum = 0;
		for(var i=1;i<rows.length;i++) {
			var row = rows[i];
			if(/*row.children[blobIndexes[ci]]!=null && */row.parentNode.parentNode === content) {
				for(var ci=0;ci<blobIndexes.length;ci++) {
					if(row.children[blobIndexes[ci]].getAttribute("null")==null) {
						
					var currval = row.children[blobIndexes[ci]].innerHTML;
					var fileExt = blobFileExtIndex[ci]>=0 ? row.children[blobFileExtIndex[ci]].innerHTML : "blob" ;
					var offset = (rownum+currentOffset);
					row.children[blobIndexes[ci]].innerHTML = "<a href=\""+urlPrepend
						+ (queryString?"&":"?")
						+"limit=1&offset="+offset+"&valuefield="+blobNames[ci]
						+"&filename=queryon_"+tableName+"_"+blobNames[ci]+"_"+(offset+1)+"."+fileExt
						+"\">"+currval+"</a>";
					
					}
				}
				rownum++;
			}
		}
	}
}

function mergeDimensions() {
	var iniTime = +new Date();
	var content = document.getElementsByTagName('table')[0];
	if(!content) {
		//console.log('table.js: mergeDimensions: no table found...');
		return;
	}
	var trs = content.querySelectorAll("tr");
	var lastDimRow = -1;
	for(var i=0;i<trs.length;i++) {
		var colname = trs[i].getAttribute("colname");
		var measuresrow = trs[i].getAttribute("measuresrow");
		if(!colname && !measuresrow) { lastDimRow = i; break; }
	}
	//console.log("mergeDimensions: lastDimRow=",lastDimRow);
	
	for(var i=lastDimRow ; i>=0 ; i--) {
		var colname = trs[i].getAttribute("colname");
		var measuresrow = trs[i].getAttribute("measuresrow");
		if(!colname && !measuresrow) { break; }
		var ths = trs[i].querySelectorAll("th");
		//console.log(i,"colname:",colname,"ths",ths);
		loop1:
		for(var j=ths.length-1;j>=0;j--) {
			if( ths[j].innerText && ths[j-1] && (ths[j].innerText===ths[j-1].innerText) ) {
				//XXXdone do not merge if upper row columns are not equally merged
				for(var z=i-1; z>=0 ; z--) {
					//console.log("test row ",z);
					var zhs =trs[z].querySelectorAll("th");
					if( zhs[j].innerText && zhs[j-1] && (zhs[j].innerText===zhs[j-1].innerText) ) {}
					else {
						//console.log("parent is not equal: ", zhs[j], zhs[j-1]);
						continue loop1;
					}
				}
				
				var cspan = ths[j].getAttribute("colspan");
				//console.log(j, cspan, ths[j-1]);
				if(!cspan) { cspan = "2"; }
				else { cspan = ""+(parseInt(cspan)+1); }
				ths[j-1].setAttribute("colspan", cspan);
				ths[j].remove();
			}
		}
	}
	//console.log("mergeDimensions: lastDimRow=",lastDimRow," ; elapsed="+((+new Date())-iniTime));
}

function doTableOnLoad() {
	createBlobLinks();
	mergeDimensions();
}

// http://stackoverflow.com/questions/9457891/how-to-detect-if-domcontentloaded-was-fired
if (document.readyState == "complete" || document.readyState == "loaded") {
	doTableOnLoad();
}
else {
	document.addEventListener("DOMContentLoaded", doTableOnLoad);
}
