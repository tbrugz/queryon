
FROM jetty:12.1-jdk25-alpine
#FROM jetty:12.1.11-jdk25-alpine

#${JETTY_BASE} is /var/lib/jetty/

RUN mkdir -p ${JETTY_BASE}/environments
ADD --chown=jetty:jetty target/qon-demo-jetty.war ${JETTY_BASE}/webapps/ROOT.war

# https://github.com/jetty/jetty.docker/issues/172#issuecomment-2003150702
RUN echo "--modules=ee8-deploy" > ${JETTY_BASE}/start.d/ee8-deploy.ini

ADD --chown=jetty:jetty jetty/resources/jetty-logging.properties ${JETTY_BASE}/resources/jetty-logging.properties
