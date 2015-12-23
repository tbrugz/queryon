
Queries Features
================

Queries may have special columns which makes them behave differently under *QueryOn*.
Columns with a special *suffix* will alter the behavior or appearance of the same column without the suffix.
Queries may also have special constructs inside SQL comments to alter their behavior.

QueryOn's editor also allows for special commands that are useful to query database's metadata.

See also: [API documentation](api.md)

Special columns
----------------

**htmlx** dump syntax (permalinks ending with `.htmlx`):

* suffixes: `_STYLE`, `_CLASS`, `_TITLE`, `_HREF` - alters the style, class, title or link (href) of the main column
* columns: `ROW_STYLE`, `ROW_CLASS` - alters the style & class of the whole row

**html(x) + table.js**:

* blob columns have download link
* `_FILEEXT` suffix: blob download link uses it's value as file extension


Special query constructs inside SQL comments (`/* ... */`)
----------------

* `allow-encapsulation=<false|true>` - allows the query to be encapsulated within another query by the QueryOn engine so
  that, for example, the query results may be paginated in the database. In some edge cases this may be a problem, so
  it can be disabled (default is *false*)
* `limit-max=<numeric>` - limits the number of rows that may be returned by the query in a single request


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


Columns used for special visualizations
----------------

**graph**

Query results may be visualized as graphs when they contain the columns `SOURCE` & `TARGET`.
These columns should contain the source and target **id**s, so that an edge will be generated from the `SOURCE` node to the `TARGET` node.

The source and target nodes may have special properties, as follows:

* `[SOURCE|TARGET]_LABEL` - source/target label
* `[SOURCE|TARGET]_COLOR` - source/target color, value should be a valid [web color](https://en.wikipedia.org/wiki/Web_colors), e.g.: `#ccc`
* `[SOURCE|TARGET]_SIZE` - source/target size, default is `1`

**map**

Query results may be visualized as a map. To that end, a Geojson map should be accessible from the QueryOn instalation and each feature should have an `id` property.
Also, the query should have a column named `GEOM_ID` so that the map feature and the query result's row may be matched. Once matched, the fill color of the feature
will change according toi the selected column value.
