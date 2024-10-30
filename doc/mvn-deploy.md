
deploy to sonatype
---------

* Setting up: Change `settings.xml`:

```
<server>
    <id>ossrh</id>
    <username>your-jira-id</username>
    <password>your-jira-pwd</password>
</server>
```


* Deploy snapshot:

`mvn clean deploy` - all projects  
<!--`mvn clean deploy -pl .,qon-core,qon-auth-keycloak,qon-graphql,qon-soap,qon-web` - ignores demo projects  -->

artifacts at snapshot repo:
`https://oss.sonatype.org/content/repositories/snapshots/org/bitbucket/tbrugz/`


* Deploy release version:

`mvn clean javadoc:jar source:jar deploy -P release` - all projects  
<!--`mvn clean javadoc:jar deploy -P release -pl .,qon-core,qon-auth-keycloak,qon-graphql,qon-soap,qon-web` - ignores demo projects  -->
& go to `https://oss.sonatype.org/` & close & release staging repo  
(maybe do `mvn -DskipTests=true package` first, see: <https://issues.apache.org/jira/browse/MDEP-98>)

artifacts at central:  
`https://repo1.maven.org/maven2/org/bitbucket/tbrugz/`


* Reference:

https://central.sonatype.org/publish/publish-maven/


run jetty
---------
```
mvn org.eclipse.jetty:jetty-maven-plugin:9.3.1.v20150714:help

mvn org.eclipse.jetty:jetty-maven-plugin:9.3.1.v20150714:run

... mvn org.mortbay.jetty:jetty-maven-plugin::run

```

run tomcat
----------

```
... mvn org.apache.tomcat.maven:tomcat7-maven-plugin:2.2:run
```


other links
-----------

http://stackoverflow.com/questions/18859138/how-to-use-bitbucket-as-a-maven-remote-repository
http://stackoverflow.com/questions/9359362/how-to-deploy-the-sources-file-with-the-jar-using-maven-deploydeploy-file
http://stackoverflow.com/questions/4725668/how-to-deploy-snapshot-with-sources-and-javadoc
http://books.sonatype.com/nexus-book/reference/staging-deployment.html
http://stackoverflow.com/questions/10533828/what-does-mvn-install-in-maven-exactly-do
http://stackoverflow.com/questions/5102571/how-to-install-maven-artifact-with-sources-from-command-line

hg changeset id & date:
`mvn buildnumber:hgchangeset`
