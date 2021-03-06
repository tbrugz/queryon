
/*

var tables;
var views;
var fks;
var execs;
var baseUrl;

var tablesHash = {}; // XXX rename to relationsHash?
var fksHash = {};
var fksPkHash = {};
var fksFkHash = {};
var execsHash = {};

function init(url) {
	baseUrl = url;
	//console.log('baseUrl: '+baseUrl);
	$.ajax({
		url: baseUrl+'/table.json',
		success: function(data) {
			tables = data.table;
			if(tables) { console.log('Load was performed. '+tables.length+' tables loaded'); }
			writeTables();
		}
	});
	$.ajax({
		url: baseUrl+'/view.json',
		success: function(data) {
			views = data.view;
			if(views) { console.log('Load was performed. '+views.length+' views loaded'); }
			writeViews();
		}
	});
	$.ajax({
		url: baseUrl+'/fk.json',
		success: function(data) {
			fks = data.fk;
			if(fks) { console.log('Load was performed. '+fks.length+' FKs loaded'); }
			writeFKs();
		}
	});
	$.ajax({
		url: baseUrl+'/executable.json',
		success: function(data) {
			execs = data.executable;
			if(execs) { console.log('Load was performed. '+execs.length+' execs loaded'); }
			writeExecutables();
		}
	});
}

function writeTables() {
	if(!tables) { return; }
	for(var i=0;i<tables.length;i++) {
		var id = tables[i].schemaName+'.'+tables[i].name;
		//$('<li><a href="'+baseUrl+'/'+id+'/select">'+id+'</li>').appendTo($('#tables'));
		$('<li><a href="javascript:loadTable(\''+id+'\',\'\');">'+id+'</a></li>').appendTo($('#tables-content'));
		tablesHash[id] = tables[i];
	}
}

function writeViews() {
	if(!views) { return; }
	for(var i=0;i<views.length;i++) {
		var id = views[i].schemaName+'.'+views[i].name;
		//$('<li><a href="'+baseUrl+'/'+id+'/select">'+id+'</li>').appendTo($('#tables'));
		$('<li><a href="javascript:loadTable(\''+id+'\',\'\');">'+id+'</a></li>').appendTo($('#tables-content'));
		tablesHash[id] = views[i];
	}
}

function writeFKs() {
	if(!fks) { return; }
	for(var i=0;i<fks.length;i++) {
		var id = fks[i].schemaName+'.'+fks[i].name;
		var fkdesc = id+': '+fks[i].fkTable+' -> '+fks[i].pkTable;
		$('<li>'+fkdesc+'</li>').appendTo($('#fks-content'));
		fksHash[id] = fks[i];

		var idPk = fks[i].pkTableSchemaName+'.'+fks[i].pkTable;
		if(fksPkHash[idPk]==null) { fksPkHash[idPk] = []; }
		fksPkHash[idPk].push(fks[i]);
		var idFk = fks[i].fkTableSchemaName+'.'+fks[i].fkTable;
		if(fksFkHash[idFk]==null) { fksFkHash[idFk] = []; }
		fksFkHash[idFk].push(fks[i]);
	}
	console.log('fksHash',fksFkHash);
}

function writeExecutables() {
	if(!execs) { return; }
	for(var i=0;i<execs.length;i++) {
		var id = execs[i].schemaName+'.'+execs[i].name;
		var execdesc = id+': '+execs[i].type+(execs[i].packageName!=null?' [pkg: '+execs[i].packageName+']':'');
		$('<li>'+execdesc+'</li>').appendTo($('#executables-content'));
		execsHash[id] = execs[i];
	}
}

// XXX rename to loadRelations? ...
function loadTable(tableid, filter) {
	return loadTableHtml(tableid, filter);
	//return loadTableJson(tableid, filter);
}

function loadTableHtml(tableid, filter) {
	$.ajax({
		url: baseUrl+'/'+tableid+filter+'.html',
		success: function(data) {
			byId('main-content').innerHTML = data;
			//$('#main-content').html(data);
			window.location.replace('#main-content');
			//window.location = '#main-content';
		}
	});
}

function loadTableJson(tableid, filter) {
	var tablearr = tableid.split('.');
	var tableschema = tablearr[0];
	var tablename = tablearr[1];
	//console.log(tablename);
	$.ajax({
		url: baseUrl+'/'+tableid+filter+'.json',
		success: function(data) {
			//console.log('success: '+tablename);
			byId('xtraTitle').innerHTML = ': '+tableid;
			//$('#xtraTitle').html(': '+tableid);
			var buf = '<table>';
			data = data[tablename];

			var fks = fksFkHash[tableid];
			console.log(tableid+': fks:: ',fks);

			var cols = getColumnsFromRelation(tablesHash[tableid]);
			var tablesLinks = {};
			for(var j=0;j<cols.length;j++) {
				//cols[j] = cols[j].trim();
				buf += '<th>'+cols[j]+'</th>';
				var tableMatch = matchFkTable(fks, cols[j]);
				//console.log(tableMatch);
				if(tableMatch!=null) { tablesLinks[cols[j]] = tableMatch; }
			}
			console.log('cols',cols);
			console.log('tableLinks:',tablesLinks);
			
			for(var i=0;i<data.length;i++) {
				buf += '<tr>';
				for(var j=0;j<cols.length;j++) {
					//XXX: construct filter URL for each FK?
					var col = cols[j];
					var dataz = data[i][col];
					if(dataz!=null) {
						//buf += '<td>';
						if(tablesLinks[col]) {
							buf += '<td><a href="javascript:loadTable(\''+tablesLinks[col]+'\',\'/'+data[i][col]+'\');">'+data[i][col]+'</a></td>';
						}
						else {
							buf += '<td>'+data[i][col]+'</td>';
						}
						//buf += '</td>';
					}
					else {
						buf += '<td class="tdnull"/>';
					}
				}
				buf += '</tr>\n';
			}
			buf += '</table>';
			if(filter=='') {
				byId('main-content').innerHTML = buf;
				//$('#main-content').html(buf);
				window.location.replace('#main-content');
			}
			else {
				var divid = 'table_'+stringToId(tableid+'_'+filter);
				byId('main-content').innerHTML += '<div id="'+divid+'" class="detail"><h4>'+tableid+' '+filter+'</h4>'+buf+'</div>';
				//$('#main-content').append('<div id="'+divid+'" class="detail"><h4>'+tableid+' '+filter+'</h4>'+buf+'</div>');
				window.location.replace('#'+divid);
			}
		}
	});
}

function stringToId(str) {
	return str.replace(/[\/\-\.]/g, '');
}

function matchFkTable(fks, col) {
	if(fks!=null) {
		for(var i=0;i<fks.length;i++) {
			var cols = getScalarArrayFromValue(fks[i].fkColumns);
			var idx = cols.indexOf(col);
			//var idx = $.inArray(col, cols);
			if(idx>=0) {
				console.log('idx', idx, fks[i].pkTable);
				return fks[i].pkTableSchemaName+'.'+fks[i].pkTable;
			}
		}
	}
	return null;
}

*/
