
qon-demo-springboot
=====

Demo using QueryOn with [Spring Boot](https://spring.io/projects/spring-boot). Uses the same environment variables as [qon-demo-anydb](../qon-demo-minimal).


running
-----

_Examples using [qon-demo-minimal](../qon-demo-minimal)_

`QON_JDBC_URL="jdbc:h2:~/.queryon/demo-minimal;SCHEMA_SEARCH_PATH=public,queryon" QON_JDBC_USER=admin QON_JDBC_PASSWORD= mvn clean spring-boot:run`

OR (using jar)

```shell
mvn clean package
QON_JDBC_URL="jdbc:h2:~/.queryon/demo-minimal;SCHEMA_SEARCH_PATH=public,queryon" QON_JDBC_USER=admin QON_JDBC_PASSWORD= java -jar target/qon-demo-springboot.jar
```

<!--
OR

```shell
mvn package spring-boot:repackage
java -jar target/qon-demo-springboot.jar
```

OR (war)

```shell
mvn clean package
java -jar target/qon-demo-springboot-0.8-SNAPSHOT.war 
```
-->

and go to <http://localhost:8080/>


building & running with docker
-----

* building:

```shell
mvn package && mkdir -p target/dependency && (cd target/dependency; jar -xf ../qon-demo-springboot.jar)
docker build --file src/main/docker/Dockerfile -t qon-demo-springboot .
```


* running:

`docker run -it --rm -p 8080:8080 qon-demo-springboot`

_Example using demo-minimal_

`docker run -it --rm -v $HOME/.queryon/:/data/ -e QON_JDBC_URL="jdbc:h2:/data/demo-minimal;SCHEMA_SEARCH_PATH=public,queryon" -e QON_JDBC_USER=admin -e QON_JDBC_PASSWORD= -p 8080:8080 qon-demo-springboot`


* ref: https://spring.io/guides/gs/spring-boot-docker/

