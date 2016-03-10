
demo-minimal
============

A minimal QueryOn demo using [H2 database](http://www.h2database.com/)


building
-------

* install queryon: 
```
cd ..
mvn install -DskipTests
```

* build demo-minimal: 
```
cd demo-minimal
mvn package
```

running
-------

```
mvn jetty:run
```

navigate to `http://localhost:8888`


example queries
---------------

```
select 'Hello World!' as message
select 'Hello '||? as message
select * from INFORMATION_SCHEMA.TABLES
select * from QON_QUERIES
```

