
# see: https://spring.io/guides/topicals/spring-boot-docker/
# see: https://hub.docker.com/_/eclipse-temurin
FROM eclipse-temurin:17-jre
#FROM eclipse-temurin:17-jdk

RUN groupadd spring && useradd -g spring spring
USER spring:spring

ARG DEPENDENCY=target/dependency
COPY ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY ${DEPENDENCY}/META-INF /app/META-INF
COPY ${DEPENDENCY}/BOOT-INF/classes /app

ENTRYPOINT ["java","-cp","app:app/lib/*","tbrugz.queryon.springboot.demo.QOnApp"]
#ENTRYPOINT ["java","-cp","app:app/lib/*","-Djava.security.egd=file:/dev/./urandom","tbrugz.queryon.springboot.demo.QOnApp"]
