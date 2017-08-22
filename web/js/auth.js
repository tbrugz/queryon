
/*
 * requires: jquery
 */

var authInfo = {
		// similar to 'auth/info.jsp'
		authenticated: false,
		username: null, //''
		roles: [ ],
		permissions: [ ],
		isAdmin: false,
		isDev: false,
		loaded: false  // should be set to 'true' after loading from ajax
};

function loadAuthInfo() { //callback?
	var url = "auth/info.jsp";
	
	var request = new XMLHttpRequest();
	request.open("POST", url, true);
	request.onload = function(oEvent) {
		var info = JSON.parse(oEvent.target.responseText);
		console.log('authInfo', info);
		info.isAdmin = info.permissions.indexOf("SELECT_ANY")>=0;
		info.isDev = info.isAdmin;
		info.loaded = true;
		authInfo = info;
		
		if(typeof loadAuthInfoCallback === 'function') {
			loadAuthInfoCallback();
		}
		if(typeof makeHrefs === 'function') {
			makeHrefs();
		}
		else {
			console.log('auth.js: function makeHrefs() not present...');
		}
	}
	request.send();
}

function refreshAuthInfo() {
	var user = document.getElementById('username');
	user.innerHTML = authInfo.username || '';
	var auth = document.getElementById('authaction');
	if(! authInfo.authenticated) {
		auth.innerHTML = '<a href="'+authGetLoginUrl()+'">login</a>';
		user.style.display = 'none';
	}
	else {
		auth.innerHTML = '<a href="'+authGetLogoutUrl()+'">logout</a>';
		user.style.display = 'inline';
	}

	var urlednew = document.getElementById("url-editor-new");
	if(authInfo.isDev && urlednew) {
		urlednew.style.display = 'inline';
		//urlednew.href = qonEditorUrl + ( getCurrentModelId()?"?model="+getCurrentModelId():"" );
		urlednew.href = qonEditorUrl + ( isMultiModel()?"?model="+getCurrentModelId():"" );
	}
	else if(urlednew){
		urlednew.style.display = 'none';
	}
}

function authGetLoginUrl() {
	return 'auth/login.jsp?return='+encodeURIComponent(window.location.href);
}

function authGetLogoutUrl() {
	return 'auth/logout.jsp?return='+encodeURIComponent(window.location.href);
}

function authHasPermission(permission) {
	return authInfo.permissions.indexOf(permission)>=0;
}
