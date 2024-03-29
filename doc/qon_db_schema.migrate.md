
2022-08 - qon_tables: add sql_filter
-------------------

```sql
alter table qon_tables
	add sql_filter varchar(1000);
```


2022-02 - qon_queries & qon_tables: add default_column_names
-------------------

```sql
alter table qon_queries
	add default_column_names varchar(1000);

alter table qon_tables
	add default_column_names varchar(1000);
```


2017-09 - qon_queries/pages: add version_seq
-------------------

```sql
alter table qon_queries
	add column version_seq integer;
	
alter table qon_pages
	add column version_seq integer;
```


2017-01 - qon_queries & qon_pages
---------------------------------

```sql
alter table qon_queries
	add column created_at timestamp,
	add column created_by varchar(200),
	add column updated_at timestamp,
	add column updated_by varchar(200);

alter table qon_pages
	add column created_at timestamp,
	add column created_by varchar(200),
	add column updated_at timestamp,
	add column updated_by varchar(200);
```


2016-05 - qon_execs
-------------------

```sql
alter table qon_execs
	alter column parameter_count null;
```


2016-05 - qon_pages: add binary_data, has_body
-----------------------

```sql
alter table qon_pages
	add column binary_data blob;

alter table qon_pages
	add column has_body char(1);
```


2016-04 - add qon_pages
-----------------------

```sql
create table qon_pages (
	id integer, -- serial, identity, auto_increment ...
	path varchar(400),
	remarks varchar(400),
	mime varchar(100),
	body clob,
	roles_filter varchar(1000),
	constraint qon_pages_pk primary key (id),
	constraint qon_pages_path_uk unique (path)
)
```


2016-02 - disabled column
-------------------------

```sql
alter table qon_queries
	add column disabled tinyint
	
alter table qon_tables
	add column disabled tinyint

alter table qon_execs
	add column disabled tinyint
```


2016-01 - qon_tables: add column_remarks ; add qon_execs
--------------------------------------------------------

```sql
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
	parameter_count integer not null,
	parameter_names varchar(1000),
	parameter_types varchar(1000),
	parameter_inouts varchar(1000),
	constraint qon_execs_pk primary key (name)
)
```


2015-07 - rename column schema
------------------------------

```sql
alter table qon_queries
	rename column schema to schema_name

alter table qon_tables
	rename column schema to schema_name
```


2015-04 - add roles_filter
------------------------

```sql
alter table qon_queries
	add roles_filter varchar(1000)
```
