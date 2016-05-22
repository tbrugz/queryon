
demo DBn
========

A QueryOn demo using four models with [H2 database](http://www.h2database.com/),
[PostgreSQL](http://www.postgresql.org/),
[MySQL](https://www.mysql.com/)/[Mariadb](https://mariadb.org/) &
[Derby](https://db.apache.org/derby/)
databases.


building
-------

* install queryon:
```
cd ..
mvn install -DskipTests
```

* build demo-DBn:
```
cd demo-dbn
mvn package
```

* download sample database

```
ant get-data
ant unzip-data
```

* init h2 & derby databases:
```
ant start-db-h2
ant start-db-derby
```

* init mysql (mariadb) & postgresql

credentials: copy `src/main/resources/credentials.template.properties` to
`credentials.properties` & edit.

postgresql: create database `classicmodels`

* create & load databases
```
ant run-sqlrun-mysql
ant run-sqlrun-pgsql
ant run-sqlrun-h2
ant run-sqlrun-derby
```

**Note**: to run on less databases, edit the `queryon.models` property on the
`src/main/resources/queryon.properties` file.


running
-------

```
mvn jetty:run
```

navigate to `http://localhost:8888`

login with `root/root` - see `src/main/resources/iniRealmRoles.ini`


usage examples
--------------

### map example

1. login with root/root (for more info about authc/authz, see [iniRealmRoles.ini](src/main/resources/iniRealmRoles.ini))
1. create a new (map) query:
```
select code as geom_id, count(*) as count
from countries c
inner join offices o on c.name = o.country
group by code
```
1. Go to `menu > map` ; click on `load` (map) then `load data`


data
----

This demo uses the sample database Classic Models, a retailer of scale models of classic cars.
More info avaiable at:
<http://www.eclipse.org/birt/documentation/sample-database.php>.


map
---

world (geojson) map from:
[johan/world.geo.json](https://github.com/johan/world.geo.json), [unlicensed](https://github.com/johan/world.geo.json/blob/master/UNLICENSE)
(without Antarctica)
