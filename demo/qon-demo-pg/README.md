
qon-demo-pg
============

A minimal QueryOn demo using a [PostgreSQL](https://www.postgresql.org/) database, connecting with
standard [environment variables](https://www.postgresql.org/docs/current/libpq-envars.html).


building
-------

* build/install queryon:

`mvn -f ../../pom.xml install -DskipTests`

**OR**

* build just qon-demo-pg:

`mvn package`


running in jetty
-------

* PostgreSQL connection environment variables must be set to run **qon-demo-pg**:

`PGHOST=<hostname> PGUSER=<username> PGPASSWORD=<password> PGDATABASE=<database> PGPORT=<port (default 5432)> QON_SCHEMAS="<schema names (comma separated, defaults to 'public, queryon')>" mvn jetty:run`

Example: `PGHOST=localhost PGUSER=postgres PGPASSWORD=s3cr3t PGDATABASE=classicmodels QON_SCHEMAS="public, queryon, classicmodels, test" mvn jetty:run`

* navigate to <http://localhost:8888>


building and running with docker & wildfly
------

* build image using WildFly (after building with maven)

`docker build --tag=qon-pg .`

* run container

```
docker run -p 8080:8080 -it \
	-e PGHOST=<hostname> \
	-e PGUSER=<username> \
	-e PGPASSWORD=<password> \
	-e PGDATABASE=<database> \
	-e PGPORT=<port (default 5432)> \
	-e QON_SCHEMAS="<schema names (comma separated)>" \
	qon-pg
```

* open in browser: <http://localhost:8080/qon-demo-pg/>
