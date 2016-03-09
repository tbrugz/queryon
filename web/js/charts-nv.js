
function runNvD3(url, columns, d3chartfunction, containerId, callbackOk, callbackError) {
	d3.json(url, function(error, data) {
		if(error) {
			if(callbackError) { callbackError(error); }
			else { console.log("error:: ",error); }
			return;
		}
		
		try {
			data = getQonData(data);
			//seriesData = sinAndCos();
			seriesData = rows2cols(data, columns);
			
			//console.log("seriesData", seriesData, "containerId", containerId);
			nvD3LineChart(seriesData, containerId);
			if(callbackOk) { callbackOk(); }
			//console.log("ok...");
		}
		catch (e) {
			if(callbackError) { callbackError(e); }
			else {
				console.log(e);
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
	
	console.log("data keys=" , Object.keys(data[0]) , "; cols=" , columns);
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

/*
 * see:
 * https://github.com/novus/nvd3/blob/master/examples/lineChart.html
 * http://nvd3.org/examples/line.html
 * http://stackoverflow.com/questions/30455485/transitionduration-function-does-not-exist-in-nvd3-js
 */
function nvD3LineChart(data, containerId) {
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
		chart.xAxis.axisLabel("Row #")
			.tickFormat(d3.format(',.0d'))
			/*.tickFormat(function(d) {
				return dateFormat(new Date(d))
			})*/
			.staggerLabels(true);
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
