
2016-01 - qon_tables: add column_remarks
----------------------------------------
alter table qon_tables
	add column column_remarks varchar(4000)
	

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
