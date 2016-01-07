/*
 * depends on qon-base.js
 */

//XXX: repaint on window.resize ?

var keyValues = function (obj, keys) {
	var r = [];
	for(var i=0;i<keys.length;i++) {
		r.push(obj[keys[i]]);
	}
	return r;
}

var runD3 = function(url, columns, d3chartfunction, containerId, callbackOk, callbackError) {
	d3.json(url, function(error, data) {
		if(error) {
			if(callbackError) { callbackError(error); }
			else { console.log("error:: ",error); }
			return;
		}
		data = getQonData(data);
		
		try {
			d3chartfunction(data, columns, containerId);
			if(callbackOk) { callbackOk(); }
		}
		catch (e) {
			if(callbackError) { callbackError(e); }
			else {
				console.log(e);
			}
		}
	});
}

/*var runD3multiseries = function(url, columns, containerId, callbackOk, callbackError) {
	runD3(url, columns, d3multiseries, containerId, callbackOk, callbackError);
}*/

var d3multiseries = function(data, columns, containerId) {
	//XXX: future thoughts: return graph instead of writin it to 'containerId'?
	var container = document.getElementById(containerId);
	
	//console.log("container.offsetWidth",container.offsetWidth,"container.offsetHeight", container.offsetHeight);
	var margin = {top: 20, right: 20, bottom: 30, left: 60}, //XXX: left should be dependent on y-values magnitude
		width = container.offsetWidth - margin.left - margin.right,
		height = container.offsetHeight - margin.top - margin.bottom;
	
	var seriesNames = [];
	for(var i=0;i<columns.length;i++) {
		seriesNames.push(columns[i].trim());
	}
	
	//console.log("columns", columns, "seriesNames", seriesNames);
	
	var x = d3.scale.linear()
		.range([0, width]);
	
	var y = d3.scale.linear()
		.range([height, 0]);
	
	var xAxis = d3.svg.axis()
		.scale(x)
		.orient("bottom");
	
	var yAxis = d3.svg.axis()
		.scale(y)
		.orient("left");
	
	var color = d3.scale.category10();
	
	var line = d3.svg.line()
		.interpolate("basis")
		.x(function(d) { return x(d.id); })
		.y(function(d) { return y(d.value); });
	
	var svg = d3.select("#"+containerId).append("svg")
		.attr("width", width + margin.left + margin.right)
		.attr("height", height + margin.top + margin.bottom)
		.append("g")
		.attr("transform", "translate(" + margin.left + "," + margin.top + ")");
	
	/*
	 * multiseries example: http://bl.ocks.org/mbostock/3884955
	 */
	var allCols = d3.keys(data[0]);
	
		if(seriesNames.length==0 || !seriesNames[0]) {
			//XXXdone throw?
			throw 'no column selected - columns are: <code>'+allCols.join(', ')+'</code>';
		}
		else {
			console.log('seriesNames',seriesNames);
		}
		
		//console.log("data[0]",data[0]);
		color.domain(allCols.filter(function(key) { return seriesNames.indexOf(key)>=0; }));
		
		for(var i=0;i<seriesNames.length;i++) {
			if(color.domain().indexOf(seriesNames[i])<0) {
				//XXX show avaiable column that are numeric...
				//XXXdone throw?
				throw 'column <code>'+seriesNames[i]+'</code> not found - columns are: <code>'+allCols.join(', ')+'</code>';
			}
		}
		
		var count = 0;
		data.forEach(function(d) {
			d.id = count++;
		});
	
		var series = color.domain().map(function(name) {
			return {
				name: name,
				values: data.map(function(d) {
					return {id: d.id, value: +d[name]};
				})
			};
		});
	
		x.domain(d3.extent(data, function(d) { return d.id; }));
		var ydom = [
			d3.min(data, function(d) { return d3.min(keyValues(d, seriesNames)); }),
			d3.max(data, function(d) { return d3.max(keyValues(d, seriesNames)); })
		];
		y.domain(ydom);
	
		console.log("data.length", data.length, "ydom", ydom, "typeof ydom[0]:", typeof ydom[0]);
		if(typeof ydom[0] != "number") {
			console.warn("Series data for series ",seriesNames," not numeric [ydom= ", ydom, "]");
			throw "Series data for series "+seriesNames+" not numeric <code>[ydom= "+ydom+"]</code>";
			//return;
		}
	
		svg.append("g")
			.attr("class", "x axis")
			.attr("transform", "translate(0," + height + ")")
			.call(xAxis);
	
		svg.append("g")
			.attr("class", "y axis")
			.call(yAxis);
	
		var serie = svg.selectAll(".serie")
			.data(series)
			.enter().append("g")
			.attr("class", "serie");
		
		serie.append("path")
			.attr("class", "line")
			.attr("d", function(d) { return line(d.values); })
			.style("stroke", function(d) { return color(d.name); });
		
		/* legends from: http://bl.ocks.org/mbostock/3886208 */
		
		var legend = svg.selectAll(".legend")
			.data(color.domain().slice().reverse())
			.enter().append("g")
			.attr("class", "legend")
			.attr("transform", function(d, i) { return "translate(0," + i * 20 + ")"; });
		
		legend.append("rect")
			.attr("x", width - 18)
			.attr("width", 18)
			.attr("height", 18)
			.style("fill", color);
		
		legend.append("text")
			.attr("x", width - 24)
			.attr("y", 9)
			.attr("dy", ".35em")
			.style("text-anchor", "end")
			.text(function(d) { return d; });
}
