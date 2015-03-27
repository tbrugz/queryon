
var bhvalues = null;

var operatorsInfo = {
	"in": {"name":"in"},
	"nin": {"name":"not in"}
};

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

function addFilterDialog() {
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
		selectHTML += "<option value='"+colz+"'>"+colz+"</option>";
	}
	selectHTML += '</select>';
	
	var operatorsHTML = "<select name='fil-operator' id='fil-operator' class='operator'>";
	var keys = Object.keys(operatorsInfo);
	for(var i=0; i<keys.length; i++) {
		operatorsHTML += "<option value='"+keys[i]+"'>"+operatorsInfo[keys[i]].name+"</option>";
	}
	operatorsHTML += "</select>";
	
	dialog.innerHTML = "<div><label>Filter: "+selectHTML+"</label> "
		+ operatorsHTML
		+ "<label>Value: <input type='text' name='value' id='fin-value'></label> "
		+ "<input type='button' value='add & close' onclick='addFilterIn();closeFilterDialog();'/>"
		+ "<input type='button' value='add' onclick='addFilterIn();'/>"
		+ "<input type='button' value='X' class='simplebutton' onclick='closeFilterDialog();'/></div>";
	//dialog.style.display = 'none';
	refreshAutocomplete();
	
	updateUI();
}

function refreshAutocomplete() {
	if(bhvalues==null) { return; }
	var sel = document.getElementById('fin-column');
	var columnName = sel.options[sel.selectedIndex].value;
	console.log('autocomplete: col='+columnName);
	
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
	var col = document.getElementById('fin-column').value;
	var operator = document.getElementById('fil-operator').value;
	var value = document.getElementById('fin-value').value;
	var filters = document.getElementById('filters');
	var finContainerId = "f"+operator+"_"+col;
	var finContainer = document.getElementById(finContainerId);
	//console.log("operator: "+operator);
	
	if(finContainer==null) {
		filters.innerHTML += "<label class='filter-label' id='"+finContainerId+"'>"+col+" <em>"+operatorsInfo[operator].name+"</em> (<span>"
			+ "<span><input type='text' class='filter' name='f"+operator+":"+col+"' value='"+value+"' onchange='updateFromFilters();'/>"
			+ "<input type='button' value='X' class='simplebutton' onclick='removeFilter(this);updateFromFilters();'></span>"
			+ "</span>)</label>";
	}
	else {
		finContainer.getElementsByTagName('span')[0].innerHTML += "<span><input type='text' class='filter' name='f"+operator+":"+col+"' value='"+value+"' onchange='updateFromFilters();'/>"
			+ "<input type='button' value='X' class='simplebutton' onclick='removeFilter(this);updateFromFilters();'></span>";
	}
	updateFromFilters();
	//makeHrefs();
	//updateUI();
}

function removeFilter(element) {
	var filter = element.parentNode.parentNode.parentNode;
	element.parentNode.parentNode.removeChild(element.parentNode);
	console.log(filter);
	if(filter.getElementsByTagName('input').length==0) {
		filter.parentNode.removeChild(filter);
	}
}

function closeFilterDialog() {
	if(bhvalues!=null) {
		$('#fin-value').typeahead('destroy');
	}
	document.getElementById('dialog').innerHTML = '';
	document.getElementById('dialog-container').style.display = 'none';
}