
var MIN_X_TICKS = 11;
var MAX_X_TICKS = 36;

function runNvD3(url, columns, xaxis, d3chartfunction, containerId, callbackOk, callbackError) {
	d3.json(url, function(error, data) {
		if(error) {
			if(callbackError) { callbackError(error); }
			else { console.log("runNvD3: error: ",error); }
			return;
		}
		
		try {
			var qondata = getQonData(data);
			//seriesData = sinAndCos();
			var seriesData = rows2cols(qondata, columns);
			var xlabelsData = null;
			if(xaxis) {
				xlabelsData = rows2arr(qondata, xaxis);
			}
			
			//console.log("seriesData", seriesData, "containerId", containerId);
			//console.log("xlabelsData", xlabelsData);
			nvD3LineChart(seriesData, containerId, xlabelsData, xaxis);
			if(callbackOk) { callbackOk(); }
			//console.log("ok...");
		}
		catch (e) {
			console.log("runNvD3: error[2]: ",e);
			if(callbackError) {
				callbackError(e);
			}
		}
	});
}

function rows2cols(data, columns) {
	var ret = [];
	var keys = Object.keys(data[0]);
	if(columns) {
		var keysOk = [], keysNok = [];
		for(var i=0; i < columns.length; i++) {
			var col = columns[i].trim()
			if(keys.indexOf(col)>=0) {
				keysOk.push(col);
			}
			else {
				keysNok.push(col);
			}
		}
		console.log("keysOk=",keysOk,"; keysNok=",keysNok);
		if(keysNok.length>0) {
			console.warn("columns not found: ",keysNok);
			throw "columns not found: "+keysNok+" [columns available: "+keys.join(", ")+"]";
		}
		keys = keysOk;
	}
	
	//console.log("data keys=" , Object.keys(data[0]) , "; cols=" , columns);
	for(var i=0; i < keys.length; i++) {
		var elem = {};
		elem.key = keys[i];
		
		var vals = [];
		for(var j=0; j < data.length; j++) {
			vals.push( { x: j, y: data[j][keys[i]] } );
		}
		elem.values = vals;
		ret.push(elem);
	}
	return ret;
}

function rows2arr(data, column) {
	var keys = Object.keys(data[0]);
	if(column) {
		if(keys.indexOf(column)<0) {
			//console.warn("x-axis col not fount: "+column);
			throw "x-axis column not fount: "+column+" [columns available: "+keys.join(", ")+"]";
		}
	}
	
	//console.log("rows2arr: data keys=" , Object.keys(data[0]) , "; column=" , column);
	var vals = [];
	for(var i=0; i < data.length; i++) {
		vals.push( { x: i, label: data[i][column] } );
		//vals.push( data[i][column] );
	}
	return vals;
}

/*
 * see:
 * https://github.com/novus/nvd3/blob/master/examples/lineChart.html
 * http://nvd3.org/examples/line.html
 * http://stackoverflow.com/questions/30455485/transitionduration-function-does-not-exist-in-nvd3-js
 */
function nvD3LineChart(data, containerId, xlabelsData, xaxis) {
	console.log("nvD3LineChart...");
	nv.addGraph(function() {
		var chart = nv.models.lineChart().options({
			transitionDuration : 300,
			useInteractiveGuideline : true
		});
		/*var dateFormat = d3.time.format.multi([
			["%Y-%m-%d %H:%M:%S.%L", function(d) { return d.getMilliseconds(); }],
			["%Y-%m-%d %H:%M:%S", function(d) { return d.getSeconds()||d.getMinutes()||d.getHours(); }],
			["%Y-%m-%d", function() { return true; }]
		]);*/
		// chart sub-models (ie. xAxis, yAxis, etc) when accessed directly,
		// return themselves, not the parent chart, so need to chain separately
		var numOfTicks = (data[0].values.length>MAX_X_TICKS)?MAX_X_TICKS:data[0].values.length;
		numOfTicks = (numOfTicks<MIN_X_TICKS)?MIN_X_TICKS:numOfTicks;
		console.log("numOfTicks",numOfTicks,"data[0].values.length",data[0].values.length);//,"data",data);
		
		if(xlabelsData && xaxis) {
			chart.xAxis.
				axisLabel(xaxis).
				ticks(numOfTicks).
				tickFormat(function(d){
					//console.log("d",d,"xlabelsData[d]",xlabelsData[d]);
					return xlabelsData[d]?xlabelsData[d].label:d;
				});
		}
		else {
			chart.xAxis.
				axisLabel("Row #").
				ticks(numOfTicks).
				tickFormat(d3.format(',.0d'));
		}
		
		chart.xAxis.staggerLabels(true);
		chart.yAxis//.axisLabel('Voltage (v)')
			.tickFormat(function(d) {
			if (d == null) {
				return 'N/A';
			}
			return d3.format(',.2f')(d);
		});
		//data = sinAndCos();
		d3.select('#' + containerId).append('svg').datum(data).call(chart);
		nv.utils.windowResize(chart.update);
		return chart;
	});
}

/* --------------------------------------- */

// http://stackoverflow.com/questions/2483919/how-to-save-svg-canvas-to-local-filesystem
// http://ggvis.rstudio.com/
function svgDownloadLink(svg) {
	// Add some critical information
	svg.setAttribute("version", "1.1");
	svg.setAttribute("xmlns", "http://www.w3.org/2000/svg");
	//$("svg").attr({ version: '1.1' , xmlns:"http://www.w3.org/2000/svg"});
	
	var b64 = btoa(svg.outerHTML);
	
	// Works in recent Webkit(Chrome)
	//$("body").append($("<img src='data:image/svg+xml;base64,\n"+b64+"' alt='file.svg'/>"));
	
	// Works in Firefox 3.6 and Webit and possibly any browser which supports the data-uri
	return "<a href-lang='image/svg+xml' href='data:image/octet-stream;base64,\n"+b64+"' download='plot.svg' title='plot.svg'>download SVG</a>";
}

/*function sinAndCos() {
  var sin = [],sin2 = [],
      cos = [];

  // Data is represented as an array of {x,y} pairs.
  for (var i = 0; i < 100; i++) {
    sin.push({x: i, y: Math.sin(i/10)});
    sin2.push({x: i, y: Math.sin(i/10) *0.25 + 0.5});
    cos.push({x: i, y: .5 * Math.cos(i/10)});
  }

  //Line chart data should be sent as an array of series objects.
  return [
    {
      values: sin,      //values - represents the array of {x,y} data points
      key: 'Sine Wave', //key  - the name of the series.
      color: '#ff7f0e'  //color - optional: choose your own line color.
    },
    {
      values: cos,
      key: 'Cosine Wave',
      color: '#2ca02c'
    },
    {
      values: sin2,
      key: 'Another sine wave',
      color: '#7777ff',
      area: true      //area - set to true if you want this line to turn into a filled area chart.
    }
  ];
}*/
