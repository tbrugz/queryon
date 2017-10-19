console.log("table-diff.js loaded");

var doRemoveTrailingWhitespace = true;

byId = function (id) { return document.getElementById(id); };

function getAddElements() {
	return document.querySelectorAll("td > span.add");
}

function addDiffBtns() {
	var adds = getAddElements();
	var count = 0;
	for(var i=0;i<adds.length;i++) {
		//console.log(adds[i]);
		var add = adds[i];
		var remove = add.parentElement.querySelector(".remove")
		var addtxt = add.innerHTML;
		var removetxt = remove.innerHTML;
		// ␀ - &#9216; - \u2400
		if(!addtxt || addtxt=="\u2400" || !removetxt || removetxt=="\u2400") { continue; } 
		//console.log("diff: ", ++count, "id=", i, " -- ", addtxt.substring(0,10), removetxt.substring(0, 10));
		
		var diffbtn = document.createElement('span');
		diffbtn.setAttribute("class", "btndiff");
		diffbtn.setAttribute("onclick", "diffcell("+i+");");
		diffbtn.innerHTML = "diff";  
		add.parentNode.appendChild(diffbtn);
	}
}

function diffcell(i) {
	var adds = getAddElements();
	
	var add = adds[i];
	var remove = add.parentElement.querySelector(".remove")
	
	console.log("add/remove[", i, "]:" , add, remove);
	
	var fromTitle = getParameterByName('modelSource', document.location.search);
	fromTitle = fromTitle?"removed ("+fromTitle+")":"removed";
	var toTitle = getParameterByName('modelTarget', document.location.search);
	toTitle = toTitle?"added ("+toTitle+")":"added";
	
	var diffed = diffUsingJS(0, remove.textContent, add.textContent, fromTitle, toTitle, 'diffoutput', false);
	if(diffed) {
		byId('diffcontrols').innerHTML = "<span class=\"closebtn\" onclick=\"closeDiff();\" title=\"close\">X</span>";
	}
	
	var table = document.querySelectorAll('table')[0];
	table.style.display = 'none';
}

function closeDiff() {
	byId('diffoutputcontainer').style.display='none';
	var table = document.querySelectorAll('table')[0];
	table.style.display = 'block';
}

removeTrailingWhitespace = function(txt) {
	var pattern = /\s*$/g;
	var newtxt = [];
	for(var i=0;i<txt.length;i++) {
		newtxt.push(txt[i].replace(pattern, ''));
	}
	return newtxt;
}

diffHasChanges = function(opcodes) {
	for(var i=0;i<opcodes.length;i++) {
		if(opcodes[i][0]!='equal') {
			return true;
		}
	}
	return false;
}

//diffUsingJS(viewType, from, to,
//		document.getElementById('modelSource').value, document.getElementById('modelTarget').value,
//		'diffoutput', inverse);

diffUsingJS = function(viewType, from, to, fromTitle, toTitle, outputId, invertSides) {
	"use strict";
	//console.log("viewType=",viewType);
	//console.log("diff0: ", from, to);
	var base = difflib.stringAsLines(from),
		newtxt = difflib.stringAsLines(to);

	if(doRemoveTrailingWhitespace) {
		base = removeTrailingWhitespace(base);
		newtxt = removeTrailingWhitespace(newtxt);
	}
	
	var sm = new difflib.SequenceMatcher(base, newtxt),
		opcodes = sm.get_opcodes(),
		diffoutputdiv = byId(outputId),
		contextSize = 3; //null; //byId("contextSize").value;

	diffoutputdiv.innerHTML = "";
	contextSize = contextSize || null;

	//console.log(base, newtxt);
	//diffedLineCount(opcodes);
	
	if(diffHasChanges(opcodes)) {
		diffoutputdiv.appendChild(diffview.buildView({
			baseTextLines: base,
			newTextLines: newtxt,
			opcodes: opcodes,
			baseTextName: fromTitle,
			newTextName: toTitle,
			contextSize: contextSize,
			viewType: viewType,
			invertSides: invertSides
		}));
		byId(outputId).parentNode.style.display='block';
		return true;
		//document.getElementById('togglediffbtn').style.visibility = 'visible';
		//document.getElementById('ddldiff-container').style.display = 'block';
	}
	else {
		console.log("no diff...");
		//document.getElementById('togglediffbtn').style.visibility = 'hidden';
		//document.getElementById('ddldiff-container').style.display = 'none';
	}
	return false;
}

//----------------------------------------

function doTableOnLoad() {
	//console.log("doTableOnLoad");
	addDiffBtns();
}

/*if (document.readyState == "complete" || document.readyState == "loaded") {
	doTableOnLoad();
}
else {
	document.addEventListener("DOMContentLoaded", doTableOnLoad);
}*/

doTableOnLoad();
