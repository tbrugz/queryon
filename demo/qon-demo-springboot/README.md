
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

