
var tables;
var fks;
var execs;
var baseUrl;

var tablesHash = {};
var fksHash = {};
var fksPkHash = {};
var execsHash = {};

function init(url) {
	baseUrl = url;
	$.ajax({
		url: baseUrl+'/table/status.json',
		success: function(data) {
			tables = data.status;
			//alert('Load was performed. '+tables.length+' tables loaded');
			writeTables();
		}
	});
	$.ajax({
		url: baseUrl+'/fk/status.json',
		success: function(data) {
			fks = data.status;
			//alert('Load was performed. '+fks.length+' FKs loaded');
			writeFKs();
		}
	});
	$.ajax({
		url: baseUrl+'/executable/status.json',
		success: function(data) {
			execs = data.status;
			writeExecutables();
		}
	});
}

function writeTables() {
	for(var i=0;i<tables.length;i++) {
		var id = tables[i].schemaName+'.'+tables[i].name;
		//$('<li><a href="'+baseUrl+'/'+id+'/select">'+id+'</li>').appendTo($('#tables'));
		$('<li><a href="javascript:loadTable(\''+id+'\');">'+id+'</a></li>').appendTo($('#tables-content'));
		tablesHash[id] = tables[i];
	}
}

function writeFKs() {
	for(var i=0;i<fks.length;i++) {
		var id = fks[i].schemaName+'.'+fks[i].name;
		var fkdesc = id+': '+fks[i].fkTable+' -> '+fks[i].pkTable;
		$('<li>'+fkdesc+'</li>').appendTo($('#fks-content'));
		fksHash[id] = fks[i];

		var idPk = fks[i].pkTableSchemaName+'.'+fks[i].pkTable;
		fksPkHash[idPk] = fks[i];
	}
}

function writeExecutables() {
	for(var i=0;i<execs.length;i++) {
		var id = execs[i].schemaName+'.'+execs[i].name;
		var execdesc = id+': '+execs[i].type+(execs[i].packageName!=null?' [pkg: '+execs[i].packageName+']':'');
		$('<li>'+execdesc+'</li>').appendTo($('#executables-content'));
		execsHash[id] = execs[i];
	}
}

function loadTable(tableid) {
	return loadTableJson(tableid);
}

function loadTableHtml(tableid) {
	$.ajax({
		url: baseUrl+'/'+tableid+'/select.html',
		success: function(data) {
			$('#main-content').html(data);
			window.location.replace('#main-content');
			//window.location = '#main-content';
		}
	});
}

function loadTableJson(tableid) {
	var tablename = tableid.split('.')[1];
	console.log(tablename);
	$.ajax({
		url: baseUrl+'/'+tableid+'/select.json',
		success: function(data) {
			console.log('success: '+tablename);
			var buf = '<table>';
			data = data[tablename];
			var columns = tablesHash[tableid].columnNames;
			columns = columns.replace(/[\[\]]/g, '');

			var cols = columns.split(',');
			for(var j=0;j<cols.length;j++) {
				cols[j] = cols[j].trim();
				buf += '<th>'+cols[j]+'</th>';
			}
			console.log(cols);
			
			for(var i=0;i<data.length;i++) {
				buf += '<tr>';
				for(var j=0;j<cols.length;j++) {
					var col = cols[j]; //.trim();
					var dataz = data[i][col];
					if(dataz!=null) {
						buf += '<td>'+data[i][col]+'</td>';
					}
					else {
						buf += '<td/>';
					}
				}
				buf += '</tr>\n';
			}
			buf += '</table>';
			$('#main-content').html(buf);
			window.location.replace('#main-content');
		}
	});
}
