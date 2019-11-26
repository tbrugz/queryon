
function setDisplayNone(id) {
	var elem = byId(id);
	if(elem) {
		elem.style.display="none";
	}
}

function invertColors(node) {
	if(! (node instanceof Node)) {
		node = byId(node);
	}
	if(node.classList.contains("invertiblecolors")) {
		//console.log("invertiblecolors!");
		var isOn = node.classList.contains("on");
		if(isOn) {
			node.classList.remove("on");
		}
		else {
			node.classList.add("on");
		}
	}
}

function resetColors(node) {
	if(! (node instanceof Node)) {
		node = byId(node);
	}
	if(node.classList.contains("invertiblecolors")) {
		//console.log("invertiblecolors!");
		var isOn = node.classList.contains("on");
		if(isOn) {
			node.classList.remove("on");
		}
	}
}

function createPopupBelow(id, btn, content) {
	
	var elem = byId(id);
	if(! elem) {
		//var str = '<div id="'+id+'" style="display: none;"></div>';
		elem = document.createElement('div');
		elem.setAttribute('id', id);
		elem.setAttribute('class', 'qonpopup');
		document.querySelector('body').appendChild(elem);
	}
	else if(elem.style.display=='block') {
		elem.style.display='none';
		invertColors(btn);
		return;
	}
	
	var btnId = null;
	if(! (btn instanceof Node)) {
		btnId = btn;
		btn = byId(btn);
	}
	else {
		btnId = btn.getAttribute('id');
	}
	
	var closeJs = 'setDisplayNone("'+id+'");invertColors("'+btnId+'");';
	var str = "";
	
	str += "<input value='close' onclick='"+closeJs+"' type='button'>";
	str += "<ul>";
	
	for(var i=0;i<content.length;i++) {
		var k = content[i];
		//var onclick = "onclick='" + closeJs + (k.onclick ? k.onclick : "") + "' ";
		str += "<li><a " +
			(k.href ? "href='" + k.href + "' target='_blank' " : "") +
			"onclick='" + closeJs + (k.onclick ? k.onclick : "") + "' " + //onclick +
			"title='" + (k.title ? k.title : "href = " + k.href ) + "'>" +
			k.label + 
			"</a>";
	}
	
	//str += "<li><a href='"+href+"' target='_blank' onclick='"+closeJs+"' title='download "+exts[i]+"'>"+exts[i]+"</a>";
	str += "</ul>";
	
	elem.innerHTML = str;
	elem.style.display = 'block';
	
	if(btn) {
		invertColors(btn);
		
		var rect = btn.getBoundingClientRect();
		elem.style.top = (rect.top + 20) + "px";
		elem.style.left = rect.left + "px";
		//console.log(rect, btn);
		
		if(elem.getBoundingClientRect().right > document.documentElement.clientWidth) {
			//console.log("partially invisible...");
			elem.style.left = (rect.right - elem.getBoundingClientRect().width) + "px";
		}
	}
	else {
		console.warn("Element ",btn," is not a node");
	}
	
}
