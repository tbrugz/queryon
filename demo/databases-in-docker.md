
running databases in docker
==========

Tips for my future self


general docker container management
------

* starting an existing container: 
  `sudo docker start -i <container-name>`

* stoping a container:
  `sudo docker stop <container-name>`

* executing interactive command (bash) in container:
  `sudo docker exec -it <container-name> bash`

* list all containers:
  `sudo docker ps -a`

* remove container:
  `sudo docker rm <container-name>`


postgresql
------

* creating container for postgresql, mapping current directory (`$(pwd)`) as a volume:
  `sudo docker run --name <container-name> -v $(pwd):$(pwd) -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:12`

ref: <https://hub.docker.com/_/postgres>


mariadb
------

* creating container for mariadb:
  `sudo docker run --name <container-name> -p 3306:3306 -e MYSQL_ROOT_PASSWORD=mypass mariadb:10.5`

ref: <https://hub.docker.com/_/mariadb>
