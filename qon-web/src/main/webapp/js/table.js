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
			urlPrepend = queryOnUrl + "/" + getId(getCurrentRelation('objects')) + getParameters(true) + queryString;
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
					var qs = "limit=1&offset="+offset+"&valuefield="+blobNames[ci] +
						"&filename=queryon_"+tableName+"_"+blobNames[ci]+"_"+(offset+1)+"."+fileExt;

					if(typeof downloadBlob == "function") {
						row.children[blobIndexes[ci]].innerHTML = "<a href=\"javascript:downloadBlob('" +
							qs + "');\">"+currval+"</a>";
					}
					else {
						row.children[blobIndexes[ci]].innerHTML = "<a href=\""+urlPrepend +
							(queryString?"&":"?") + qs +
							"\" target=\"_blank\">"+currval+"</a>";
					}
					
					}
				}
				rownum++;
			}
		}
	}
}

function mergeDimensions() {
	var content = document.getElementsByTagName('table')[0];
	if(!content) {
		//console.log('table.js: mergeDimensions: no table found...');
		return;
	}
	var trs = content.querySelectorAll("tr");

	mergeRowDimensions(content, trs);
	mergeColumnDimensions(content, trs);
}

function getLastDimRow(trs) {
	var lastDimRow = -1;
	for(var i=0;i<trs.length;i++) {
		var colname = trs[i].getAttribute("colname");
		var measuresrow = trs[i].getAttribute("measuresrow");
		//console.log("i==", i, "colname", colname, "measuresrow", measuresrow);
		if(!colname && !measuresrow) { break; }
		else { lastDimRow = i; }
	}
	return lastDimRow
}

function mergeRowDimensions(content, trs) {
	var iniTime = +new Date();
	// rows...
	var lastDimRow = getLastDimRow(trs);
	//console.log("mergeDimensions: lastDimRow=",lastDimRow);
	if(lastDimRow<=0) { return; }
	
	var merges = 0;
	
	for(var i=lastDimRow ; i>=0 ; i--) {
		var colname = trs[i].getAttribute("colname");
		var measuresrow = trs[i].getAttribute("measuresrow");
		//console.log("i",i,"colname",colname,"measuresrow",measuresrow);
		if(!colname && !measuresrow) { continue; }
		var ths = trs[i].querySelectorAll("th");
		//console.log(i,"colname:",colname,"ths",ths);
		loop1:
		for(var j=ths.length-1;j>=0;j--) {
			if(colname) { ths[j].setAttribute("title", "dim: "+colname); }
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
				merges++;
			}
		}
	}
	console.log("mergeRowDimensions: lastDimRow=",lastDimRow," ; #merges=",merges," ; elapsed=",((+new Date())-iniTime));
}

/*
function mergeDimensions4test() {
	var content = document.getElementsByTagName('table')[0];
	if(!content) {
		console.log('table.js: mergeDimensions: no table found...');
		return;
	}
	var trs = content.querySelectorAll("tr");

	mergeRowDimensions(content, trs);
	mergeColumnDimensions(content, trs);
}
*/

function mergeColumnDimensions(content, trs) {
	var iniTime = +new Date();
	
	var lastDimRow = getLastDimRow(trs);
	
	// cols...
	var lastDimCol = -1;
	//console.log("mergeColumnDimensions: lastDimRow=",lastDimRow,"rows=",trs.length);
	if(trs.length<=1) { return; }
	
	var headerRows = (lastDimRow > 0) ? lastDimRow : 1;
	var dimRow = trs[headerRows];
	var dimRowCols = dimRow.querySelectorAll("th,td");
	for(var i=0;i<dimRowCols.length;i++) {
		//console.log("dimRowCols[",i,"]: ",dimRowCols[i]);
		/*if(dimRowCols[i].getAttribute("measuresrow")!=null) {
			continue;
		}*/
		var dimoncol = dimRowCols[i].getAttribute("dimoncol");
		//console.log(i, dimoncol);
		if(dimoncol) { lastDimCol = i; }
	}
	if(lastDimCol<=0) { return; }
	//console.log("mergeColumnDimensions: lastDimCol=",lastDimCol,"rows=",trs.length,"cols=",dimRowCols.length,"headerRows=",headerRows);

	var allTds = [];
	for(var i=lastDimCol ; i>=0 ; i--) {
		//console.log("cols: i=", i);
		allTds[i] = content.querySelectorAll("td:nth-of-type("+(i+1)+")");
	}
	
	var merges = 0;

	loop1:
	for(var i=lastDimCol ; i>=0 ; i--) {
		//console.log("cols: i=", i);
		var tds = allTds[i];
		//var tds = content.querySelectorAll("td:nth-of-type("+(i+1)+")");
		//console.log(i, "tds", tds);
		loop2:
		for(var j=tds.length-1;j>=0;j--) {
			if( (typeof tds[j].innerText !== 'undefined') && tds[j-1] && (tds[j].innerText===tds[j-1].innerText) ) {
				//XXXdone do not merge if upper row columns are not equally merged
				//var innerText = null;
				for(var z=i; z>=0 ; z--) {
					//var tdsParent = content.querySelectorAll("td:nth-of-type("+(z)+")");
					var tdsParent = allTds[z];
					if((typeof tdsParent[j] !== 'undefined') && (typeof tdsParent[j-1] !== 'undefined')) {
						if( tdsParent[j].innerText === tdsParent[j-1].innerText ) {
							//innerText = tdsParent[j].innerText;
						}
						else {
							//console.log("parent is not equal: ", tdsParent[j].innerText, tdsParent[j-1].innerText, "text=", tds[j].innerText);
							continue loop2;
						}
					}
					else {
						//console.log("undefined parents: row(i)=", i ,"col(j)=", j, "z=", z);
					}
				}
				
				var rspan = tds[j].getAttribute("rowspan");
				if(!rspan) { rspan = "2"; }
				else { rspan = ""+(parseInt(rspan)+1); }
				//console.log("will merge: row(i)=", i ,"col(j)=", j, "rspan", rspan, "content=", tds[j].innerText);
				tds[j-1].setAttribute("rowspan", rspan);
				tds[j].remove();
				merges++;
			}
			else {
				//continue loop1;
			}
		}
	}
	
	console.log("mergeColumnDimensions: lastDimCol(+1)=",(lastDimCol+1)," ; #merges=",merges," ; elapsed=",((+new Date())-iniTime));
}

function getCssRuleBySelectorText(selector) {
	for(var i=0; i<document.styleSheets.length; i++) {
		var sheet = document.styleSheets[i];
		try {
			if(sheet && sheet.cssRules) {
				for(var j=0; j<sheet.cssRules.length; j++) {
					if(sheet.cssRules[j].selectorText == selector) {
						return sheet.cssRules[j];
					}
				}
			}
		}
		catch(error) {
			// XXX firefox may generate an error...
			//console.warn("Error @ getCssRuleBySelectorText[",i,"]: ",error);
		}
	}
	return null;
}

function applyTableStyles() {
	var row1 = document.getElementsByTagName('tr')[0];
	if(!row1) { return; }
	var rule = getCssRuleBySelectorText("th.break");
	if(!rule) { return; }
	rule.style.top = row1.offsetHeight+"px";
}

function doTableOnLoad() {
	createBlobLinks();
	mergeDimensions();
	applyTableStyles();
	console.log("table.js: doTableOnLoad() finished");
}

// http://stackoverflow.com/questions/9457891/how-to-detect-if-domcontentloaded-was-fired
if (document.readyState == "complete" || document.readyState == "loaded") {
	doTableOnLoad();
}
else {
	document.addEventListener("DOMContentLoaded", doTableOnLoad);
}
