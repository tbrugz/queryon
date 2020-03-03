
qon-demo-static
===============

A QueryOn using no plugins and no "rich" web interface (qon-web). Uses [H2 database](http://www.h2database.com/).


building
-------

* install queryon:

`mvn -f ../../pom.xml install -DskipTests`

* build qon-demo-static:

`mvn package`


running with jetty (java 8+)
-------

* `mvn jetty:run`

* navigate to <http://localhost:8888>


building and running docker image
------

* build image using WildFly (after building qon-demo-static)

`docker build --tag=qon-static .`

* run container

`docker run -p 8080:8080 -it qon-static`

OR (mapping h2 database from host as a volume)

```
mkdir -p $HOME/.queryon/qon-demo-static
docker run -p 8080:8080 -it \
	-v "$HOME/.queryon/qon-demo-static":/opt/jboss/.queryon/qon-demo-static \
	qon-static
```

* open in browser: <http://localhost:8080/qon-demo-static/>
