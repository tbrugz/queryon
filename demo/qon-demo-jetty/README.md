
demo-jetty
============

A minimal QueryOn demo using [Jetty](https://jetty.org/) and [H2 database](http://www.h2database.com/)

Jetty versions:

https://jetty.org/download.html


building
-------

```sh
mvn package
```


running
-------

```sh
mvn jetty:run
```

navigate to <http://localhost:8080>


docker - building / running
------

## jetty 10

* uses servlet 4.0 / Jakarta EE8 / javax

`docker run --rm -it -p 8080:8080 -v ./target/qon-demo-jetty.war:/var/lib/jetty/webapps/root.war jetty:10`


## jetty 11

* Jakarta EE9, JakartaEE Namespace, Java11+ (**not working yet**, needs `jakarta` classifier for queryon)

`docker run --rm -it -p 8080:8080 -v ./target/qon-demo-jetty.war:/var/lib/jetty/webapps/root.war jetty:11`


## jetty 12

* Jakarta EE8 thru EE10, JakartaEE Namespace, JavaEE Namespace, Java17+

```sh
docker build --tag qon-demo-jetty .

docker run --rm -it -p 8080:8080 qon-demo-jetty
```


references
---------

https://hub.docker.com/_/jetty

https://github.com/jetty/jetty.docker

https://jetty.org/docs/jetty/12.1/programming-guide/maven-jetty/jetty-maven-plugin.html
