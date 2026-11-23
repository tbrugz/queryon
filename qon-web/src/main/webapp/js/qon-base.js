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

function getLocationHash() {
	var hash = window.location.hash;
	//return hash;
	return hash.replaceAll("%7C", "|");
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
			if(isObject(o1)) {
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

function qonContextPath() {
    return "";
}

/*
 * Minimal replacement for the subset of jQuery.ajax() used by QueryOn pages.
 * opts: url, type|method (default GET), data (object: form-encoded; string: sent as is),
 *       dataType ('json' parses the response; anything else returns text), success, error
 * Callbacks: success(data, textStatus, xhr), error(xhr, textStatus, errorThrown).
 * Returns an object with chainable done(fn)/fail(fn), which receive the same arguments.
 */
function qonAjax(opts) {
	var method = (opts.type || opts.method || 'GET').toUpperCase();
	var url = opts.url;
	var body = null;
	var data = opts.data;
	if(data!=null && typeof data !== 'string') {
		data = Object.keys(data).map(function(k) { return encodeURIComponent(k) + '=' + encodeURIComponent(data[k]==null ? '' : data[k]); })
			.join('&');
	}
	if(data!=null && data!=='') {
		if(method=='GET' || method=='HEAD') {
			url += (url.indexOf('?')>=0 ? '&' : '?') + data;
		}
		else {
			body = data;
		}
	}

	var doneFns = [], failFns = [], result = null;
	if(opts.success) { doneFns.push(opts.success); }
	if(opts.error) { failFns.push(opts.error); }

	function settle(ok, args) {
		result = { ok: ok, args: args };
		var fns = ok ? doneFns : failFns;
		for(var i=0;i<fns.length;i++) { fns[i].apply(null, args); }
	}

	var xhr = new XMLHttpRequest();
	xhr.open(method, url, true);
	if(body!=null) {
		xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
	}
	xhr.onload = function() {
		if((xhr.status >= 200 && xhr.status < 300) || xhr.status == 304) {
			var resp = xhr.responseText;
			if(opts.dataType == 'json') {
				try {
					resp = resp ? JSON.parse(resp) : null;
				}
				catch(e) {
					settle(false, [xhr, 'parsererror', e]);
					return;
				}
			}
			settle(true, [resp, 'success', xhr]);
		}
		else {
			settle(false, [xhr, 'error', xhr.statusText]);
		}
	};
	xhr.onerror = function() { settle(false, [xhr, 'error', '']); };
	xhr.onabort = function() { settle(false, [xhr, 'abort', '']); };
	xhr.send(body);

	var req = {
		done: function(fn) {
			if(result) { if(result.ok) { fn.apply(null, result.args); } }
			else { doneFns.push(fn); }
			return req;
		},
		fail: function(fn) {
			if(result) { if(!result.ok) { fn.apply(null, result.args); } }
			else { failFns.push(fn); }
			return req;
		}
	};
	return req;
}
