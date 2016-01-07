
var menucontent = null;
var nav = null;

function loadMenu() {
	var logo = document.getElementById('logo');
	nav = logo.parentNode;
	
	var menu = document.createElement("i"); //<i class="fa fa-bars"></i>
	menu.setAttribute("class", "fa fa-bars");
	menu.setAttribute("onclick", "toggleMenu()");

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
	menucontent = oDOM.getElementById("content")
	//console.log(menucontent);
	// id="content"
}

function toggleMenu() {
	//console.log("toggleMenu... admin? "+authInfo.isAdmin);
	// menucontent
	var menuicon = nav.getElementsByTagName("i")[0];
	var prevmenu = document.getElementById('leftmenu');
	if(prevmenu!=null) {
		prevmenu.parentElement.removeChild(prevmenu);
		menuicon.classList.remove("fa-arrow-left");
		menuicon.classList.add("fa-bars");
		return;
	}

	var lis = menucontent.getElementsByTagName('li');
	var menu = document.createElement("div");
	menu.setAttribute("id", "leftmenu");
	var innerContent = '';
	var isDeveloper = typeof authInfo != "undefined" && typeof authInfo.isAdmin != "undefined" && authInfo.isAdmin;
	var hasLoginLink = document.getElementById('authaction') != null;
	for(var i=0;i<lis.length;i++) {
		var li = lis[i];
		//XXXdone: index, d3chart: do not show login, logout
		//XXX: graph: add login/out
		//~XXX: all: do not show (linkable) self - current!
		if( (!li.classList.contains("dev") || isDeveloper)
			&& (!li.classList.contains("auth") || !hasLoginLink) ) {
			var href = li.getElementsByTagName("a")[0].getAttribute("href");
			if(href.startsWith(".")) { href = href.substring(1); }
			//console.log("menu["+i+"]: ",location.pathname," ; ",href);
			if(location.pathname.endsWith(href)) {
				li.classList.add("current");
			}
			innerContent += li.outerHTML;
		}
	}
	var menuUL = document.createElement("ul");
	menuUL.innerHTML = innerContent;
	menu.appendChild(menuUL);
	menu.style.top = nav.offsetHeight + 'px';
	//console.log("toggleMenu... admin? "+authInfo.isAdmin+" ; "+innerContent);

	menuicon.classList.remove("fa-bars");
	menuicon.classList.add("fa-arrow-left");
	
	var body = document.getElementsByTagName('body')[0];
	body.appendChild(menu);
}

document.addEventListener("DOMContentLoaded", loadMenu);

