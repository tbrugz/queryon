drop table if exists EMP;

drop table if exists DEPT;

create table DEPT ( -- type=TABLE
	ID INTEGER not null,
	NAME VARCHAR(100),
	PARENT_ID INTEGER,
	constraint DEPT_PK primary key (ID)
);

create table EMP ( -- type=TABLE
	ID INTEGER not null,
	NAME VARCHAR(100) not null,
	SUPERVISOR_ID INTEGER,
	DEPARTMENT_ID INTEGER,
	SALARY INTEGER,
	constraint EMP_PK primary key (ID)
);

drop table if exists PAIR;

create table PAIR ( -- type=TABLE
	ID1 INTEGER not null,
	ID2 INTEGER not null,
	REMARKS VARCHAR,
	constraint PAIR_PK primary key (ID1, ID2)
);

drop table if exists TASK;

create table TASK (
	ID INTEGER primary key auto_increment,
	SUBJECT varchar not null,
	DESCRIPTION text,
	ATTACH blob
);

--alter table EMP drop constraint EMP_DEPT_FK;
alter table EMP
	add constraint EMP_DEPT_FK foreign key (DEPARTMENT_ID)
	references DEPT (ID);

--alter table EMP drop constraint EMP_EMP_FK;
alter table EMP
	add constraint EMP_EMP_FK foreign key (SUPERVISOR_ID)
	references EMP (ID);

