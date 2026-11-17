// QueryOn's SQL mode: upstream "ace/mode/sql" plus QueryOn-specific keywords, constants, functions and types.
// build.mjs prepends upstream mode-sql.js and writes it to the vendor ace dir, so ace loads it as "ace/mode/qon_sql"
// (file name convention: mode-<name>.js)
ace.define("ace/mode/qon_sql_highlight_rules", ["require", "exports", "module", "ace/lib/oop", "ace/mode/sql_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var SqlHighlightRules = require("./sql_highlight_rules").SqlHighlightRules;

var words = function(list) { return "\\b(?:" + list + ")\\b"; };

var extraKeywords = "aggregate|alias|over|recursive|within|replace|procedure|package|body|begin|return";

//see: tbrugz.queryon.SQL class and package tbrugz.queryon.sqlcmd
var extraConstants = "\\$(?:where_clause|filter_clause|projection_clause|order_clause|username"
    + "|schemas|tables|columns|exportedkeys|importedkeys|metadata)\\b";

var extraFunctions = "cast|row_number|upper|lower|lead|lag|first_value|last_value|extract|dense_rank|to_char|to_date|to_number";

var extraTypes = "clob|blob";

var QonSqlHighlightRules = function() {
    SqlHighlightRules.call(this);

    var rules = [
        { token: "constant.language", regex: extraConstants, caseInsensitive: true },
        { token: "keyword", regex: words(extraKeywords), caseInsensitive: true },
        { token: "support.function", regex: words(extraFunctions), caseInsensitive: true },
        { token: "storage.type", regex: words(extraTypes), caseInsensitive: true }
    ];
    this.$rules.start = rules.concat(this.$rules.start);
    this.normalizeRules();
};

oop.inherits(QonSqlHighlightRules, SqlHighlightRules);

exports.QonSqlHighlightRules = QonSqlHighlightRules;
});

ace.define("ace/mode/qon_sql", ["require", "exports", "module", "ace/lib/oop", "ace/mode/sql", "ace/mode/qon_sql_highlight_rules"], function(require, exports, module) {
"use strict";

var oop = require("../lib/oop");
var SqlMode = require("./sql").Mode;
var QonSqlHighlightRules = require("./qon_sql_highlight_rules").QonSqlHighlightRules;

var Mode = function() {
    SqlMode.call(this);
    this.HighlightRules = QonSqlHighlightRules;
};
oop.inherits(Mode, SqlMode);

(function() {
    this.$id = "ace/mode/qon_sql";
}).call(Mode.prototype);

exports.Mode = Mode;
});
