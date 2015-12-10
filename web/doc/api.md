
API / permalink parameters
==========================

The permalink to a query behaves mostly like a [REST](https://en.wikipedia.org/wiki/Representational_state_transfer)-like API.
The base [URL](https://en.wikipedia.org/wiki/Uniform_Resource_Locator) is as:

	http[s]://<host>/<queryon-context>/q/[<schema>.]<object>[/<query-parameters>].<syntax>[?<parameters>]

Where

* `<query-parameters>` are required parameters that a given query may have
* `<syntax>` is the syntax of the return. Common values are `csv`, `html`, `htmlx`, `json`, `xml`, `ffc` & `sql`

Parameters
----------

There are several parameters that may be used when crafting a query URL, as follows:

* `fields` - names of the columns to be returned by the query (*all* when not informed)
* `distinct` - when `true`, returns only distinct rows
* `order` - names of the columns that will be used to order the query (a `-` before the column name means it will be in decreasing order)
* `limit` - maximum number of rows to be returned (integer parameter)
* `offset` - number of rows to be skipped (not returned) before the first row is returned

There are also a number of filters that may be applyed to a query:

* `feq:<column>` - value must be **equal** to `<column>`
* `fne:<column>` - value must be **not equal** to `<column>`
* `fgt:<column>` - value must be **greater** than `<column>`
* `fge:<column>` - value must be **greater or equal** than `<column>`
* `flt:<column>` - value must be **less** than `<column>`
* `fle:<column>` - value must be **less or equal** than `<column>`
* `fin:<column>` - `<column>`'s value must be **in** the informed value
* `fnin:<column>` - `<column>`'s value must **not** be **in** the informed value
* `flk:<column>` - `<column>`'s value must be **like** the informed value
* `fnlk:<column>` - `<column>`'s value must be **not like** the informed value

There are also some parameters that may be used to return a unique value from a query (e.g.: a unique column from the first row returned).
Useful for Blob types.

* `valuefield` - the column to be returned
* `mimetype` - the mimetype to be returned (optional)
* `mimefield` - the column name that represents the mimetype of the value
* `filename` - the filename to be used in the return
* `filenamefield` - the column that contains the filename of the value

Details about the implementation of the parameters may be found on the
[RequestSpec](https://bitbucket.org/tbrugz/queryon/src/default/src/tbrugz/queryon/RequestSpec.java)
class
