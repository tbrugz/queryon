
sql ansi
--------
create table qon_queries (
	schema varchar(100),
	name varchar(100) not null,
	remarks varchar(400),
	query clob,
	--query varchar(4000),
	roles_filter varchar(1000),
	constraint qon_queries_pk primary key (name)
	--constraint qon_queries_pk primary key (schema,name)
)

create table qon_tables (
	schema varchar(100),
	name varchar(100) not null,
	column_names varchar(400),
	remarks varchar(400),
	roles_select varchar(1000),
	roles_insert varchar(1000),
	roles_update varchar(1000),
	roles_delete varchar(1000),
	roles_insert_columns varchar(1000),
	roles_update_columns varchar(1000),
	constraint qon_tables_pk primary key (name)
	--constraint qon_tables_pk primary key (schema,name)
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

create table qon_tables (
	schema varchar2(100),
	name varchar2(100) not null,
	column_names varchar2(400),
	remarks varchar2(400),
	roles_select varchar2(1000),
	roles_insert varchar2(1000),
	roles_update varchar2(1000),
	roles_delete varchar2(1000),
	roles_insert_columns varchar2(1000),
	roles_update_columns varchar2(1000),
	constraint qon_tables_pk primary key (name)
)
