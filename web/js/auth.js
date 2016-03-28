
var authInfo = {
		// similar to 'auth/info.jsp'
		authenticated: false,
		username: null, //''
		roles: [ ],
		permissions: [ ],
		isAdmin: false,
		isDev: false
};

function loadAuthInfo() {
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
	if(authInfo.isDev && urlednew) {
		urlednew.style.display = 'inline';
		urlednew.href = qonEditorUrl;
	}
	else if(urlednew){
		urlednew.style.display = 'none';
	}
}

function authHasPermission(permission) {
	return authInfo.permissions.indexOf(permission)>=0;
}
