
# qon-demo-quarkus

QueryOn demo using [Quarkus](https://quarkus.io), based on `qon-demo-anydb`


## Building / Packaging / Running

* Running in dev mode (using H2):

`QON_JDBC_URL="jdbc:h2:~/.queryon/demo-anydb.h2;SCHEMA_SEARCH_PATH=QUERYON,PUBLIC;DB_CLOSE_ON_EXIT=true" QON_SCHEMAS=PUBLIC QON_JDBC_DRIVER=org.h2.Driver QUARKUS_DATASOURCE_DBKIND=h2 mvn quarkus:dev`

Database **kinds** (env `QUARKUS_DATASOURCE_DBKIND`, translated to property `quarkus.datasource.db-kind`) can be found in [Quarkus Datasources Guide](https://quarkus.io/guides/datasource#default-datasource)


* Browse:

http://localhost:8080/

* Packaging & running in prod mode:

Packaging: `mvn package`

Running: `java -jar target/quarkus-app/quarkus-run.jar`

Running (using H2): `QON_JDBC_URL="jdbc:h2:~/.queryon/demo-anydb.h2;SCHEMA_SEARCH_PATH=QUERYON,PUBLIC;DB_CLOSE_ON_EXIT=true" QON_SCHEMAS=PUBLIC QON_JDBC_DRIVER=org.h2.Driver QUARKUS_DATASOURCE_DBKIND=h2 java -jar target/quarkus-app/quarkus-run.jar`


## Other commands

`mvn quarkus:dependency-tree`

`mvn quarkus:list-extensions`


## Bootstrapping

Original project built with

```shell
mvn io.quarkus.platform:quarkus-maven-plugin:2.2.3.Final:create \
    -DprojectGroupId=org.bitbucket.tbrugz \
    -DprojectArtifactId=qon-demo-quarkus \
    -DclassName="tbrugz.queryon.quarkusdemo.HelloDemo" \
    -Dpath="/hello"
```

ref: https://quarkus.io/guides/getting-started
