
qon-demo-springboot
=====

Demo using QueryOn with [Spring Boot](https://spring.io/projects/spring-boot)

Uses the same database of [qon-demo-minimal](../qon-demo-minimal)


running
-----

`mvn clean spring-boot:run`

OR (using jar)

```shell
mvn clean package
java -jar target/qon-demo-springboot.jar
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

