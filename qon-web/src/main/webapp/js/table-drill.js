
function getNodeIndex(cell) {
	var idx = 0;
	while(cell.previousElementSibling) {
		cell = cell.previousElementSibling;
		idx++;
	}
	return idx; 
}

function getColumnIndex(cell) {
	var idx = 0;
	while(cell.previousElementSibling) {
		cell = cell.previousElementSibling;
		if(cell.getAttribute("dimoncol") != null) {
			//console.log("has dimoncol");
			break;
		}
		idx++;
		//var colname = cell.parentNode.getAttribute("colname");
	}
	return idx; 
}

function getRowIndex(cell) {
	var idx = 0;
	var nodeIdx = getNodeIndex(cell);
	var row = cell.parentNode;
	while(row.previousElementSibling) {
		row = row.previousElementSibling;
		if(row.getAttribute("colname") != null || row.getAttribute("measuresrow") != null ||
			(row.children[nodeIdx] && row.children[nodeIdx].tagName == 'TH') ) {
			//console.log("has colname or measuresrow", row);
			break;
		}
		idx++;
	}
	return idx;
}

function getColDimValues(table, colN) {
	var ret = {};
	for(var i=0;i<table.children.length;i++) {
		var row = table.children[i];
		if(row.getAttribute("colname")) {
			var dim = row.getAttribute("colname");
			//console.log("colname", dim, row.children.length);
			var idx = -1;
			for(var j=0;j < row.children.length; j++) {
				var cell = row.children[j];
				if(cell.getAttribute("dimoncol")) { continue; }
				var width = cell.getAttribute("colspan");
				if(! width) { width = 1; }
				else { width = parseInt(width); }
				idx += width;
				//console.log("idx", idx);
				if(colN <= idx) {
					ret[dim] = cell.innerText;
					break;
				}
			}
		}
	}
	return ret;
}

function getRowDimValues(table, rowN) {
	var ret = {};
	var keys = [];
	var keysRowIndex = -1;
	var keysValues = {};
	var rowCount = -1;
	for(var i=0;i<table.children.length;i++) {
		var row = table.children[i];
		if(row.getAttribute("measuresrow") && !row.getAttribute("colname")) { continue; }
		for(var j=0;j < row.children.length; j++) {
			var cell = row.children[j];
			var blank = cell.getAttribute("class") == "blank";
			if(blank) { break; }
			if(cell.tagName == 'TH') {
				var dim = cell.innerText;
				keys.push(dim);
				keysRowIndex = i;
			}
		}
	}
	//console.log("keys=", keys, "keysRowIndex=", keysRowIndex);
	rowCount = keys.length;
	
	var row = table.children[keysRowIndex+1];
	var keysMaxColIndex = -1;
	for(var j=0;j < row.children.length; j++) {
		var cell = row.children[j];
		var dimoncol = cell.getAttribute("dimoncol");
		if(!dimoncol) {
			keysMaxColIndex = j;
			break;
		}
	}
	keys = keys.slice(0, keysMaxColIndex);
	//console.log("keysMaxColIndex=", keysMaxColIndex, "keys=", keys);
	for(var i=0;i<keys.length;i++) {
		keysValues[keys[i]] = [];
	}

	for(var i=keysRowIndex+1;i<table.children.length;i++) {
		var row = table.children[i];
		if(row.getAttribute("measuresrow") && !row.getAttribute("colname")) { continue; }
		for(var j=0;j < row.children.length; j++) {
			var cell = row.children[j];
			var dimoncol = cell.getAttribute("dimoncol");
			//var blank = cell.getAttribute("class") == "blank";
			if(!dimoncol) { break; }
			
			var dimIdx = j + rowCount - row.children.length;
			var dim = keys[dimIdx];
			//var dim = cell.innerText;
			//var dim = cell.getAttribute("colname");
			if(!dim) { dim = "xxx"; }
			//console.log("j=", j, "dim=", dim);
			
			var height = cell.getAttribute("rowspan");
			if(! height) { height = 1; }
			else { height = parseInt(height); }
			//console.log("i=", i, "j=", j, "dimIdx=", dimIdx, "dim=", dim, "height=", height);
			
			for(var z=0;z<height;z++) {
				keysValues[dim].push(cell.innerText);
			}
		}
	}
	
	for(var i=0;i<keys.length;i++) {
		ret[keys[i]] = keysValues[keys[i]][rowN];
	}
	
	//console.log("keys=", keys, "keysValues=", keysValues, "rowN=", rowN, "ret=", ret);
	return ret;
}

function getTableDimValues(table, cell) {
	var colDimVals = getColDimValues(table, getColumnIndex(cell));
	var rowDimVals = getRowDimValues(table, getRowIndex(cell));
	//console.log("... cOT getColDimValues=", colDimVals);
	//console.log("... cOT getRowDimValues=", rowDimVals);
	return Object.assign({}, colDimVals, rowDimVals);
}
