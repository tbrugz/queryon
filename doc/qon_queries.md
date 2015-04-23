
sql ansi
--------
create table qon_queries (
	schema varchar(100),
	name varchar(100) not null,
	remarks varchar(400),
	query varchar(4000),
	roles_filter varchar(1000),
	constraint qon_queries_pk primary key (name)
)


ansi with clob
--------------
create table qon_queries (
	schema varchar(100),
	name varchar(100) not null,
	remarks varchar(400),
	query clob,
	roles_filter varchar(1000),
	constraint qon_queries_pk primary key (name)
	--constraint qon_queries_pk primary key (schema,name)
)


oracle
------
create table qon_queries (
	schema varchar2(100),
	name varchar2(100) not null,
	remarks varchar2(400),
	query clob,
	roles_filter varchar2(1000),
	constraint qon_queries_pk primary key (name)
)
