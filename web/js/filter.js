
function addFilter() {
	var select = document.getElementById('objects');
	var id = select.options[select.selectedIndex].value;
	if(!id) {
		alert('no object selected!');
		return;
	}
	var dialogCont = document.getElementById('dialog-container');
	dialogCont.style.display = 'block';
	var dialog = document.getElementById('dialog');
	var cols = getColumnsFromRelation(relationsHash[id]);
	if(cols==null || cols.length==0) {
		cols = getColumnsFromContainer('content');
	}

	var selectHTML = "<select name='fin-column' id='fin-column' onchange='refreshAutocomplete()'>";
	for(var i=0;i<cols.length;i++) {
		var colz = cols[i];
		selectHTML += "<option name='"+colz+"'>"+colz+"</option>";
	}
	selectHTML += '</select>';
	dialog.innerHTML = "<div><label>Filter: "+selectHTML+"</label> <em>in</em> "
		+ "<label>Value: <input type='text' name='value' id='fin-value'></label> "
		+ "<input type='button' value='add' onclick='addFilterIn();closeFilterDialog();'/>"
		+ "<input type='button' value='X' class='simplebutton' onclick='closeFilterDialog();'/></div>";
	//dialog.style.display = 'none';
	
	updateUI();
}

function refreshAutocomplete() {
}

function addFilterIn() {
	var col = document.getElementById('fin-column').value;
	var value = document.getElementById('fin-value').value;
	var filters = document.getElementById('filters');
	filters.innerHTML += "<label class='filter-label'>"+col+" = <input type='text' class='filter' name='fin:"+col+"' value='"+value+"' onchange='updateFromFilters();'/>"
		+ "<input type='button' value='X' class='simplebutton' onclick='this.parentNode.parentNode.removeChild(this.parentNode);updateFromFilters();'></label>";
	updateFromFilters();
	//makeHrefs();
	//updateUI();
}

function closeFilterDialog() {
	document.getElementById('dialog').innerHTML = '';
	document.getElementById('dialog-container').style.display = 'none';
}