drop table if exists DEPT;

create table DEPT ( -- type=TABLE
	ID INTEGER(10) not null,
	NAME VARCHAR(100),
	PARENT_ID INTEGER(10),
	constraint DEPT_PK primary key (ID)
);

drop table if exists EMP;

create table EMP ( -- type=TABLE
	ID INTEGER(10) not null,
	NAME VARCHAR(100) not null,
	SUPERVISOR_ID INTEGER(10),
	DEPARTMENT_ID INTEGER(10),
	SALARY INTEGER(10),
	constraint EMP_PK primary key (ID)
);

--alter table EMP drop constraint EMP_DEPT_FK;
alter table EMP
	add constraint EMP_DEPT_FK foreign key (DEPARTMENT_ID)
	references DEPT (ID);

--alter table EMP drop constraint EMP_EMP_FK;
alter table EMP
	add constraint EMP_EMP_FK foreign key (SUPERVISOR_ID)
	references EMP (ID);

