
var bhvalues = null;

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

var numericSqlTypes = ["TINYINT", "SMALLINT", "INTEGER", "BIGINT", "DECIMAL", "NUMERIC", "NUMBER", "REAL", "FLOAT", "DOUBLE"];

if(typeof Bloodhound != 'undefined') {
	bhvalues = new Bloodhound({
		datumTokenizer : Bloodhound.tokenizers.obj.whitespace('value'),
		queryTokenizer : Bloodhound.tokenizers.whitespace,
		local: $.map([], function(str) {
			return {
				value : str
			};
		})
	});
	// kicks off the loading/processing of `local` and `prefetch`
	bhvalues.initialize();
}

function addFilterDialog(selectedCol) {
	var select = document.getElementById('objects');
	var id = select.options[select.selectedIndex].value;
	if(!id) {
		alert('no object selected!');
		return;
	}
	var dialogCont = document.getElementById('dialog-container');
	dialogCont.style.display = 'block';
	var dialog = document.getElementById('dialog');
	dialog.style.display = 'block';
	var cols = getColumnNamesFromColgroup('content');
	if(cols==null || cols.length==0) {
		cols = getColumnsFromRelation(relationsHash[id]);
	}
	if(cols==null || cols.length==0) {
		cols = getColumnsFromContainer('content');
	}

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
		+ "<input type='button' value='add & close' onclick='addFilterIn();closeDialogs();'/>"
		+ "<input type='button' value='add' onclick='addFilterIn();'/>"
		+ "<input type='button' value='X' class='simplebutton' onclick='closeDialogs();'/></div>";
	//dialog.style.display = 'none';
	refreshAutocomplete();
	
	updateUI();
}

function refreshAutocomplete() {
	if(bhvalues==null) { return; }
	var sel = document.getElementById('fin-column');
	var columnName = sel.options[sel.selectedIndex].value;
	var input = document.getElementById('fin-value');
	var colType = getColumnsTypesFromHash()[sel.selectedIndex];
	
	console.log('autocomplete: col='+columnName+' ; type= ['+colType+']');
	
	var inputType = 'text';
	if(numericSqlTypes.indexOf(colType)>=0) { inputType = 'number'; }
	input.setAttribute('type', inputType);
	
	// constructs the suggestion engine
	/*var bhvalues = new Bloodhound({
		datumTokenizer : Bloodhound.tokenizers.obj.whitespace('value'),
		queryTokenizer : Bloodhound.tokenizers.whitespace,
		// `states` is an array of state names defined in "The Basics"
		local : $.map(getValuesFromColumn('content', columnName), function(str) {
			return {
				value : str
			};
		})
	});*/
	
	bhvalues.local = $.map(getValuesFromColumn('content', columnName), function(str) {
		return {
			value : str
		};
	});

	// kicks off the loading/processing of `local` and `prefetch`
	bhvalues.clear();
	bhvalues.initialize(true);

	if($('#fin-value').typeahead) {
		$('#fin-value').typeahead('destroy');
	}

	$('#fin-value').typeahead({
		hint : true,
		highlight : true,
		minLength : 1
	}, {
		name : columnName.replace(/([^a-zA-Z0-9])/g,""),
		displayKey : 'value',
		// `ttAdapter` wraps the suggestion engine in an adapter that
		// is compatible with the typeahead jQuery plugin
		source : bhvalues.ttAdapter()
	});

	// removing styles added by typeahead
	$('#fin-value').css('vertical-align','');
	$('#fin-value').css('background-color','');
}

function addFilterIn() {
	var sel = document.getElementById('fin-column');
	var col = sel.value;
	var operator = document.getElementById('fil-operator').value;
	var value = document.getElementById('fin-value').value;
	var colType = getColumnsTypesFromHash()[sel.selectedIndex];
	
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
	console.log('removeFilter: el:', element, 'filter:', filter);
	element.parentNode.parentNode.removeChild(element.parentNode);
	if(filter.getElementsByTagName('input').length==0) {
		filter.parentNode.removeChild(filter);
	}
}

function closeDialogs() {
	if(bhvalues!=null) {
		$('#fin-value').typeahead('destroy');
	}
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

