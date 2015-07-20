
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

```
mvn install:install-file -DgroupId=org.bitbucket.tbrugz -DartifactId=queryon -Dversion=0.6.0 -Dfile=target/queryon-0.6.0.war -Dpackaging=war -DgeneratePom=true -DlocalRepositoryPath=D:/proj/mvn-repo -DcreateChecksum=true

mvn install:install-file -DgroupId=org.bitbucket.tbrugz -DartifactId=queryon -Dversion=0.6.0 -Dfile=target/queryon-0.6.0-sources.jar -Dpackaging=jar -Dclassifier=sources -DlocalRepositoryPath=D:/proj/mvn-repo -DcreateChecksum=true
```
