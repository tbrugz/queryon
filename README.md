
queryon
=======

A REST-like API that follows the naked objects pattern. More like a 'naked database objects' API.
Depends on [sqldump](https://bitbucket.org/tbrugz/sqldump)

Database objects are defined by a properties file. See 
[queryon.template.properties](https://bitbucket.org/tbrugz/queryon/src/tip/src/queryon.template.properties)
for more info.

More info about QueryOn:

* the [API spec](web/doc/api.md)
* [Query features](web/doc/queries.md)
* "the [index](web/doc/index.md)" - in-app help index

-- [Telmo Brugnara](mailto:tbrugz@gmail.com)


building
--------

Dependencies: java 1.6+ ; maven 2+

Building:

	`mvn install`

Important: QueryOn usually depends on the latest version of [sqldump](https://bitbucket.org/tbrugz/sqldump),
so you may need to build it first - and publish it to your local maven repo


license
-------

[AGPLv3](http://www.gnu.org/licenses/agpl-3.0.html) - except where otherwise noted


license - notable exceptions
----------------------------
* `/web/css/font-awesome/*` - [SIL OFL 1.1](http://scripts.sil.org/OFL) - <https://fortawesome.github.io/Font-Awesome/>
* `/web/map/js/jscolor/*` - GNU LGPL - <http://jscolor.com/>, <http://www.gnu.org/copyleft/lesser.html>
* `/web/js/ace/*` - BSD license - <https://github.com/ajaxorg/ace/blob/master/LICENSE>
* `/web/js/d3*` - BSD license - <https://github.com/mbostock/d3/blob/master/LICENSE>
* `/web/js/jquery-*` - MIT License - <https://jquery.org/license/>
* `/web/js/jquery.key.js` - MIT License - <https://github.com/OscarGodson/jKey>
* `/web/[js|css]/jsdifflib/*` - BSD license - <https://github.com/cemerick/jsdifflib#license>
* `/web/js/markdown.js` - MIT License - <https://github.com/evilstreak/markdown-js#license>
* `/web/[js|css]/nv.d3*` - Apache License 2.0 - <https://github.com/novus/nvd3/blob/master/LICENSE.md>
* `/web/[js|css]/prism.[js|css]` - MIT License - <https://github.com/LeaVerou/prism/blob/gh-pages/LICENSE>
* `/web/js/sigma/*` - MIT License - <https://github.com/jacomyal/sigma.js/blob/master/LICENSE.txt>
* `/web/js/typeahead.bundle.js`- MIT License - <https://github.com/twitter/typeahead.js/blob/master/LICENSE>
