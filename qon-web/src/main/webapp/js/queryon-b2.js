
/*
 * depends on qon-base.js
 */

/* globals */

var queryOnUrl = 'q';
var qonEditorUrl = 'qon-editor.html';
var utf8par = "utf8=✓";
//var processorUrl = 'processor';

/* functions */

function loadQueries(modelId, filterSchema, doUpdate, callback) {
	init(queryOnUrl, 'objects', function(containerId, rels) {
		if(filterSchema) {
			console.log('filtering rels', rels.length, 'filterSchema', filterSchema);
			if(!Array.isArray(filterSchema)) {
				filterSchema = [filterSchema];
			}
			for(var i=rels.length-1;i>=0;i--) {
				if(filterSchema.indexOf(rels[i].schemaName)<0) {
					//console.log('removing rel:', rels[i].schemaName, rels[i].name);
					//console.log('removing rel ',rels[i], 'filterSchema', filterSchema, 'obj', obj);
					var obj = rels.splice(i, 1);
				}
			}
		}
		else {
			filterSchema = null;
		}
		//console.log('loadQueries: filterSchema=', filterSchema);
		writeRelations(containerId, rels, (filterSchema==null));
		if(doUpdate != false) {
			updateSelectedQueryState(); makeHrefs();
		}
		if(callback) {
			callback();
		}
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

function onParameterChange(pname) {
	updateState();
	makeHrefs();
}

function updateSelectedQueryStateParameters() {
	var hash = window.location.hash;
	if(hash.indexOf('#')==0) {
		hash = hash.substring(1);
		var bigParts = hash.split('|');
		var parts = bigParts[0].split('/');
		var modelId = null;
		if(bigParts.length>=3) {
			modelId = bigParts[2];
		}
		var relname = parts.splice(0, 1)[0];
		//console.log("updateSelectedQueryStateParameters: bigParts", bigParts, ", parts", parts, ", relname ", relname, ", modelId ", modelId);
		
		if(modelId) {
			var mSelect = document.getElementById('model');
			var found = false;
			for(var i = 0;i<mSelect.length;i++) {
				if(mSelect.options[i].value==modelId) {
					found = true;
					//console.log("model found: modelId ==", modelId, ", mSelect.value=", mSelect.value, ", relname =", relname, ", bigParts =", bigParts);
					if(modelId != mSelect.value) {
						console.log("modelId [", modelId, "] != mSelect.value [", mSelect.value, "], will load relations (again)");
						mSelect.options[i].selected = true;
						doLoadQueries(false, function() {
							updateSelectedQueryStateParametersCallback(relname, parts);
							console.log("recursion hazzard! modelId=", modelId);
							updateSelectedQueryState(); // XXX: recursion hazzard!
						});
					}
					else {
						updateSelectedQueryStateParametersCallback(relname, parts);
						return bigParts;
					}
				}
			}
			
			if(!found) {
				console.log("model not found? modelId ==", modelId);
			}
			else {
				//console.log("model found? modelId ==", modelId);
			}
			return null;
		}
		else {
			//console.log("model null, bigParts =", bigParts);
			updateSelectedQueryStateParametersCallback(relname, parts);
			return bigParts;
		}
	}
}

function updateSelectedQueryStateParametersCallback(relname, parts) {
	//console.log("updateSelectedQueryStateParametersCallback", relname, parts);
	var found = false;
	var select = document.getElementById('objects');
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
		var message = "Query "+relname+" not found.";
		console.log('query "',relname,'" not found [',select.length,']... authenticated == ', authInfo.authenticated);
		showWarnMessages('messages', 'Query <code>'+relname+'</code> not found.'+
			(authInfo.authenticated?'':' Maybe you should login') );
		throw message;
		/*else {
			closeMessages('messages');
		}*/
	}
	else {
		setParametersValues(parts);
	}
	
	loadModelStatusContentCallback();
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

function makeOneHref(objectName, syntax, modelId) {
	//aElem.style.display = 'initial';
	//var utf8par = "utf8=✓";
	var href = queryOnUrl+"/"+encodeURIComponent(objectName);
	
	href += getParameters(true)+"."+syntax+"?"+utf8par;
	if(modelId) {
		href += "&model="+modelId;
	}
	href = urlAddNamedParameters(href);
	href = urlAddFilterOrderOffset(href);
	href = getPivotURL(href);
	return href;
}

function makeHrefs() {
	var urled = document.getElementById("url-editor");
	var urlpl = document.getElementById("url-permalink");
	var urldown = document.getElementById("url-down");
	var urldownByExt = document.getElementById("url-down-by-extension");

	var select = document.getElementById('objects');
	//console.log("select.selectedIndex", select.selectedIndex);
	//var utf8par = "utf8=✓";
	
	var id = null;
	var modelId = getCurrentModelId();
	if(select.selectedIndex>=0) {
		id = select.options[select.selectedIndex].value;
	}
	if(!id) {
		urled && (urled.style.display = 'none');
		urlpl && (urlpl.style.display = 'none');
		urldown && (urldown.style.display = 'none');
		urldownByExt && (urldownByExt.style.display = 'none');
		//return;
	}
	else {
		if(authInfo.isDev && urled && isQonQueriesPluginActive()) {
			if(relationsHash[id].relationType=="query") {
				urled.style.display = 'initial';
				var name = id;
				urled.href = qonEditorUrl+"?";
				var parts = id.split('.');
				if(parts.length>1) {
					urled.href += "schema="+encodeURIComponent(parts[0])+"&name="+encodeURIComponent(parts[1])+"&"+utf8par;
				}
				else {
					urled.href += "name="+encodeURIComponent(parts[0])+"&"+utf8par;
				}
				if(modelId) {
					urled.href += "&model="+modelId;
				}
			}
			else {
				urled.style.display = 'none';
			}
		}

		if(urlpl) {
			urlpl.style.display = 'initial';
			urlpl.href = makeOneHref(id, "htmlx", modelId);
			/*
			urlpl.href = queryOnUrl+"/"+encodeURIComponent(id);
			urlpl.href += getParameters(true)+".htmlx"+"?"+utf8par;
			if(modelId) {
				urlpl.href += "&model="+modelId;
			}
			*/
		}
	
		if(urldown) {
			urldown.style.display = 'initial';
			urldown.href = makeOneHref(id, "csv", modelId);
			/*
			urldown.href = queryOnUrl+"/"+encodeURIComponent(id);
			urldown.href += getParameters(true)+".csv"+"?"+utf8par;
			if(modelId) {
				urldown.href += "&model="+modelId;
			}
			*/
		}
		
		if(urldownByExt) {
			urldownByExt.style.display = 'initial';
		}
	}
	
	/*
	if(urlpl) {
		urlpl.href = urlAddNamedParameters(urlpl.href);
		urlpl.href = urlAddFilterOrderOffset(urlpl.href);
		urlpl.href = getPivotURL(urlpl.href);
	}
	if(urldown) {
		urldown.href = urlAddNamedParameters(urldown.href);
		urldown.href = urlAddFilterOrderOffset(urldown.href);
		urldown.href = getPivotURL(urldown.href);
	}
	if(urldownByExt) {
		//urldownByExt.href = urlAddFilterOrderOffset(urldownByExt.href);
	}
	*/
	
	var urlednew = document.getElementById("url-editor-new");
	if(urlednew) {
		if(authInfo.isDev && isQonQueriesPluginActive()) {
			urlednew.style.display = 'inline';
			urlednew.href = qonEditorUrl + ( isMultiModel()?"?model="+getCurrentModelId():"" );
		}
		else {
			urlednew.style.display = 'none';
		}
	}

	var btnManage = document.getElementById("btn-manage");
	if(btnManage) {
		btnManage.style.display = authInfo.isAdmin ? 'inline' : 'none';
	}
	
	//console.log('urled.href: ',urled.href);
	//console.log('urlpl.href: ',urlpl.href);
	//console.log('urldown.href: ',urldown.href);
	
	updateUI();
	refreshAuthInfo();
}

function urlAddNamedParameters(urlz) {
	urlz = append2url(urlz, urlGetQueryParameters(true));
	return urlz;
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
		console.log('relation has no PK', relation);
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
