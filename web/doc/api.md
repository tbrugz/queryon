
API / permalink parameters
==========================

The permalink to a query behaves mostly like a [REST](https://en.wikipedia.org/wiki/Representational_state_transfer)-like API.
The base [URL](https://en.wikipedia.org/wiki/Uniform_Resource_Locator) is as:

	http[s]://<host>/<queryon-context>/q/[<schema>.]<object>[/<query-parameters>].<syntax>[?<parameters>]

Where

* `<query-parameters>` are required parameters that a given query may have
* `<syntax>` is the syntax of the return. Common values are `csv`, `html`, `htmlx`, `json`, `xml`, `ffc` & `sql`

**htmlx** is a syntax in which some columns have a special meaning.
For more information see [Queries Features / Special columns](queries.md)

Parameters
----------

There are several parameters that may be used when crafting a query URL.
Parameter syntax follows the [query string](https://en.wikipedia.org/wiki/Query_string) syntax: `<parameter-name>=<parameter-value>`.

Parameter names are as follows:

* `fields` - names of the columns to be returned by the query (*all* when not informed)
* `distinct` - when `true`, returns only distinct rows
* `order` - names of the columns that will be used to order the query (a `-` before the column name means it will be in decreasing order)
* `limit` - maximum number of rows to be returned (integer parameter)
* `offset` - number of rows to be skipped (not returned) before the first row is returned

There are also a number of filters that may be applyed to a query:

* `feq:<column>` - `<column>` must be **equal** to the informed value
* `fne:<column>` - `<column>` must be **not equal** to the informed value
* `fgt:<column>` - `<column>` must be **greater** than the informed value
* `fge:<column>` - `<column>` must be **greater or equal** than the informed value
* `flt:<column>` - `<column>` must be **less** than the informed value
* `fle:<column>` - `<column>` must be **less or equal** than the informed value
* `fin:<column>` - `<column>`'s value must be **in** the informed value
* `fnin:<column>` - `<column>`'s value must **not** be **in** the informed value
* `flk:<column>` - `<column>`'s value must be **like** the informed value
* `fnlk:<column>` - `<column>`'s value must be **not like** the informed value
* `fnull:<column>` - `<column>` must be **NULL**
* `fnotnull:<column>` - `<column>` must **not** be **NULL**

There are also some parameters that may be used to return a unique value from a query (e.g.: a unique column from the first row returned).
Useful for Blob types.

* `valuefield` - the column to be returned (only required field for "Blob-dump")
* `mimetype` - the mimetype to be returned
* `mimefield` - the column name that represents the mimetype of the value
* `filename` - the filename to be used in the return
* `filenamefield` - the column that contains the filename of the value

Syntax-based parameters
-----------------------

Some parameters are specific to a syntax. Below you can find more about them:

### CSV

* `header` - returns (or not) the header row (default is `true`)
* `delimiter` - delimiter character between fields (columns) - default is `,`

### FFC

* `rgs` - *row group size*: set the number of rows to be grouped (default is `20`)

### HTML(X)

* `title` - returns (or not) the table title/name (default is `false`)
* `escape` - escapes (or not) the column's XML content (default is `true`)

### JSON

* `callback` - padding to be used for [JSONP](https://en.wikipedia.org/wiki/JSONP) (*optional*)

### XML

* `escape` - escapes (or not) the column's XML content (default is `true`)

More info about syntax-based parameters may be found at
[syntaxinfo.properties](https://bitbucket.org/tbrugz/queryon/src/default/src_resources/tbrugz/queryon/syntaxes/syntaxinfo.properties)


More information
----------------

Details about the implementation of the parameters may be found on the
[RequestSpec](https://bitbucket.org/tbrugz/queryon/src/default/src/tbrugz/queryon/RequestSpec.java)
class
