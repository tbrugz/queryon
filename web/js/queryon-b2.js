
/*
 * depends on qon-base.js
 */

/* globals */

var queryOnUrl = 'q';
var qonEditorUrl = 'qon-editor.jsp';
var processorUrl = 'processor';
/*var authInfo = {
		// similar to 'auth/info.jsp'
		authenticated: false,
		username: null, //''
		roles: [ ],
		permissions: [ ],
		isAdmin: false,
		isDev: false
};*/
//var settings = {};

/* functions */

function loadQueries(modelId, filterSchema) {
	init(queryOnUrl,'objects', function(containerId, rels) {
		if(filterSchema) {
			console.log('filtering rels', rels.length, 'filterSchema', filterSchema);
			for(var i=rels.length-1;i>=0;i--) {
				if(rels[i].schemaName!=filterSchema) {
					var obj = rels.splice(i, 1);
					//console.log('removing rel ',rels[i], 'filterSchema', filterSchema, 'obj', obj);
				}
			}
		}
		byId(containerId).innerHTML = '';
		writeRelations(containerId, rels); updateSelectedQueryState(); makeHrefs();
	}, modelId);
}

function onQueryChangedClean() {
	//cleaning 'order'
	document.getElementById('order').value = '';

	//cleaning 'offset'
	var offset = document.getElementById('offset');
	if(offset) { offset.value = ''; };
}

function onQueryChanged() {
	loadRelation('objects', 'parameters', 'content');
	updateNavBar();
	
	//onQueryChangedClean();
	
	//updateUI();
	//if(!doNotUpdateState) {
	//	updateState();
	//}

	makeHrefs();
}

function onParameterChange(i) {
	updateState();
	makeHrefs();
}

function updateSelectedQueryStateParameters() {
	var hash = window.location.hash;
	if(hash.indexOf('#')==0) {
		hash = hash.substring(1);
		var bigParts = hash.split('|');
		var parts = bigParts[0].split('/');
		var select = document.getElementById('objects');
		var found = false;
		var relname = parts.splice(0, 1);
		for(var i = 0;i<select.length;i++) {
			if(select.options[i].value==relname) {
				select.options[i].selected = true;
				found = true;
				onQueryChangedClean();
				onQueryChanged();
				//updateNavBar();
				break;
			}
		}
		
		if(!found) {
			console.log('query '+relname+' not found...', authInfo.authenticated);
			if(! authInfo.authenticated) {
				showInfoMessages('messages', 'Query <code>'+relname+'</code> not found. Maybe you should login');
			}
			/*else {
				closeMessages('messages');
			}*/
		}
		else {
			setParametersValues(parts);
		}
		
		return bigParts;
	}
}

function updateNavBar() {
	var select = document.getElementById('objects');
	var id = select.options[select.selectedIndex].value;
	var display = 'initial';
	if(id=="") {
		display = 'none';
	}
	document.getElementById('navbar-prop').style.display = display;
	refreshAuthInfo();
}

function makeHrefs() {
	var urled = document.getElementById("url-editor");
	var urlpl = document.getElementById("url-permalink");
	var urldown = document.getElementById("url-down");

	var select = document.getElementById('objects');
	//console.log("select.selectedIndex", select.selectedIndex);
	
	var id = null;
	if(select.selectedIndex>=0) {
		id = select.options[select.selectedIndex].value;
	}
	if(!id) {
		urled && (urled.style.display = 'none');
		urlpl && (urlpl.style.display = 'none');
		urldown && (urldown.style.display = 'none');
		//return;
	}
	else {
		if(authInfo.isDev && urled) {
			if(relationsHash[id].relationType=="query") {
				urled.style.display = 'initial';
				var name = id;
				urled.href = qonEditorUrl+"?";
				var parts = id.split('.');
				if(parts.length>1) {
					urled.href += "schema="+parts[0]+"&name="+parts[1];
				}
				else {
					urled.href += "name="+parts[0];
				}
				if(getCurrentModelId()) {
					urled.href += "&model="+getCurrentModelId();
				}
			}
			else {
				urled.style.display = 'none';
			}
		}

		if(urlpl) {
			urlpl.style.display = 'initial';
			urlpl.href = queryOnUrl+"/"+id;
			urlpl.href += getParameters()+".htmlx";
		}
	
		if(urldown) {
			urldown.style.display = 'initial';
			urldown.href = queryOnUrl+"/"+id;
			urldown.href += getParameters()+".csv";
		}
	}
	
	//var queryString = '';
	/*
	var filters = document.querySelectorAll('.filter');
	for (var i = 0; i < filters.length; ++i) {
		var item = filters[i];
		//queryString += '&'+item.name+"="+item.value;
		//queryString = append2url(queryString, item.name+"="+item.value);
		if(urlpl) {
			urlpl.href = append2url(urlpl.href, item.name+"="+encodeURIComponent(item.value));
		}
		if(urldown) {
			urldown.href = append2url(urldown.href, item.name+"="+encodeURIComponent(item.value));
		}
	}
	
	var orderElem = document.getElementById('order');
	if(orderElem) {
	var order = orderElem.value;
	if(order!=null && order!='') {
		//queryString += '&order='+order;
		//queryString = append2url(queryString, 'order='+order);
		if(urlpl) {
			urlpl.href = append2url(urlpl.href, 'order='+order);
		}
		if(urldown) {
			urldown.href = append2url(urldown.href, 'order='+order);
		}
	}
	}

	var offset = document.getElementById('offset');
	if(offset) { offset = offset.value; };
	if(offset!=null && offset>0) {
		if(urlpl) {
			urlpl.href = append2url(urlpl.href, 'offset='+offset);
		}
		if(urldown) {
			urldown.href = append2url(urldown.href, 'offset='+offset);
		}
	}
	*/
	
	if(urlpl) {
		urlpl.href = urlAddFilterOrderOffset(urlpl.href);
	}
	if(urldown) {
		urldown.href = urlAddFilterOrderOffset(urldown.href);
	}
	
	//console.log('urlpl.href: ',urlpl.href);
	//console.log('urldown.href: ',urldown.href);
	
	updateUI();
	refreshAuthInfo();
}

function urlAddFilterOrderOffset(urlz) {
	var filters = document.querySelectorAll('.filter');
	if(filters) {
		for (var i = 0; i < filters.length; ++i) {
			var item = filters[i];
			urlz = append2url(urlz, item.name+"="+encodeURIComponent(item.value));
		}
	}
	
	var orderElem = document.getElementById('order');
	if(orderElem) {
		var order = orderElem.value;
		if(order!=null && order!='') {
			urlz = append2url(urlz, 'order='+order);
		}
	}

	var offset = document.getElementById('offset');
	if(offset) { offset = offset.value; };
	if(offset!=null && offset>0) {
		urlz = append2url(urlz, 'offset='+offset);
	}
	
	if(urlz==null) { urlz = ''; }
	return urlz;
}

/*function loadAuthInfo() {
	$.ajax({
		url: 'auth/info.jsp',
		dataType: "text",
		success: function(data) {
			var info = JSON.parse(data);
			console.log('authInfo', info);
			info.isAdmin = info.permissions.indexOf("SELECT_ANY")>=0;
			info.isDev = info.isAdmin;
			authInfo = info;
			makeHrefs();
		}
	});
}*/

/*function loadSettings(callbackOk) {
	$.ajax({
		url: 'info/settings.jsp',
		dataType: "text",
		success: function(data) {
			var info = JSON.parse(data);
			console.log('settings',info);
			settings = info;
			if(callbackOk) { callbackOk(); }
		}
	});
}*/

/*function refreshAuthInfo() {
	var user = document.getElementById('username');
	user.innerHTML = authInfo.username || '';
	var auth = document.getElementById('authaction');
	if(! authInfo.authenticated) {
		auth.innerHTML = '<a href="auth/login.jsp?return='+encodeURIComponent(window.location.href)+'">login</a>';
		user.style.display = 'none';
	}
	else {
		auth.innerHTML = '<a href="auth/logout.jsp?return='+encodeURIComponent(window.location.href)+'">logout</a>';
		user.style.display = 'inline';
	}

	var urlednew = document.getElementById("url-editor-new");
	if(authInfo.isDev && urlednew) {
		urlednew.style.display = 'inline';
		urlednew.href = qonEditorUrl;
	}
	else if(urlednew){
		urlednew.style.display = 'none';
	}
}*/

function getKeyValsForRow(rownum) {
	var content = document.getElementById('content');
	var rows = content.querySelectorAll('tr');
	//rows[0].innerHTML += "<th>actions</th>";
	var row = rows[rownum];
	var pk = getPkCols('objects');

	var relation = getCurrentRelation('objects');
	var cols = getColumnsFromRelation(relation);
	
	//console.log('getKeyValsForRow row', row, 'pk', pk, 'cols.length', cols.length, 'cols', cols);
	if(pk==null) {
		console.log('relation has no PK');
		return null;
	}
	var idx = [];
	for(var i=0;i<pk.length;i++) {
		idx.push(cols.indexOf(pk[i]));
	}
	var tds = row.querySelectorAll('td');
	//console.log('getKeyValsForRow idx', idx,'tds.length',tds.length);
	var vals = [];
	var allvals = [];
	var filter = '';
	for(var i=0;i<idx.length;i++) {
		vals.push(tds[idx[i]].innerHTML);
		if(filter) { filter += ', '; }
		filter += pk[i]+" = "+vals[i];
	}
	
	var valsKey = vals.join('/'); //XXX url encode ?
	/*
	var valsKey = '';
	for(var i=0; i<vals.length; i++) {
		if(i!=0) { valsKey += "/"; }
		valsKey += encodeURIComponent(vals[i]);
	}
	*/
	
	for(var i=0;i<tds.length;i++) {
		allvals.push(tds[i].innerHTML);
	}
	
	var colTypes = getColumnTypesFromColgroup('content');

	return {key: valsKey, filter: filter, pk: pk, pkvals: vals, cols: cols, allvals: allvals, colTypes: colTypes};
}

function getKeyValsForTr(pk, cols, tr) {
	var tds = tr.querySelectorAll('td');

	var idx = [];
	for(var i=0;i<pk.length;i++) {
		idx.push(cols.indexOf(pk[i]));
	}
	
	var vals = [];
	for(var i=0;i<idx.length;i++) {
		vals.push(tds[idx[i]].innerHTML);
	}
	
	var valsKey = vals.join('/'); //XXX url encode ?

	return {key: valsKey, pkvals: vals};
}
