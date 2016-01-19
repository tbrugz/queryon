
2016-01 - qon_tables: add column_remarks
----------------------------------------
alter table qon_tables
	add column column_remarks varchar(4000)
	
create table qon_execs (
	schema_name varchar(100),
	name varchar(100) not null,
	remarks varchar(400),
	roles_filter varchar(1000),
	exec_type varchar(100),
	package_name varchar(100),
	body clob,
	parameter_count integer,
	parameter_names varchar(1000),
	parameter_types varchar(1000),
	parameter_inouts varchar(1000),
	constraint qon_execs_pk primary key (name)
)


2015-07 - rename column schema
------------------------------
alter table qon_queries
	rename column schema to schema_name

alter table qon_tables
	rename column schema to schema_name


2015-04 - add roles_filter
------------------------
alter table qon_queries
	add roles_filter varchar(1000)
