
# qon-demo-quarkus

QueryOn demo using [Quarkus](https://quarkus.io), based on `qon-demo-anydb`


## Building / Packaging / Running

* Running in dev mode (using H2):

`QUARKUS_DATASOURCE_JDBC_URL="jdbc:h2:~/.queryon/demo-anydb.h2;SCHEMA_SEARCH_PATH=QUERYON,PUBLIC;DB_CLOSE_ON_EXIT=true" QON_SCHEMAS=PUBLIC QUARKUS_DATASOURCE_JDBC_DRIVER=org.h2.Driver QUARKUS_DATASOURCE_DB_KIND=h2 mvn quarkus:dev`

Database **kinds** (env `QUARKUS_DATASOURCE_DB_KIND`, translated to property `quarkus.datasource.db-kind`) and **driver** (env `QUARKUS_DATASOURCE_JDBC_DRIVER`) can be found in [Quarkus Datasources Guide](https://quarkus.io/guides/datasource#default-datasource)

(**Note**: Quarkus performs optimizations & checks at build time, and using QueryOn in dev mode has some issues - including classloading/JAXB, ...)


* Packaging & running in prod mode:

Packaging: `QUARKUS_DATASOURCE_JDBC_DRIVER=<driver> QUARKUS_DATASOURCE_DB_KIND=<db-kind> mvn package`

Running: `[<ENVs>] java -jar target/quarkus-app/quarkus-run.jar`

Example Packaging (using H2): `QUARKUS_DATASOURCE_JDBC_DRIVER=org.h2.Driver QUARKUS_DATASOURCE_DB_KIND=h2 mvn package`

Example Running (using H2): `QUARKUS_DATASOURCE_JDBC_URL="jdbc:h2:~/.queryon/demo-anydb.h2;SCHEMA_SEARCH_PATH=QUERYON,PUBLIC;DB_CLOSE_ON_EXIT=true" QON_SCHEMAS=PUBLIC QON_DIFF_APPLY=true java -jar target/quarkus-app/quarkus-run.jar`


* Browse:

http://localhost:8080/


## Other commands

`mvn quarkus:dependency-tree`

`mvn quarkus:info`

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


## Links

Dev:
https://quarkus.io/guides/maven-tooling

Releases:
https://github.com/quarkusio/quarkus/releases

Migration Guides:
https://github.com/quarkusio/quarkus/wiki/Migration-Guides
