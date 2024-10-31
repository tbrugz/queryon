
queryon
=======

[![GNU Affero General Public License, v3](https://img.shields.io/github/license/tbrugz/queryon.svg?label=License&color=blue)](LICENSE.md)
[![Maven Central](https://img.shields.io/maven-central/v/org.bitbucket.tbrugz/queryon.svg?label=Maven%20Central)](https://search.maven.org/artifact/org.bitbucket.tbrugz/queryon)
[![CI](https://github.com/tbrugz/queryon/actions/workflows/maven.yml/badge.svg)](https://github.com/tbrugz/queryon/actions/workflows/maven.yml)


A REST-like API that follows the naked objects pattern. A bit like a 'naked database objects' API.
Depends on [sqldump](https://github.com/tbrugz/sqldump).

Database objects are defined by a properties file. See
[queryon.template.properties](qon-core/src/main/java/queryon.template.properties)
for more info.

[Apache Shiro](https://shiro.apache.org/) is used for handling authentication and authorization. 
Permissions rules for objects and actions are available in [doc/permissions.md](doc/permissions.md).

More info about QueryOn:

* the [API spec](qon-web/src/main/webapp/doc/api.md)
* [Query features](qon-web/src/main/webapp/doc/queries.md)
* "the [index](qon-web/src/main/webapp/doc/index.md)" - in-app help index

-- [Telmo Brugnara](mailto:tbrugz@gmail.com)


building
--------

Dependencies: java 8+ ; maven 3.2.5+

Building: `mvn install`

See also: [BUILDING.md](BUILDING.md)


artifacts
---------

* snapshots repo: https://oss.sonatype.org/content/repositories/snapshots/org/bitbucket/tbrugz/


demos - running
---------------

* **demo-minimal**: demo using H2 with default web interface ([qon-web](qon-web)) - see [demo/qon-demo-minimal/README.md](demo/qon-demo-minimal/README.md)

* **demo-dbn**: demo with 5 databases (H2, Mysql/Mariadb, Postgresql, Derby & SQLite) - see [demo/qon-demo-dbn/README.md](demo/qon-demo-dbn/README.md)

* **demo-static**: demo using H2 without web ui - see [demo/qon-demo-static/README.md](demo/qon-demo-static/README.md)

* **demo-pg**: demo using PostgreSQL with default web interface & standard [environment variables](https://www.postgresql.org/docs/current/libpq-envars.html) - see [demo/qon-demo-pg/README.md](demo/qon-demo-pg/README.md)

* **demo-anydb**: demo that can use any supported JDBC database (using default web interface) - see [demo/qon-demo-anydb/README.md](demo/qon-demo-anydb/README.md)

* **demo-springboot**: demo using [Spring Boot](https://spring.io/projects/spring-boot) - see [demo/qon-demo-springboot/README.md](demo/qon-demo-springboot/README.md)

* **demo-quarkus**: demo using [Quarkus](https://quarkus.io/) - see [demo/qon-demo-quarkus/README.md](demo/qon-demo-quarkus/README.md)

Note: *demo-minimal* & *demo-dbn* need maven ant tasks to populate database(s).
Install with `curl -o  ~/.ant/lib/maven-ant-tasks-2.1.3.jar https://repo1.maven.org/maven2/org/apache/maven/maven-ant-tasks/2.1.3/maven-ant-tasks-2.1.3.jar`


license
-------
[AGPLv3](https://www.gnu.org/licenses/agpl-3.0.en.html), see [LICENSE.md](LICENSE.md) - except where otherwise noted


license - notable exceptions
----------------------------
* `/web/css/font-awesome/*` - [SIL OFL 1.1](http://scripts.sil.org/OFL) - <http://fontawesome.io/>
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

(**/web**: `/qon-web/src/main/webapp/`)
