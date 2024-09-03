
// ␀ - &#9216; - \u2400
const DIM_NULL_VALUE = '\u2400';

function getNodeIndex(cell) {
	var idx = 0;
	while(cell.previousElementSibling) {
		cell = cell.previousElementSibling;
		idx++;
	}
	return idx; 
}

function getColumnIndex(cell) {
	if(cell.getAttribute("dimoncol") != null) {
		//is dimoncol
		return null;
	}
	
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
	if(row.getAttribute("colname") != null || row.getAttribute("measuresrow") != null ||
		(row.children[nodeIdx] && row.children[nodeIdx].tagName == 'TH') ) {
		//is dimonrow
		return null;
	}
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

function getColumnCount(row) {
	while(row.previousElementSibling) {
		row = row.previousElementSibling;
	}
	var count =0;
	for(var i=0;i<row.children.length;i++) {
		var cell = row.children[i];
		var width = cell.getAttribute("colspan");
		if(! width) { width = 1; }
		else { width = parseInt(width); }
		count += width;
	}
	return count;
}

function getDimColumnIndex(cell) {
	var idx = 0;
	while(cell.previousElementSibling) {
		var width = cell.getAttribute("colspan");
		if(! width) { width = 1; }
		else { width = parseInt(width); }
		idx += width;
		cell = cell.previousElementSibling;
		//idx++;
	}
	return idx;
}

function getDimRowDimIndex(cell) {
	var row = cell.parentNode;
	var columnCount = getColumnCount(row); //row.children.length;
	var initialRowCount = row.children.length;
	var nodeIndex = getNodeIndex(cell);
	return columnCount - initialRowCount + nodeIndex;
}

function getDimColDimIndex(cell) {
	var row = cell.parentNode;
	var count = 0;
	while(row.previousElementSibling) {
		var colname = row.getAttribute("colname");
		if(colname) { count++; }
		row = row.previousElementSibling;
	}
	return count;
}

function getDimRowIndex(cell) {
	var idx = 0;
	var row = cell.parentNode;
	//var columnCount = getColumnCount(row); //row.children.length;
	var initialRowCount = row.children.length;
	var cellIdx = getNodeIndex(cell);
	var count = 0;
	while(row.previousElementSibling) {
		//if(row.children.length == columnCount) {
		var colIdx = cellIdx + (row.children.length - initialRowCount);// + (row.children.length - columnCount);
		if(colIdx>=0) {
			//if(colIdx<0) {colIdx = 0;}
			//if(count == 0 && row.children.length == columnCount) { colIdx = cellIdx; }
			var cellx = row.children[colIdx];
			if(!cellx) {
				console.log("getDimRowIndex: ERR colIdx=", colIdx, "initialRowCount=", initialRowCount, "cellIdx=", cellIdx, "row.children.length=", row.children.length);
			}
			//console.log("getDimRowIndex: colIdx=", colIdx, "initialRowCount=", initialRowCount, "cellIdx=", cellIdx, "row.children.length=", row.children.length, "text=", cellx.innerText);
			if(!cellx.getAttribute("measure")) {
				var height = cellx.getAttribute("rowspan");
				if(! height || (count == 0)) { height = 1; }
				else { height = parseInt(height); }
				//console.log("getDimRowIndex: height=", height, "cellIdx=", cellIdx, "count=", count, "text=", cellx.innerText);
				idx += height;
			}
		}
		row = row.previousElementSibling;
		count++;
		//idx++;
	}
	return idx;
}

function getColDimValues(table, colN) {
	var ret = [];
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
				//console.log("idx=", idx, "width=", width, "cell=", cell);
				if(colN <= idx) {
					//ret[dim] = cell.innerText;
					const map = {};
					map[dim] = cell.innerText;
					ret.push(map);
					break;
				}
			}
		}
	}
	return ret;
}

function getRowDimValues(table, rowN) {
	var ret = [];
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
		//console.log("i=", i, "keys=", keys[i]);
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
			if(dimIdx<0) {
				//console.warn("dimIdx [=",dimIdx," ; i=",i,"] should be >= 0");
				//console.log("row.children.length=", row.children.length, "row.children=", row.children);
				dimIdx = 0;
			}
			var dim = keys[dimIdx];
			//var dim = cell.innerText;
			//var dim = cell.getAttribute("colname");
			if(!dim) { dim = "xxx"; }
			//console.log("j=", j, "dimIdx=", dimIdx, "dim=", dim, "j=", j, "rowCount=", rowCount, "row.children.length=", row.children.length);
			
			var height = cell.getAttribute("rowspan");
			if(! height) { height = 1; }
			else { height = parseInt(height); }
			//console.log("i=", i, "j=", j, "dimIdx=", dimIdx, "dim=", dim, "height=", height);
			
			for(var z=0;z<height;z++) {
				var isnull = cell.getAttribute("null");
				if(isnull=="true") {
					keysValues[dim].push(DIM_NULL_VALUE);
				}
				else {
					keysValues[dim].push(cell.innerText);
				}
			}
		}
	}
	
	for(var i=0;i<keys.length;i++) {
		//ret[keys[i]] = keysValues[keys[i]][rowN];
		const kk = keys[i];
		const map = {};
		map[kk] = keysValues[kk][rowN];
		ret.push(map);
	}
	
	//console.log("keys=", keys, "keysValues=", keysValues, "rowN=", rowN, "ret=", ret);
	return ret;
}

function getTableDimValues(table, cell) {
	const colIndex = getColumnIndex(cell);
	const rowIndex = getRowIndex(cell);
	var colDimVals = colIndex!=null ? getColDimValues(table, colIndex) : [];
	var rowDimVals = rowIndex!=null ? getRowDimValues(table, rowIndex) : [];
	//console.log("... cOT getColDimValues0=", colDimVals);
	//console.log("... cOT getRowDimValues0=", rowDimVals);

	const rowDimIndex = getDimRowIndex(cell)-1;
	const colDimIndex = getDimColumnIndex(cell)-1;
	//console.log("... rowDimIndex=", rowDimIndex, "colDimIndex=", colDimIndex);
	if(colIndex==null) {
		rowDimVals = getRowDimValues(table, rowDimIndex);
		var dimRowDimIndex = getDimRowDimIndex(cell);
		//console.log("... >> rowDimIndex=", rowDimIndex, "getColumnIndex=", getColumnIndex(cell), "dimRowDimIndex=", dimRowDimIndex);
		rowDimVals = rowDimVals.slice(0, dimRowDimIndex+1);
	}
	if(rowIndex==null) {
		colDimVals = getColDimValues(table, colDimIndex-1);
		var dimColDimIndex = getDimColDimIndex(cell);
		//console.log("... >> colDimIndex=", colDimIndex, "dimColDimIndex=", dimColDimIndex);
		colDimVals = colDimVals.slice(0, dimColDimIndex);
	}
	//console.log("... cOT getColDimValues=", colDimVals);
	//console.log("... cOT getRowDimValues=", rowDimVals);

	//return Object.assign({}, colDimVals, rowDimVals);
	var ret = {};
	for(var i=0;i<colDimVals.length;i++) {
		ret[Object.keys(colDimVals[i])[0]] = Object.values(colDimVals[i])[0];
		//console.log("...C",i," -- ", Object.keys(colDimVals[i])[0], " -- ", Object.values(colDimVals[i])[0]);
	}
	for(var i=0;i<rowDimVals.length;i++) {
		ret[Object.keys(rowDimVals[i])[0]] = Object.values(rowDimVals[i])[0];
		//console.log("...R",i," -- ", Object.keys(rowDimVals[i])[0], " -- ", Object.values(rowDimVals[i])[0]);
	}
	//console.log("getTableDimValues: ret=", ret);
	return ret;
}
