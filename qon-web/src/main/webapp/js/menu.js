
var menucontent = null;
//var nav = null;

function loadMenu() {
	var logo = document.getElementById('logo');
	var nav = logo.parentNode;
	
	var menu = document.createElement("i"); //<i class="fa fa-bars"></i>
	menu.setAttribute("class", "fa fa-bars");
	menu.setAttribute("onclick", "toggleMenu()");
	menu.setAttribute("title", "show/hide menu");
	menu.setAttribute("id", "menuicon");

	nav.insertBefore(menu,logo);
	
	var oReq = new XMLHttpRequest();
	oReq.addEventListener("load", loadMenuContent);
	oReq.open("GET", "menu.html");
	oReq.send();
}

function loadMenuContent(oEvent) {
	var txt = oEvent.target.responseText;
	var oParser = new DOMParser();
	var oDOM = oParser.parseFromString(txt, "text/xml");
	menucontent = oDOM.getElementById("content");
	if(! menucontent) {
		console.warn("loadMenuContent: null menucontent: ", menucontent);
		return;
	}
	var hrefs = menucontent.getElementsByTagName('a');
	for(var i=0;i<hrefs.length;i++) {
		var a = hrefs[i];
		a.setAttribute("href-orig", a.getAttribute("href"));
	}

	//console.log(typeof loadMenuContentCallback === 'function');
	if(typeof loadMenuContentCallback === 'function') {
		loadMenuContentCallback();
	}
	//console.log(menucontent);
	// id="content"
}

function toggleMenu() {
	//console.log("toggleMenu... admin? "+authInfo.isAdmin);
	// menucontent
	var menuicon = document.getElementById("menuicon");
	//var menuicon = nav.getElementsByTagName("i")[0];
	var prevmenu = document.getElementById('leftmenu');
	if(prevmenu!=null) {
		prevmenu.parentElement.removeChild(prevmenu);
		menuicon.classList.remove("fa-arrow-left");
		menuicon.classList.add("fa-bars");
		return;
	}

	var menu = createMenuContent(true);
	if(! menu) {
		console.warn("toggleMenu: null menu: ", menu);
		return;
	}
	menu.setAttribute("id", "leftmenu");

	//menu.style.top = nav.offsetHeight + 'px';
	//console.log("toggleMenu... admin? "+authInfo.isAdmin+" ; "+innerContent);

	menuicon.classList.remove("fa-bars");
	menuicon.classList.add("fa-arrow-left");
	
	var body = document.getElementsByTagName('body')[0];
	body.appendChild(menu);
}

function createMenuContent(addCloseBtn) {
	if(! menucontent) {
		console.warn("createMenuContent: null menucontent", menucontent);
		return;
	}
	var lis = menucontent.getElementsByTagName('li');
	var menu = document.createElement("div");
	menu.classList.add("leftmenu");
	var innerContent = '';
	var isAdmin = typeof authInfo != "undefined" && typeof authInfo.isAdmin != "undefined" && authInfo.isAdmin;
	var isDeveloper = typeof authInfo != "undefined" && typeof authInfo.isDev != "undefined" && authInfo.isDev;
	var hasLoginLink = document.getElementById('authaction') != null;
	var isMultiModel = (typeof modelsInfo != "undefined" && modelsInfo != null) ? modelsInfo.length>1 : true;
	var path = location.pathname;
	//console.log('typeof authInfo =', (typeof authInfo) ," typeof modelsInfo =", (typeof modelsInfo));
	
	for(var i=0;i<lis.length;i++) {
		var li = lis[i];
		//XXXdone: index, d3chart: do not show login, logout
		//XXX: graph: add login/out
		//~XXX: all: do not show (linkable) self - current!
		if( (!li.classList.contains("sqlany") || isAdmin)
			&& (!li.classList.contains("dev") || isDeveloper)
			&& (!li.classList.contains("auth") || !hasLoginLink)
			&& (!li.classList.contains("multimodel") || isMultiModel) ) {
			
			var hrefElem = li.getElementsByTagName("a")[0];
			if(!hrefElem) { continue; }
			var href = hrefElem.getAttribute("href-orig");
			var hrefCurrentLike = href; //"/"+href;
			if(hrefCurrentLike.startsWith(".")) { hrefCurrentLike = hrefCurrentLike.substring(1); }
			hrefCurrentLike = hrefCurrentLike.replace(/\/\//g, '/');
			if(hrefCurrentLike.indexOf('?')>0) {
				hrefCurrentLike = hrefCurrentLike.substring(0, hrefCurrentLike.indexOf('?'));
			}
			
			var currentModelId = getCurrentModelId();
			//console.log("menu["+i+"]: path=",path," ; href=",href,' ; hLike=',hrefCurrentLike);
			if(path.endsWith(hrefCurrentLike)) {
				li.classList.add("current");
			}
			if(li.classList.contains("auth") && href.indexOf('?return')<0) {
				href += '?return='+encodeURIComponent(window.location.href);
				hrefElem.setAttribute("href", href);
			}
			if(!li.classList.contains("auth") && !li.getElementsByTagName("a")[0].getAttribute('target')) {
				href += location.search;
				hrefElem.setAttribute("href", href);
			}
			if(li.classList.contains("addmodel") && currentModelId) {
				href += "?model="+currentModelId;
				hrefElem.setAttribute("href", href);
			}
			innerContent += li.outerHTML;
		}
	}
	
	if(addCloseBtn) {
		var closebtn = document.createElement("i");
		closebtn.setAttribute("class", "fa fa-times closebtn");
		closebtn.setAttribute("onclick", "toggleMenu()");
		menu.appendChild(closebtn);
	}
	
	var menuUL = document.createElement("ul");
	menuUL.innerHTML = innerContent;
	menu.appendChild(menuUL);

	return menu;
}

document.addEventListener("DOMContentLoaded", loadMenu);

