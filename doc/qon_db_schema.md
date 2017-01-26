
sql ansi
--------
create table qon_queries (
	schema_name varchar(100),
	name varchar(100) not null,
	remarks varchar(400),
	query clob, -- ansi(?), oracle
	--query text, -- pgsql, mysql
	--query varchar(4000),
	roles_filter varchar(1000),
	disabled tinyint,
	created_at timestamp,
	created_by varchar(200),
	updated_at timestamp,
	updated_by varchar(200),
	constraint qon_queries_pk primary key (name)
	--constraint qon_queries_pk primary key (schema,name)
)

create table qon_tables (
	schema_name varchar(100),
	name varchar(100) not null,
	column_names varchar(400),
	pk_column_names varchar(400),
	remarks varchar(400),
	column_remarks varchar(4000),
	roles_select varchar(1000),
	roles_insert varchar(1000),
	roles_update varchar(1000),
	roles_delete varchar(1000),
	roles_insert_columns varchar(1000),
	roles_update_columns varchar(1000),
	disabled tinyint,
	constraint qon_tables_pk primary key (name)
	--constraint qon_tables_pk primary key (schema,name)
)

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
	disabled tinyint,
	constraint qon_execs_pk primary key (name)
)

create table qon_pages (
	id integer, -- serial, identity, auto_increment ...
	path varchar(400),
	remarks varchar(400),
	mime varchar(100),
	body clob, --text ...
	binary_data blob,
	has_body char(1),
	roles_filter varchar(1000),
	created_at timestamp,
	created_by varchar(200),
	updated_at timestamp,
	updated_by varchar(200),
	constraint qon_pages_pk primary key (id),
	constraint qon_pages_path_uk unique (path)
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
	pk_column_names varchar2(400),
	remarks varchar2(400),
	column_remarks varchar2(4000),
	roles_select varchar2(1000),
	roles_insert varchar2(1000),
	roles_update varchar2(1000),
	roles_delete varchar2(1000),
	roles_insert_columns varchar2(1000),
	roles_update_columns varchar2(1000),
	constraint qon_tables_pk primary key (name)
)

create table qon_execs (
	schema_name varchar2(100),
	name varchar2(100) not null,
	remarks varchar2(400),
	roles_filter varchar2(1000),
	exec_type varchar2(100),
	package_name varchar2(100),
	body clob,
	parameter_count integer,
	parameter_names varchar2(1000),
	parameter_types varchar2(1000),
	parameter_inouts varchar2(1000),
	constraint qon_execs_pk primary key (name)
)

create table qon_pages (
	id integer,
	path varchar2(400),
	remarks varchar2(400),
	mime varchar2(100),
	body clob,
	binary_data blob,
	roles_filter varchar2(1000),
	has_body char(1),
	constraint qon_pages_pk primary key (id),
	constraint qon_pages_path_uk unique (path)
)

(see: http://stackoverflow.com/questions/11296361/how-to-create-id-with-auto-increment-on-oracle)

create sequence qon_pages_seq;

create or replace trigger qon_pages_trg_ins
before insert on qon_pages
for each row
begin
  select qon_pages_seq.nextval into :new.id from dual;
end;

create or replace trigger qon_pages_body_trg_iu
before insert or update on qon_pages
for each row
begin
  if :new.body is null then
     :new.has_body := 'f';
  else
     :new.has_body := 't';
  end if;
end;

