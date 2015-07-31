console.log("table.js loaded");

//document.addEventListener("DOMContentLoaded", function(event) {

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
		var rows = content.querySelectorAll('tr');
		var urlPrepend = typeof(getCurrentRelation) == "function" ? queryOnUrl + "/" + getId(getCurrentRelation('objects')) : "";
		for(var i=1;i<rows.length;i++) {
			var row = rows[i];
			for(var ci=0;ci<blobIndexes.length;ci++) {
				if(row.children[blobIndexes[ci]].getAttribute("null")==null) {
					var currval = row.children[blobIndexes[ci]].innerHTML;
					//XXX add filters!
					rows[i].children[blobIndexes[ci]].innerHTML = "<a href=\""+urlPrepend
						+"?limit=1&offset="+(i-1)+"&valuefield="+blobNames[ci]
						+"&filename=qon-"+tableName+"-"+blobNames[ci]+"-"+i+".blob"
						+"\">"+currval+"</a>";
				}
			}
		}
	}

};

createBlobLinks();

