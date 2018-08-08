/*
 * should not depend on anything...
 */

var numericSqlTypes = ["TINYINT", "SMALLINT", "INTEGER", "BIGINT", "DECIMAL", "NUMERIC", "NUMBER", "REAL", "FLOAT", "DOUBLE",
	"DOUBLE PRECISION", "INT2", "INT4", "INT8"];

var blobSqlTypes = ["BLOB", "RAW", "LONG RAW", "BYTEA"]; // see DBUtil.java

function getQonData(qonData) { //JSON...
	if(Array.isArray(qonData)) { return qonData; }
	var keys = Object.keys(qonData);
	var index = 0;
	if(keys[index].startsWith("$") || keys[index].startsWith("@")) { index = 1; }
	return qonData[keys[index]];
}

function append2url(url, append) {
	if(append==null || append=='') {
		return url;
	}

	if(url && url.indexOf("?") > -1) {
		return url+"&"+append;
	}
	/*else if(append!=null && append.indexOf("?")==0) {
		return url+append;
	}*/
	else {
		if(url==null) { url = ''; }
		return url+"?"+append;
	}
}

function getScalarArrayFromValue(value) {
	if(typeof value === "string") {
		if(value.substring(0,1)=="[") { value = value.substring(1); }
		if(value.substring(value.length-1)=="]") { value = value.substring(0,value.length-1) }
		return value.split(",");
	}
	if(Array.isArray(value)) {
		if(value.length>0) {
			var o1 = value[0];
			if(o1 === Object(o1)) {
				var keys = Object.keys(o1);
				if(keys.length==1) {
					var ret = [];
					for(var i=0;i<value.length;i++) {
						ret.push(value[i][keys[0]]);
					}
					return ret;
				}
			}
			else {
				return value;
			}
		}
		else {
			return value;
		}
	}
	return null;
}

// polyfill IE - https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/startsWith

if (!String.prototype.startsWith) {
	String.prototype.startsWith = function(search, pos) {
		return this.substr(!pos || pos < 0 ? 0 : +pos, search.length) === search;
	};
}
