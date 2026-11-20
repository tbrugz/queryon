
/* depends on: qon-base.js */

var ttInstance = null;

var operatorsInfo = {
	"in":  {"name":"in", "multiple": true },
	"nin": {"name":"not in", "multiple": true },
	"lk":  {"name":"like", "multiple": true },
	"nlk": {"name":"not like", "multiple": true },
	// eq, ne ?
	//"": {"name":"", "multiple":}
	"gt": { "name":">", "multiple": false },
	"ge": { "name":"&ge;", "multiple": false },
	"lt": { "name":"<", "multiple": false },
	"le": { "name":"&le;", "multiple": false },

	"null": { "name":"is null", "multiple": false, "has-no-argument": true },
	"notnull": { "name":"is not null", "multiple": false, "has-no-argument": true }
};

function addFilterDialog(selectedCol, columns) {
	var select = document.getElementById('objects');
	if(select == null) {
		// diff.html
		select = document.getElementById('object');
	}
	if(select == null) {
		log.warn("element #objects/#object not found");
		return;
	}
	var id = select.options[select.selectedIndex].value;
	if(!id) {
		alert('no object selected!');
		return;
	}
	var dialogCont = document.getElementById('dialog-container');
	dialogCont.style.display = 'block';
	var dialog = document.getElementById('dialog');
	dialog.style.display = 'block';
	var cols = columns ? columns : getColumnNames('content');

	var selectHTML = "<select name='fin-column' id='fin-column' onchange='refreshAutocomplete()'>";
	for(var i=0;i<cols.length;i++) {
		var colz = cols[i];
		selectHTML += "<option value='"+colz+"'"+
			( colz==selectedCol ? " selected" : "")+
			">"+colz+"</option>";
	}
	selectHTML += '</select>';

	var operatorsHTML = "<select name='fil-operator' id='fil-operator' class='operator' onchange='onFilterOperatorChange()'>";
	var keys = Object.keys(operatorsInfo);
	for(var i=0; i<keys.length; i++) {
		operatorsHTML += "<option value='"+keys[i]+"'>"+operatorsInfo[keys[i]].name+"</option>";
	}
	operatorsHTML += "</select>";

	dialog.innerHTML = "<div><label>Filter: "+selectHTML+"</label> "
		+ operatorsHTML
		+ "<label>Value: <input type='text' name='value' id='fin-value'></label> "
		+ "<input type='button' value='add' onclick='addFilterIn();'/>"
		+ "<input type='button' value='add & close' onclick='addFilterIn();closeDialogs();'/>"
		+ "<input type='button' value='X' class='simplebutton' onclick='closeDialogs();'/></div>";
	//dialog.style.display = 'none';
	refreshAutocomplete();

	updateUI();
}

function filterGetColumnTypes() {
	var cols = getColumnTypesFromColgroup('content');
	if(!cols || pivotQueryActive()) {
		cols = getColumnsTypesFromHash();
	}
	return cols;
}

function refreshAutocomplete() {
	if(typeof typeahead == 'undefined') { return; }
	var sel = document.getElementById('fin-column');
	var columnName = sel.options[sel.selectedIndex].value;
	var input = document.getElementById('fin-value');
	var colType = filterGetColumnTypes()[sel.selectedIndex];
	if(colType) { colType = colType.toUpperCase(); }

	console.log('autocomplete: col=',columnName,' ; type= [',colType,']');

	var inputType = 'text';
	if(numericSqlTypes.indexOf(colType)>=0) { inputType = 'number'; }
	input.setAttribute('type', inputType);

	destroyAutocomplete();

	// typeahead-standalone works on strings (null/undefined values are skipped)
	var values = getValuesFromColumn('content', columnName).filter(function(v) { return v!=null && v!==''; })
		.map(function(v) { return String(v); });

	ttInstance = typeahead({
		input : input,
		hint : false, //XXX true?
		highlight : true,
		minLength : 1,
		limit : 10,
		source : {
			local : values
		}
	});
}

function destroyAutocomplete() {
	if(ttInstance!=null) {
		ttInstance.destroy();
		ttInstance = null;
	}
}

function addFilterIn() {
	var sel = document.getElementById('fin-column');
	var col = sel.value;
	var operator = document.getElementById('fil-operator').value;
	var value = document.getElementById('fin-value').value;
	var colTypes = filterGetColumnTypes();
	var colType = colTypes ? colTypes[sel.selectedIndex] : null;

	addFilterWithValues(col, operator, value, colType);
}

function addFilterWithValues(col, operator, value, colType) {
	var filters = document.getElementById('filters');
	var finContainerId = "f"+operator+"_"+col;
	var finContainer = document.getElementById(finContainerId);
	var inputType = 'text';
	if(numericSqlTypes.indexOf(colType)>=0) { inputType = 'number'; }

	//console.log("operator: "+operator);

	if(finContainer==null) {
		filters.innerHTML += "<label class='filter-label' id='"+finContainerId+"'>"+col+" <em>"+operatorsInfo[operator].name+"</em> "
			+ (operatorsInfo[operator].multiple?"(":"")
			+ "<span>" // dont touch what you dont know (or remember)
			+ "<span class='filterspan'><input type='"+inputType+"' class='filter"
			+ (operatorsInfo[operator]["has-no-argument"]?" noargs":"")
			+ "' name='f"+operator+":"+col+"' value='"+value+"' onchange='updateFromFilters();'"
			+ "/>"
			+ "<input type='button' value='X' class='simplebutton' onclick='removeFilter(this);updateFromFilters();'></span>"
			+ "</span>" // same as above
			+ (operatorsInfo[operator].multiple?")":"")
			+ "</label>";
	}
	else {
		if(operatorsInfo[operator].multiple) {
			// add filter value
			finContainer.getElementsByTagName('span')[0].innerHTML += "<span>"
				//+ "," // if first element is removed it doesn't looks good
				+ "<input type='"+inputType+"' class='filter' name='f"+operator+":"+col+"' value='"+value+"' onchange='updateFromFilters();'/>"
				+ "<input type='button' value='X' class='simplebutton' onclick='removeFilter(this);updateFromFilters();'></span>";
		}
		else {
			// replace filter value
			finContainer.getElementsByTagName('span')[0].innerHTML = "<span><input type='text' class='filter' name='f"+operator+":"+col+"' value='"+value+"' onchange='updateFromFilters();'/>"
				+ "<input type='button' value='X' class='simplebutton' onclick='removeFilter(this);updateFromFilters();'></span>";
		}
	}
	updateFromFilters();
	//makeHrefs();
	//updateUI();
}

function removeFilter(element) {
	var filter = element.parentNode.parentNode.parentNode;
	//console.log('removeFilter: el:', element, 'filter:', filter);
	element.parentNode.parentNode.removeChild(element.parentNode);
	if(filter.getElementsByTagName('input').length==0) {
		filter.parentNode.removeChild(filter);
	}
}

function removeAllFilters() {
	var filters = document.getElementById('filters');
	filters.innerHTML = '';
}

function closeDialogs() {
	destroyAutocomplete();
	var components = ['dialog', 'update-dialog'];
	for(var i=0;i<components.length;i++) {
		var elem = document.getElementById(components[i]);
		if(elem) {
			elem.innerHTML = '';
			elem.style.display = 'none';
		}
	}
	document.getElementById('dialog-container').style.display = 'none';
}

function onFilterOperatorChange() {
	var operator = document.getElementById('fil-operator').value;
	var valueInput = document.getElementById('fin-value');

	//console.log("onFilterOperatorChange: ",operatorsInfo[operator]);
	if(operatorsInfo[operator]["has-no-argument"]) {
		valueInput.parentNode.parentNode.style.display = 'none';
	}
	else {
		valueInput.parentNode.parentNode.style.display = '';
	}
}

document.onkeydown = function(evt) {
	var evt = evt || window.event;
	var key = evt.keyCode; // evt.key
	if (key == 27) {
		closeDialogs();
	}
	//console.log('document.onkeydown: ',key);
};
