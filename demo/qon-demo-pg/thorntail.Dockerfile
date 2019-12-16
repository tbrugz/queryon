# see: https://github.com/thorntail/thorntail-examples/blob/master/docker/docker-jaxrs-hollow/Dockerfile

FROM openjdk:jre-alpine

ADD target/qon-demo-pg-hollow-thorntail.jar /opt/hollow-thorntail.jar
ADD target/qon-demo-pg.war /opt/application.war
ADD src/main/resources/project-defaults.yml /opt/project-defaults.yml

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/hollow-thorntail.jar", "-Djava.net.preferIPv4Stack=true", "/opt/application.war", "-s", "/opt/project-defaults.yml"]
