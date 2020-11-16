
running databases in docker
==========

Tips for my future self


general docker container management
------

* starting an existing container: 
  `docker start -i <container-name>`

* stoping a container:
  `docker stop <container-name>`

* executing interactive command (bash) in container:
  `docker exec -it <container-name> bash`

* list all containers:
  `docker ps -a`

* follow the logs:
  `docker logs -f <container-name>`

* remove container:
  `docker rm <container-name>`


postgresql
------

* creating container for postgresql, mapping current directory (`$(pwd)`) as a volume:
  `docker run --name <container-name> -v $(pwd):$(pwd) -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:12`

ref: <https://hub.docker.com/_/postgres>


mariadb
------

* creating container for mariadb:
  `docker run --name <container-name> -p 3306:3306 -e MYSQL_ROOT_PASSWORD=mypass mariadb:10.5`

ref: <https://hub.docker.com/_/mariadb>


mysql
-----

* creating container for mysql:
  `docker run --name <container-name> -p 3306:3306 -e MYSQL_ROOT_PASSWORD=mypass mysql:8 --local-infile=1`

ref: <https://hub.docker.com/_/mysql>


mssql server
-----

* creating container for mssql server:
  `docker run --name <container-name> -e 'ACCEPT_EULA=Y' -e 'SA_PASSWORD=yourStrongPassword' -p 1433:1433 -d mcr.microsoft.com/mssql/server:2017-latest`

* using sqlcmd to connect to mssql server:
  `docker exec -it <container-name> /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P yourStrongPassword`

ref: <https://hub.docker.com/_/microsoft-mssql-server>
