//---------------- stats functions

function getLinearCategoriesLimits(min, max, numCategories) {
	var list = [];
	var amplitude = max-min;
	var interval = amplitude/numCategories;
	for(var i = 0;i<=numCategories;i++) {
		list.push(min+interval*i);
	}
	return list;
}

function getLinearCategoriesLimitsMultipleValues(values, numCategories) {
	//XXX error if numCategories <= 1
	var list = [];
	var div = (values.length-1)/(numCategories-1);
	for(var i = 0;i<numCategories;i++) {
		var mvalue = div*i;
		//if(i==0) { list.push(values[0]); continue; }
		//if(i==numCategories-1) { list.push(values[values.length-1]); continue; }
		//if(isInteger(mvalue)) { list.push(values[mvalue]); continue; }
		var mlow = Math.floor(mvalue);
		var mhigh = Math.ceil(mvalue);
		var min = values[mlow];
		var max = values[mhigh];
		var amplitude = max-min;
		var mreminder = mvalue%1;
		var newvalue = min+amplitude*mreminder;
		//console.log("["+i+"]","div=",div,"mvalue=",mvalue,"mlow=",mlow,"mhigh=",mhigh,"min=",min,"max=",max,"ampl=",amplitude,"newvalue=",newvalue);
		list.push(newvalue);
	}
	return list;
}

function getLogCategoriesLimits(min, max, numCategories) {
	var negativeDiff = 0;
	if(min<1) {
		negativeDiff = min-1;
	}
	var newMin = Math.log(min-negativeDiff);
	var newMax = Math.log(max-negativeDiff);
	var list = [];
	
	var amplitude = newMax-newMin;
	var interval = amplitude/numCategories;
	for(var i = 0;i<=numCategories;i++) {
		list.push(Math.exp(newMin+interval*i)+negativeDiff);
	}
	return list;
}

function getQuantileCategoriesLimits(values, numCategories) {
	var sorted = values.sort(compareNumbers);
	var valuesPerQuantile = Math.ceil(sorted.length/numCategories);
	
	var list = [];
	for(var i = 1;i<=sorted.length;i++) {
		if(i==1) { list.push(sorted[i-1]); }
		else if(i%valuesPerQuantile==0) {
			list.push(sorted[i-1]);
		}
		else if(i==sorted.length) { list.push(sorted[i-1]); }
	}
	if(list.length<numCategories) {
		console.log(">> prev list.length="+list.length+" / numCategories="+numCategories);
		list.push(sorted[sorted.length-1]);
	}
	console.log("min=",min(values),"max=",max(values),"#values=",sorted.length,"#categories=",numCategories,"valuesPerQuantile=",valuesPerQuantile,"valuesFloat=",(sorted.length/numCategories),"list.length=",list.length);
	console.log("list=",list);
	return list;
}

function isInteger(x) {
	return x % 1 === 0;
}

function max(series) {
	var max = -Number.MAX_VALUE;
	for(var i in series) {
		if(series[i]>max) { max = series[i]; }
	}
	return Number(max);
}

function min(series) {
	var min = Number.MAX_VALUE;
	for(var i in series) {
		//console.log("i:"+i+" / "+series[i]);
		if(series[i]<min) { min = series[i]; }
	}
	return Number(min);
}

function hexString(number) {
	var hex = number.toString(16);
	if(hex.length==1) { hex = "0"+hex; }
	return hex;
}

//see: http://stackoverflow.com/questions/149055/how-can-i-format-numbers-as-money-in-javascript
function formatNumber(n, c, d, t) {
	var c = isNaN(c = Math.abs(c)) ? 2 : c, d = d == undefined ? "," : d, t = t == undefined ? "." : t, s = n < 0 ? "-" : "", i = parseInt(n = Math.abs(+n || 0).toFixed(c)) + "", j = (j = i.length) > 3 ? j % 3 : 0;
	return s + (j ? i.substr(0, j) + t : "") + i.substr(j).replace(/(\d{3})(?=\d)/g, "$1" + t) + (c ? d + Math.abs(n - i).toFixed(c).slice(2) : "");
};

function formatFloat(n) {
	return formatNumber(n, 2, '.', ',');
};

function compareNumbers(a, b) {
	return a - b;
}

function seriesValues(series) {
	var ret = []
	var count = 0;
	for(var i in series) {
		ret[count++] = series[i];
	}
	return ret;
}

//----

var DEFAULT_FILL_COLOR = "#cccccc";
var ERROR_FILL_COLOR = "#444444";
var global_selectCatIdElements = 0;

function normalizeNum(float) {
	return Math.round(float * 1000);
	//return float; //float.toFixed(0);
}

function getCat(value, catData) {
	value = normalizeNum(value);
	for(id in catData) {
		//$("#debug").append("id: "+id+"; v:"+value+":"+catData[id].startval+"-"+catData[id].endval+"\n");
		if(value >= normalizeNum(catData[id].startval) && value <= normalizeNum(catData[id].endval)) {
			return id;
		}
	}
	return null;
}

function genCategoriesFromLimits(vals) {
	var cats = {};
	for(var i=1;i<vals.length;i++) {
		var cat = {};
		cat.startval = vals[i-1];
		cat.endval = vals[i];
		//cat.name = getNameFromId(i);
		cat.styleId = i;
		//cats.push(cat);
		cats[i] = cat;
	}
	return cats;
}

function procStylesFromCategories(cats, colorFrom, colorTo, valueLabel) {
	//console.log(colorFrom);
	//console.log(colorTo);

	var numCat = Object.keys(cats).length - 1;
	var colorsA = getLinearCategoriesLimits(parseInt(colorFrom.substring(0, 2), 16), parseInt(colorTo.substring(0, 2), 16), numCat);
	var colorsB = getLinearCategoriesLimits(parseInt(colorFrom.substring(2, 4), 16), parseInt(colorTo.substring(2, 4), 16), numCat);
	var colorsG = getLinearCategoriesLimits(parseInt(colorFrom.substring(4, 6), 16), parseInt(colorTo.substring(4, 6), 16), numCat);
	var colorsR = getLinearCategoriesLimits(parseInt(colorFrom.substring(6, 8), 16), parseInt(colorTo.substring(6, 8), 16), numCat);
	
	//console.log(colorsA);
	//console.log(colorsB);
	//console.log(colorsG);
	//console.log(colorsR);

	var i=0;
	for(var c in cats) {
		cats[c].kmlcolor = hexString(Math.round(colorsA[i])) + hexString(Math.round(colorsB[i])) + hexString(Math.round(colorsG[i])) + hexString(Math.round(colorsR[i]));
		//XXX: color -> rgbcolor?
		cats[c].color = hexString(Math.round(colorsR[i])) + hexString(Math.round(colorsG[i])) + hexString(Math.round(colorsB[i]));
		//TODO: format numbers! integer, float, ...
		//cats[c].description = cats[c].startval + " &lt; # " + valueLabel + " &lt; " + cats[c].endval;
		cats[c].description = formatFloat(cats[c].startval) + " &le; # " + valueLabel + " &le; " + formatFloat(cats[c].endval);
		
		//console.log('cat: '+c+'/'+colorsA[i]+'/'+colorsB[i]);
		//console.log(cats[c].kmlcolor);
		i++;
	}
	
	return cats;
}

function extractInts(colors, idx) {
	var ints = [];
	for(var i=0;i<colors.length;i++) {
		ints.push(parseInt(colors[i].substring(idx, idx+2), 16));
	}
	return ints;
}

function procStylesFromCategoriesMultipleColors(cats, colors, valueLabel) {
	//console.log(colors);

	var numCat = Object.keys(cats).length;
	var colorsA = getLinearCategoriesLimitsMultipleValues(extractInts(colors, 0), numCat);
	var colorsB = getLinearCategoriesLimitsMultipleValues(extractInts(colors, 2), numCat);
	var colorsG = getLinearCategoriesLimitsMultipleValues(extractInts(colors, 4), numCat);
	var colorsR = getLinearCategoriesLimitsMultipleValues(extractInts(colors, 6), numCat);
	//console.log(colors)
	//console.log(extractInts(colors, 0), extractInts(colors, 2), extractInts(colors, 4), extractInts(colors, 6))

	var i=0;
	for(var c in cats) {
		cats[c].kmlcolor = hexString(Math.round(colorsA[i])) + hexString(Math.round(colorsB[i])) + hexString(Math.round(colorsG[i])) + hexString(Math.round(colorsR[i]));
		//XXX: color -> rgbcolor?
		cats[c].color = hexString(Math.round(colorsR[i])) + hexString(Math.round(colorsG[i])) + hexString(Math.round(colorsB[i]));
		//TODO: format numbers! integer, float, ...
		//cats[c].description = cats[c].startval + " &lt; # " + valueLabel + " &lt; " + cats[c].endval;
		cats[c].description = formatFloat(cats[c].startval) + " &le; # " + valueLabel + " &le; " + formatFloat(cats[c].endval);
		
		//console.log('cat: '+c+'/'+colorsA[i]+'/'+colorsB[i]);
		//console.log(cats[c].kmlcolor);
		i++;
	}
	
	return cats;
}

/*function applyStyleColor(gPlaceMarks, cats) {
	var count = 0;
	for(var id in gPlaceMarks) {
		var placemark = gPlaceMarks[id];
		//var bef = placemark.fillColor;
		
		placemark.catId = getCat(placemark.dataValue, cats);
		var cat = cats[placemark.catId];
		if(cat==undefined) {
			console.warn('undefined id: '+id+' / '+placemark+' / '+placemark.catId);
			continue;
		}
		placemark.kmlColor = cats[placemark.catId].kmlcolor;
		placemark.fillColor = placemark.kmlColor.substring(6,8) + placemark.kmlColor.substring(4,6) + placemark.kmlColor.substring(2,4);
		//console.log('b: '+bef+' / a: '+placemark.fillColor);
		placemark.setMap(map); //atualiza placemark no mapa - 'null' retira elemento do mapa
		count++;
		//if(count>10) { break; }
	}
	//console.log(count+' / '+Object.keys(gmapsPlaces).length+' / '+Object.keys(places).length);
	//console.log(gmapsPlaces);
}*/

function applySeriesDataAndStyle(gPlaceMarks, seriesData, catData, map) {
	var countOk = 0, countUndef = 0;
	for(var id in gPlaceMarks) {
		var placemark = gPlaceMarks[id];
		
		//set data
		placemark.dataValue = seriesData.series[id];
		placemark.catId = getCat(placemark.dataValue, catData);
		//TODO: numberFormat (grouping char, ...)
		//console.log("seriesData=",seriesData);
		
		placemark.description = seriesData.valueLabel + ': '+formatFloat(seriesData.series[id])
			+ (seriesData.measureUnit?' ' + seriesData.measureUnit:"");
		if(placemark.catId==undefined) {
			//console.warn('undefined cat: '+id+' / '+placemark.name); //+' / '+placemark.catId);
			placemark.fillColor = ERROR_FILL_COLOR;
			placemark.setMap(null);
			placemark.setMap(map);
			countUndef++;
			//TODO: option to remove element from map
			continue;
		}
		
		//set style & map
		placemark.kmlColor = catData[placemark.catId].kmlcolor;
		placemark.rgbColor = '#'+catData[placemark.catId].color;
		placemark.fillColor = placemark.rgbColor;
		//placemark.fillColor = placemark.kmlColor.substring(6,8) + placemark.kmlColor.substring(4,6) + placemark.kmlColor.substring(2,4);
		//TODO: add category in description
		
		//console.log('placemark: '+id+' cat: '+placemark.catId+' color: '+placemark.fillColor+" map: "+map);
		
		//atualiza placemark no mapa - 'null' retira elemento do mapa
		placemark.setMap(null);
		placemark.setMap(map);
		
		placemark.listener = google.maps.event.addListener(placemark, 'click', function(event) {
			//console.log('click!');
			//console.log(placemark);
			//console.log(this);
			showPlaceInfo(this.id, this.name, this.description, this.catId);
		});
		
		countOk++;
	}
	console.log('applySeriesDataCount: ok=',countOk,' err=',countUndef);
}

function removeSeriesDataWhenPlacemarkNotFound(gPlaceMarks, seriesData) {
	var count = 0;
	console.log('removeSeriesDataWhen... before: '+Object.keys(seriesData.series).length+" / "+Object.keys(gPlaceMarks).length);
	for(var id in seriesData.series) {
		//console.log(gPlaceMarks[id]);
		if(gPlaceMarks[id]==undefined) { delete seriesData.series[id]; }
	}
	console.log('removeSeriesDataWhen... after: '+Object.keys(seriesData.series).length+" / "+Object.keys(gPlaceMarks).length);
}

function showPlaceInfo(id, name, description, catId) {
	//TODO: do not show id (?); do not show name if null
	//console.log(id+" / "+name);
	//console.log(placemark);
	document.getElementById('placeId').innerHTML = id;
	document.getElementById('placeName').innerHTML = name;
	document.getElementById('placeDesc').innerHTML = description;
	document.getElementById('placeCat').innerHTML = catId;
	if(name==null) {
		document.getElementById('placeName').style.display = 'none';
		document.getElementById('placeNameLabel').style.display = 'none';
	}
	else {
		document.getElementById('placeName').style.display = 'inherit';
		document.getElementById('placeNameLabel').style.display = 'inherit';
	}
	document.getElementById('place_info').style.display = 'block';
}

function showCategoryInfo(id, name, description) {
	document.getElementById('category_info_id').innerHTML = id;
	document.getElementById('category_info_text').innerHTML = description;
	document.getElementById('category_info').style.display = 'block';
	document.getElementById('map_canvas').style.bottom = 
		"" + (parseInt(document.getElementById('category_info').style.height) + 0.4) + "em";
	//mapcanvas.style.right = "" + (parseInt(container.style.width) + 0.3) + "em";
	
	//TODOne: remove old buttons!
	var container = document.getElementById('category_info_button_container');
	/*while (container.hasChildNodes()) {
		container.removeChild(container.lastChild);
	}

	var catbutton = document.createElement('a');
	catbutton.setAttribute('href', '#');
	catbutton.setAttribute('class', 'medium');
	catbutton.innerHTML = ;
	catbutton.setAttribute('onClick', 'selectFromCategory('+id+');');
	container.appendChild(catbutton);*/
	if(global_selectCatIdElements>0) {
		selectFromCategory(id);
		//container.innerHTML = "[<a href='#' onClick='selectFromAllCategories("+id+");'>show all elements</a>] [#elements = "+countIn+"];
	}
	else {
		container.innerHTML = "[<a href='#' onClick='selectFromCategory("+id+");'>show elements from cat #"+id+"</a>]";
	}
}

function closeCatInfo() {
	document.getElementById('category_info').style.display='none';
	document.getElementById('map_canvas').style.bottom = 0;
	if(global_selectCatIdElements>0) {
		selectFromAllCategories();
	}
}

function selectFromCategory(selectCatId) {
	//XXX: gmapsPlaces as parameter
	//console.log('selectCatId: '+selectCatId);
	var countIn = 0, countOut = 0;
	for(var id in gmapsPlaces) {
		var placemark = gmapsPlaces[id];
		
		if(placemark.catId==undefined) {
			continue;
		}
		
		if(placemark.catId==selectCatId) {
			placemark.fillColor = placemark.rgbColor;
			placemark.setMap(null);
			placemark.setMap(map);
			countIn++;
		}
		else {
			placemark.fillColor = DEFAULT_FILL_COLOR;
			placemark.setMap(null);
			placemark.setMap(map);
			countOut++;
		}
	}
	//console.log('selectFromCategory.count: '+countIn+' / '+countOut+' // in+out = '+(countIn+countOut)+' / all = '+Object.keys(gmapsPlaces).length);

	var container = document.getElementById('category_info_button_container');
	container.innerHTML = "[<a href='#' onClick='selectFromAllCategories("+selectCatId+");'>show all elements</a>] [#elements = "+countIn+"]";
	
	global_selectCatIdElements = selectCatId;
}

function selectFromAllCategories(oldSelectCatId) {
	//XXX: gmapsPlaces as parameter
	var countIn = 0, countOut = 0;
	for(var id in gmapsPlaces) {
		var placemark = gmapsPlaces[id];
		
		if(placemark.catId==undefined) {
			continue;
		}
		
		placemark.fillColor = placemark.rgbColor;
		placemark.setMap(null);
		placemark.setMap(map);
		countIn++;
	}
	//console.log('selectFromAllCategories.count: '+countIn);
	
	if(oldSelectCatId) {
		var container = document.getElementById('category_info_button_container');
		container.innerHTML = "[<a href='#' onClick='selectFromCategory("+oldSelectCatId+");'>show elements from cat #"+oldSelectCatId+"</a>]";
	}
	
	global_selectCatIdElements = 0;
}
