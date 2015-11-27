
Queries Features
================

Queries may have special columns which makes them behave differently under *QueryOn*.
Columns with a special *suffix* will alter the behavior or appearance of the same column without the suffix.
Queries may also have special constructs inside SQL comments to alter their behavior.

QueryOn's editor also allows for special commands that are useful to query datamase's metadata.


Special columns
----------------

**htmlx** dump syntax (permalinks ending with `.htmlx`):

* suffixes: `_STYLE`, `_CLASS`, `_TITLE`, `_HREF` - alters the style, class, title or link (href) of the main column
* columns: `ROW_STYLE`, `ROW_CLASS` - alters the style & class of the whole row

**table.js**:

* blob columns have download link
* `_FILEEXT` suffix: blob download link uses this file extension


Special query constructs inside SQL comments (`/* ... */`)
----------------

* `allow-encapsulation=<true|false>`
* `limit-max=<numeric>`


Query commands
----------------

May only be used inside query editor, not on saved queries. Does not bind parameters. Can't be validated.
See [DatabaseMetaData's javadoc](http://docs.oracle.com/javase/7/docs/api/java/sql/DatabaseMetaData.html).

* `$columns [ [[<schema pattern>.]<table>][.<column>] ]`
* `$exportedkeys [ <schema pattern>[.<table>] ]`
* `$importedkeys [ <schema pattern>[.<table>] ]`
* `$metadata <method>`
* `$schemas [ <schema pattern> ]`
* `$tables [ <schema pattern>[.<table>] ]`
