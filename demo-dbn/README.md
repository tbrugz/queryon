
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

* init h2 & derby databases:
```
ant run-sqlrun-h2
ant run-sqlrun-derby
```

* init mysql (mariadb) & postgresql

* create & load databases
```
ant run-sqlrun-mysql
ant run-sqlrun-pgsql
ant run-sqlrun-h2
ant run-sqlrun-derby
```

running
-------

```
mvn jetty:run
```

navigate to `http://localhost:8888`


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

