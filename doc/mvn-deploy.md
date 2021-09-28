
deploy maven artifacts to local dir
-----------------------------------

* https://maven.apache.org/plugins/maven-install-plugin/examples/specific-local-repo.html

```
mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file  -Dfile=path-to-your-artifact-jar \
                                                                              -DgroupId=your.groupId \
                                                                              -DartifactId=your-artifactId \
                                                                              -Dversion=version \
                                                                              -Dpackaging=jar \
                                                                              -DlocalRepositoryPath=path-to-specific-local-repo
```

* http://stackoverflow.com/a/22715898/616413


**fast install:**

```
export MVN_ARTIFACT_VERSION=0.6.2-SNAPSHOT

mvn install:install-file -DgroupId=org.bitbucket.tbrugz -DartifactId=queryon -Dfile=target/queryon-$MVN_ARTIFACT_VERSION.war -Dpackaging=war -DpomFile=pom.xml -DlocalRepositoryPath=$HOME/Desktop/proj/mvn-repo -DcreateChecksum=true

mvn install:install-file -DgroupId=org.bitbucket.tbrugz -DartifactId=queryon -Dfile=target/queryon-$MVN_ARTIFACT_VERSION-sources.jar -Dpackaging=jar -DpomFile=pom.xml -Dclassifier=sources -DlocalRepositoryPath=$HOME/Desktop/proj/mvn-repo -DcreateChecksum=true
```

deploy to sonatype
---------

http://central.sonatype.org/pages/apache-maven.html

change `settings.xml`:
```
<server>
    <id>ossrh</id>
    <username>your-jira-id</username>
    <password>your-jira-pwd</password>
</server>
```

snapshot:  
`mvn clean deploy` - all projects  
`mvn clean deploy -pl .,qon-core,qon-auth-keycloak,qon-graphql,qon-soap,qon-web` - ignores demo projects  

artifacts at snapshot repo:
`https://oss.sonatype.org/content/repositories/snapshots/org/bitbucket/tbrugz/`

release:  
`mvn clean javadoc:jar deploy -P release` - all projects  
`mvn clean javadoc:jar deploy -P release -pl .,qon-core,qon-auth-keycloak,qon-graphql,qon-soap,qon-web` - ignores demo projects  
& go to `https://oss.sonatype.org/` & close & release staging repo

artifacts at central:  
`https://repo1.maven.org/maven2/org/bitbucket/tbrugz/`


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
