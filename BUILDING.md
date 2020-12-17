
building queryon
================

Dependencies: java 1.6+ ; maven 3+

Building:

`mvn install`

Important: QueryOn usually depends on the latest version of [sqldump](https://github.com/tbrugz/sqldump),
so it will be pulled from [sonatype snapshot repository](https://oss.sonatype.org/content/repositories/snapshots/) - or
you may build it first (and publish it to your local maven repo)

Deploy (local/sonatype), see: [doc/mvn-deploy.md](doc/mvn-deploy.md)


other useful maven goals
-----

* `mvn enforcer:display-info`

* `mvn versions:display-dependency-updates -DallowAnyUpdates=false -DallowMajorUpdates=false`

* `mvn versions:display-plugin-updates -DallowAnyUpdates=false -DallowMajorUpdates=false`

* `mvn javadoc:test-javadoc`

* `mvn org.owasp:dependency-check-maven:6.0.3:check` / `mvn org.owasp:dependency-check-maven:6.0.3:aggregate`
