
/* globals */

var queryOnUrl = '/queryon/q';
var qonEditorUrl = 'qon-editor.jsp';
var authInfo = {
		// similar to 'auth/info.jsp'
		authenticated: false,
		username: null, //''
		roles: [ ],
		permissions: [ ]
}

/* functions */

function loadQueries() {
	init(queryOnUrl,'objects', function(containerId, rels) {
		writeRelations(containerId, rels); updateSelectedQueryState(); makeHrefs();
	});
}

function onQueryChanged(doNotUpdateState) {
	loadRelation('objects', 'parameters', 'content');
	updateNavBar();
	
	//cleaning 'order'
	document.getElementById('order').value = '';
	
	//updateUI();
	if(!doNotUpdateState) {
		updateState();
	}

	makeHrefs();
}

function updateSelectedQueryState() {
	var hash = window.location.hash;
	if(hash.indexOf('#')==0) {
		hash = hash.substring(1);
		var parts = hash.split('/');
		var select = document.getElementById('objects');
		var found = false;
		var relname = parts.splice(0, 1);
		for(var i = 0;i<select.length;i++) {
			if(select.options[i].value==relname) {
				select.options[i].selected = true;
				found = true;
				onQueryChanged(true);
				//updateNavBar();
				break;
			}
		}
		
		if(!found) {
			console.log('query '+relname+' not found...');
			if(! authInfo.authenticated) {
				showInfoMessages('messages', 'Query <code>'+relname+'</code> not found. Maybe you should login');
			}
		}
		else {
			setParametersValues(parts);
		}
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

function updateState() {
	//replace href
	var select = document.getElementById('objects');
	var id = select.options[select.selectedIndex].value;
	//history.replaceState(null, null, "#"+id);
	////var hash = id!="" ? "#"+id : "";
	////history.replaceState(null, null, hash);
	var state = id+getParameters();
	console.log('updateState',state);
	history.replaceState(null, null, "#"+state);
}

function makeHrefs() {
	var urled = document.getElementById("url-editor");
	var urlpl = document.getElementById("url-permalink");
	var urldown = document.getElementById("url-down");

	var select = document.getElementById('objects');
	var id = select.options[select.selectedIndex].value;
	if(id=="") {
		urled && (urled.style.display = 'none');
		urlpl && (urlpl.style.display = 'none');
		urldown && (urldown.style.display = 'none');
		return;
	}
	
	if(authInfo.isAdmin && urled) {
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
	
	//var queryString = '';
	
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
	
	var order = document.getElementById('order').value;
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
	//console.log('queryString: '+queryString);

	//console.log('urlpl.href: '+urlpl.href);

	//console.log('urldown.href: '+urldown.href);
	updateUI();
	refreshAuthInfo();
}

function loadAuthInfo() {
	$.ajax({
		url: 'auth/info.jsp',
		success: function(data) {
			var info = JSON.parse(data);
			console.log('authinfo',info);
			if(info.authenticated) {
				//username = info.username;
				info.isAdmin = info.permissions.indexOf("SELECT_ANY")>=0;
			}
			else {
				//username = '';
				info.isAdmin = false;
			}
			authInfo = info;
			refreshAuthInfo();
		}
	});
}

function refreshAuthInfo() {
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
	if(authInfo.isAdmin && urlednew) {
		urlednew.style.display = 'inline';
		urlednew.href = qonEditorUrl;
	}
	else if(urlednew){
		urlednew.style.display = 'none';
	}
}

function getQueryUrl(selectId, syntax) {
	var select = document.getElementById(selectId);
	var id = select.options[select.selectedIndex].value;
	var params = document.querySelectorAll('.parameter');
	var paramsStr = '';
	for (var i = 0; i < params.length; ++i) {
		var item = params[i];
		//console.log(item);
		var str = item.value;
		if(str=='') { str = '-'; } 
		paramsStr += '/'+str;
	}
	
	var queryString = '';
	
	var filters = document.querySelectorAll('.filter');
	for (var i = 0; i < filters.length; ++i) {
		var item = filters[i];
		//console.log(item);
		queryString += '&'+item.name+"="+encodeURIComponent(item.value);
	}
	
	var order = document.getElementById('order').value;
	if(order!=null && order!='') {
		queryString += '&order='+order;
	}
	
	//console.log('query-string: '+queryString);
	console.log('url: '+baseUrl+'/'+id+paramsStr+'.htmlx?'+queryString);
	
	return baseUrl+'/'+id+paramsStr
		+(syntax?'.'+syntax:'')
		+'?'+queryString;
}
