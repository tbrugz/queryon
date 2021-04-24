
qon-demo-anydb
==============

A QueryOn demo that can use any supported JDBC database. JDBC drivers for H2, PosrgreSQL, MySQL/MariaDB, Derby, SQLite & MSSQL Server already included.


building
-------

* build/install queryon:

`mvn -f ../../pom.xml install -DskipTests`

**OR**

* build just qon-demo-anydb:

`mvn package`


running with jetty (java 8+)
-------

* Environment variables must be set to run **qon-demo-anydb**:

`QON_JDBC_DRIVER=<driver-class> QON_JDBC_URL=<jdbc-url> QON_JDBC_USER=<username> QON_JDBC_PASSWORD=<password> QON_SCHEMAS="<schema names (comma separated, defaults to 'public, queryon')>" QON_JDBC_INITSQL="<some-sql>" mvn jetty:run`

Example with H2: `QON_JDBC_URL="jdbc:h2:~/.queryon/demo-anydb.h2;SCHEMA_SEARCH_PATH=QUERYON,PUBLIC;DB_CLOSE_ON_EXIT=true" QON_SCHEMAS=PUBLIC mvn jetty:run`

Example with PostgreSQL: `QON_JDBC_URL="jdbc:postgresql://localhost/database" QON_JDBC_USER=postgres QON_JDBC_PASSWORD=s3cr3t QON_SCHEMAS=public mvn jetty:run`

Example with MySQL: `QON_JDBC_URL="jdbc:mysql://localhost/classicmodels" QON_JDBC_USER=mysql QON_JDBC_PASSWORD=s3cr3t mvn jetty:run`

Example with MSSQL Server: `QON_JDBC_URL="jdbc:sqlserver://localhost;database=AdvertureWorks;encrypt=false" QON_JDBC_USER=sa QON_JDBC_PASSWORD=yourStrongPassword. mvn jetty:run -Djetty.http.port=9999`

Example with external `queryon.properties`: `QON_PROPERTIES_PATH=~/queryon.properties mvn jetty:run`  
(any queryon configuration may go into the file. See [queryon.template.properties](../../qon-core/src/main/java/queryon.template.properties). Using minimal sqlite database config below)

```
queryon.dburl=jdbc:sqlite:${user.dir}/queryon.sqlite.db
```


* navigate to <http://localhost:8080> (or <http://localhost:9999> in the MSSQL Server example)

* Other properties/env vars:
  * `QON_DIFF_APPLY=true`: apply diff between in-memory model & database to database


building and running with docker & wildfly
------

* build image using WildFly (after building with maven)

`docker build --tag=qon-anydb .`

* run container

```
docker run -p 8080:8080 -it \
	-e QON_JDBC_DRIVER=<driver-class> \
	-e QON_JDBC_URL=<jdbc-url> \
	-e QON_JDBC_USER=<username> \
	-e QON_JDBC_PASSWORD=<password> \
	-e QON_SCHEMAS="<schema names (comma separated)>" \
	qon-anydb
```

* open in browser: <http://localhost:8080/qon-demo-anydb/>


adding a jdbc driver to a war file
------

* Add jar to .war ([Ant](https://ant.apache.org/) needed):  
 `ant update-war -Ddriver=</path/to/some-driver.jar>`

_After adding the driver, you may run with docker/wildfly using the steps above_

* Example with Oracle/docker (run container after _docker build_):

Updating .war:  
`ant update-war -Ddriver=/path/to/ojdbc8-12.2.0.1.jar`

Running Docker:

```
docker run -p 8080:8080 -it \
	-e QON_JDBC_DRIVER=oracle.jdbc.driver.OracleDriver \
	-e QON_JDBC_URL="jdbc:oracle:thin:@orcl_server:1521:orcl" \
	-e QON_JDBC_USER=scott \
	-e QON_JDBC_PASSWORD=tiger \
	-e QON_SCHEMAS=SCOTT \
	qon-anydb
```
