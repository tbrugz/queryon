
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
	menu.setAttribute("id", "leftmenu");

	//menu.style.top = nav.offsetHeight + 'px';
	//console.log("toggleMenu... admin? "+authInfo.isAdmin+" ; "+innerContent);

	menuicon.classList.remove("fa-bars");
	menuicon.classList.add("fa-arrow-left");
	
	var body = document.getElementsByTagName('body')[0];
	body.appendChild(menu);
}

function createMenuContent(addCloseBtn) {
	var lis = menucontent.getElementsByTagName('li');
	var menu = document.createElement("div");
	var innerContent = '';
	var isDeveloper = typeof authInfo != "undefined" && typeof authInfo.isAdmin != "undefined" && authInfo.isAdmin;
	var hasLoginLink = document.getElementById('authaction') != null;
	var isMultiModel = (typeof modelsInfo != "undefined" && modelsInfo != null) ? modelsInfo.length>1 : true;
	for(var i=0;i<lis.length;i++) {
		var li = lis[i];
		//XXXdone: index, d3chart: do not show login, logout
		//XXX: graph: add login/out
		//~XXX: all: do not show (linkable) self - current!
		if( (!li.classList.contains("dev") || isDeveloper)
			&& (!li.classList.contains("auth") || !hasLoginLink)
			&& (!li.classList.contains("multimodel") || isMultiModel) ) {
			var href = li.getElementsByTagName("a")[0].getAttribute("href");
			if(href.startsWith(".")) { href = href.substring(1); }
			//console.log("menu["+i+"]: ",location.pathname," ; ",href);
			if(location.pathname.endsWith(href)) {
				li.classList.add("current");
			}
			if(li.classList.contains("auth") && href.indexOf('?return')<0) {
				href += '?return='+encodeURIComponent(window.location.href);
				li.getElementsByTagName("a")[0].setAttribute("href", href);
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

