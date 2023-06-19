
Queries Features
================

Queries may have parameters that should be informed when executing the query.
Queries may have special columns which makes them behave differently under *QueryOn*.
Columns with a special *suffix* may alter the behavior or appearance of the same column without the suffix.
Queries may also have special constructs inside SQL comments to alter their behavior.

QueryOn's editor also allows for special commands that are useful to query database's metadata.

See also: [API documentation](api.md)


Variables
---------

Queries may have variables (constants?) that will be replaced when the query is executed.

* `$username` - replaces the token by the current logged user (with quotes, e.g.: `'root'`)


Parameters
----------

You may define query parameters by using characters for binding variables (usually `?`).
Parameters will be created by the order they are defined in the query. 

Example: In the query `select * from person where age > ? and name like ?` the age should be informed
as the first parameter and the name pattern as the second parameter. 

You may also define named parameters by using the `:<parameter-name>` syntax. <!--This is similar to the `named-parameters=<param1-name>` query construct...-->
Using both positional and named parameters in the same query is not allowed.


Special columns
----------------

**htmlx** dump syntax (permalinks ending with `.htmlx`):

* suffixes: `_STYLE`, `_CLASS`, `_TITLE`, `_HREF` - alters the style, class, title or link (href) of the main column
* columns: `ROW_STYLE`, `ROW_CLASS` - alters the style & class of the whole row

**html(x) + table.js**:

* columns of Blob type (or columns ending with `_ASBLOB`) have download link (when not null)
* `_FILEEXT` suffix: blob download link uses it's value as file extension
* **Warning**: query should be totally ordered for correct blob download (e.g.: *order by <primary key>*)

**xml**, **html** & **htmlx** dump syntax:

* suffix: `_RAW`: do not parse column contents (outputs *RAW* content)

**atom** dump syntax (permalinks ending with `.atom`):

* columns: `TITLE`, `ID`, `SUMMARY`, `CONTENT`, `AUTHOR_NAME`, `UPDATED` - information needed by the
  [Atom Feed Standard](https://en.wikipedia.org/wiki/Atom_(standard))


Special query constructs inside SQL comments (`/* ... */`)
----------------

* `allow-encapsulation=<true|false>` - allows the query to be encapsulated within another query by the QueryOn engine so
  that, for example, the query results may be paginated in the database. In some edge cases this may be a problem, so
  it can be disabled (default is *true*)
* `bind-null-on-missing-parameters=<true|false>[,<true|false>[,...]]` - binds `NULL` on required missing parameters
* `default-columns=<column-1>[,<column-2>[,...]]` - sets the default columns that will be retuned by this query (all columns are returned by default)
* `limit-default=<numeric>` - sets the default limit of the number of rows that may be returned by the query in a single
  request - may be overriden by the `limit` parameter
* `limit-max=<numeric>` - limits the number of rows that may be returned by the query in a single request
* `named-parameters=<param1-name>[,<param2-name>[,...]]` - request parameter names to be binded into the query


Special constructs
----------------

Constructs that will be replaced by API features/parameters. When they are not defined in a query,
*QueryOn* will create an encapsulating query to enable the features.

* `$filter_clause` - where the filters will be injected. See (in API docs): `feq:`, `fne:`, `fin:`...
* `$order_clause` - where the order will be applied. See API `order` parameter.
* `$projection_clause` - where the columns/fields will be selected/projected. See API `fields` parameter.
* `$where_clause` - Similar to `$filter_clause`, but assumes `WHERE` is not present in the query.


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

The edges may have special properties, as follows:

* `EDGE_COLOR` - edge color, value should be a valid [web color](https://en.wikipedia.org/wiki/Web_colors), e.g.: `#ccc`
* `EDGE_WIDTH` - edge width

Default implementation of graphs visualization uses the [Sigma.js javascript library](http://sigmajs.org/).

**map**

Query results may be visualized as a map. To that end, a Geojson map should be accessible from the QueryOn instalation and each feature should have an `id` property.
Also, the query should have a column named `GEOM_ID` so that the map feature and the query result's row may be matched. Once matched, the fill color of the feature
will change according toi the selected column value.

Default implementation of maps visualization uses the [Google Maps API](https://developers.google.com/maps/) and the [Mapproc framework](https://github.com/tbrugz/mapproc).

**chart**

Queries may be visualized as charts with any column that is numeric, so no special column names need to be used.

Default implementation of charts visualization uses the [d3.js javascript library](http://d3js.org/).
