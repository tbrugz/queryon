function getQueryString(formname) {
	var form = document.forms[formname];
	var qstr = "";
	function GetElemValue(name, value) {
		qstr += (qstr.length > 0 ? "&" : "")
		+ escape(name).replace(/\+/g, "%2B") + "="
		+ escape(value ? value : "").replace(/\+/g, "%2B");
		//+ escape(value ? value : "").replace(/\n/g, "%0D");
	}
	var elemArray = form.elements;
	for ( var i = 0; i < elemArray.length; i++) {
		var element = elemArray[i];
		var elemType = element.type.toUpperCase();
		var elemName = element.name;
		if (elemName) {
			if (elemType == "TEXT"
			|| elemType == "TEXTAREA"
			//|| elemType == "PASSWORD"
			//|| elemType == "BUTTON"
			//|| elemType == "RESET"
			//|| elemType == "SUBMIT"
			//|| elemType == "FILE"
			//|| elemType == "IMAGE"
			|| elemType == "HIDDEN")
				GetElemValue(elemName, element.value);
			else if (elemType == "CHECKBOX" && element.checked)
				GetElemValue(elemName,
				element.value ? element.value : "On");
			else if (elemType == "RADIO" && element.checked)
				GetElemValue(elemName, element.value);
			else if (elemType.indexOf("SELECT") != -1)
				for ( var j = 0; j < element.options.length; j++) {
					var option = element.options[j];
					if (option.selected)
						GetElemValue(elemName,
						option.value ? option.value : option.text);
				}
		}
	}
	return qstr;
}

function changeColor(elementChanged, elementToChange) {
	/*document.getElementById(elementToChange).value = 'BB'
		+ document.getElementById(elementChanged).value.substring(4,6)
		+ document.getElementById(elementChanged).value.substring(2,4)
		+ document.getElementById(elementChanged).value.substring(0,2);*/
	document.getElementById(elementToChange).value = rgbColor2kmlColor(document.getElementById(elementChanged).value);
}

function rgbColor2kmlColor(rgb) {
	return 'BB'
		+ rgb.substring(4,6)
		+ rgb.substring(2,4)
		+ rgb.substring(0,2);
}

function showDivs(divs) {
	for(var i=0;i<divs.length;i++) {
		document.getElementById(divs[i]).style.display = 'block';
	}
}

function openInGoogleMaps(formname) {
	//http://maps.google.com.br/maps?q=
	var geoUrl = document.getElementById(formname).action+"?"+getQueryString(formname);
	var gmapsUrl = "http://maps.google.com.br/maps?q="+encodeURIComponent(geoUrl);
	window.open(gmapsUrl, "_blank");
}

//------------------- gmaps

var map;
var geoXml;

function loadKml(formName, mapCanvasName, mapLocationDivName) {
	var geoUrl = document.getElementById(formName).action+"?"+getQueryString(formName);
	document.getElementById(mapLocationDivName).innerHTML = geoUrl;
	
	var myOptions = {
		zoom: 4,
		position: new google.maps.LatLng(-15, -47),
		mapTypeId: google.maps.MapTypeId.ROADMAP
	};
	map = new google.maps.Map(document.getElementById(mapCanvasName), myOptions);
		
	map.setCenter(myOptions.position);
	
	var georssLayer = new google.maps.KmlLayer(geoUrl);
	georssLayer.setMap(map);
}

function initMap(mapCanvasName) {
	var myOptions = {
		zoom: 4,
		position: new google.maps.LatLng(-15, -47),
		mapTypeId: google.maps.MapTypeId.ROADMAP
	};
	var map = new google.maps.Map(document.getElementById(mapCanvasName), myOptions);
	map.setCenter(myOptions.position);
	return map;
}

function loadKmlInMap(formName, map, mapLocationDivName) {
	var geoUrl = document.getElementById(formName).action+"?"+getQueryString(formName);
	if(document.getElementById(mapLocationDivName)) {
		document.getElementById(mapLocationDivName).innerHTML = geoUrl;
	}
	
	var kmlLayer = new google.maps.KmlLayer(geoUrl);
	kmlLayer.setMap(map);

	return kmlLayer;
}
