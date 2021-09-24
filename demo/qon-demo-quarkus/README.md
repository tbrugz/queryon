
# qon-demo-quarkus

QueryOn demo using [Quarkus](https://quarkus.io), based on `qon-demo-anydb`


## Building / Packaging / Running

* Running in dev mode (using H2):

`QON_JDBC_URL="jdbc:h2:~/.queryon/demo-anydb.h2;SCHEMA_SEARCH_PATH=QUERYON,PUBLIC;DB_CLOSE_ON_EXIT=true" QON_SCHEMAS=PUBLIC mvn compile quarkus:dev`

* Browse:

http://localhost:8080/

* Packaging & running in prod mode:

Packaging: `mvn package`

Running: `java -jar target/quarkus-app/quarkus-run.jar`


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