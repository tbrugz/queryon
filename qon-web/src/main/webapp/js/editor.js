
function checkForSqlWarnings(request, editor) {
	var range = null;
	var position = parseInt(request.getResponseHeader('X-Warning-SQL-Position'));
	if(position) {
		//console.log("checkForPosition:: position = ", position);
		if(isTextSelected(editor)) {
			diff = getCharCountPrecedingSelection(editor);
			//console.log("isTextSelected:: checkForPosition position = ", position, "; diff = ", diff);
			position += diff;
		}
		range = getRangeFromPosition(editor, position);
	}
	var line = parseInt(request.getResponseHeader('X-Warning-SQL-Line'));
	if(line) {
		//console.log("checkForPosition:: line = ", line);
		if(isTextSelected(editor)) {
			diff = getRowCountPrecedingSelection(editor);
			//console.log("isTextSelected:: checkForPosition line = ", line, "; diff = ", diff);
			line += diff;
		}
		range = getRangeFromLine(editor, line);
	}
	
	if(range) {
		console.log("checkForPosition range = ", range);
		editor.warningMarker = editor.session.addMarker(range, "editor-warning", "line");
	}
}

function getRangeFromPosition(editor, position) {
	position = parseInt(position);
	// https://stackoverflow.com/questions/27531860/how-to-highlight-a-certain-line-in-ace-editor
	//console.log("position", position);
	var Range = ace.require('ace/range').Range;

	var sqlString = editor.getValue();
	
	var partz = sqlString.split("\n")
	var newpos = 0;
	var row=0, startColumn=0, endColumn=0;
	for(var i=0;i<partz.length;i++) {
		newpos += partz[i].length + 1;
		//console.log("newpos", newpos);
		if(newpos > position) {
			row = i;
			//console.log("addMarker: found row/col: newpos", newpos, "partz[i].length", partz[i].length, "position", position);
			startColumn = partz[i].length - newpos + position;
			endColumn = partz[i].length;
			break;
		} 
	}
	console.log("getRangeFromPosition: row", row, "startColumn", startColumn, "endColumn", endColumn, "position", position);
	return new Range(row, startColumn, row, endColumn);
}

function getRangeFromLine(editor, line) {
	line = parseInt(line)-1;
	var Range = ace.require('ace/range').Range;
	
	var sqlString = editor.getValue();
	var partz = sqlString.split("\n")
	
	console.log(partz);
	console.log("getRangeFromLine: line", line, "partz[line].length", partz[line].length);
	return new Range(line, 0, line, partz[line].length);
}

function removeMarker(editor) {
	if(editor.warningMarker) {
		editor.session.removeMarker(editor.warningMarker);
	}
}

function getEditorText(editor) {
	var sqlString = editor.getSelectedText();
	if(!sqlString) { sqlString = editor.getValue(); }
	return sqlString;
}

function isTextSelected(editor) {
	var range = editor.getSelectionRange();
	return ! ( (range.start.row == range.end.row) && (range.start.column == range.end.column) );
}

function getRowCountPrecedingSelection(editor) {
	return editor.getSelectionRange().start.row;
}

function getCharCountPrecedingSelection(editor) {
	var count = 0;
	var start = editor.getSelectionRange().start;
	for(var i=0;i<start.row;i++) {
		count += editor.session.getLine(i).length + 1;
	}
	count += start.column;
	return count;
}
