
building queryon
================

Dependencies: java 1.6+ ; maven 3+


Building
----

`mvn install`

Important: QueryOn usually depends on the latest version of [sqldump](https://github.com/tbrugz/sqldump),
so it will be pulled from [sonatype snapshot repository](https://oss.sonatype.org/content/repositories/snapshots/) - or
you may build it first (and publish it to your local maven repo)

Deploy (local/sonatype), see: [doc/mvn-deploy.md](doc/mvn-deploy.md)


Testing
-----

`mvn clean package && mvn -P hardness test`

see also: `demo/qon-demo-dbn/TESTING.md`, `demo/qon-demo-minimal/TESTING.md`


Upgrading version
-----

`mvn versions:set -DnewVersion=<new-version>` - ex: `mvn versions:set -DnewVersion=0.8-SNAPSHOT`


maven profiles
-----

* `release` - release profile (sign artifacts)

* `hardness` - extra tests - currently this profile must be called after a initial build (eg: `mvn clean package && mvn -P hardness test && mvn clean` )

* `thorntail` - package thorntail demos



other useful maven goals
-----

* `mvn enforcer:display-info`

* `mvn buildplan:list-phase`

* `mvn versions:display-plugin-updates -DallowAnyUpdates=false -DallowMajorUpdates=false`

* `mvn versions:display-dependency-updates -DallowAnyUpdates=false -DallowMajorUpdates=false`

* `mvn javadoc:test-javadoc`

* `mvn org.owasp:dependency-check-maven:check` / `mvn org.owasp:dependency-check-maven:aggregate`

* `mvn se.kth.castor:depclean-maven-plugin:1.1.0:depclean -Dcreate.pom.debloated=true -Dcreate.result.json=true -Dignore.scopes=test`
  (see <https://castor-software.github.io/depclean/>)

see also: <https://github.com/rfichtner/maven-survival-guide>


maven goals that need external services
-----

* `mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.8.0.2131:sonar -Dsonar.projectKey=tbrugz_queryon -Dsonar.organization=tbrugz -Dsonar.host.url=https://sonarcloud.io -Dsonar.login=<TOKEN>`
  (see <https://sonarcloud.io/documentation/analysis/scan/sonarscanner-for-maven/>)(needs node.js v10+)

