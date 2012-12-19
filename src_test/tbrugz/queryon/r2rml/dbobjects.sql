drop table if exists "Addresses";
CREATE TABLE "Addresses" (
	"ID" INT, PRIMARY KEY("ID"), 
	"city" CHAR(10), 
	"state" CHAR(2)
);

drop table if exists "PeopleSimple";
CREATE TABLE "PeopleSimple" (
	"ID" INT, PRIMARY KEY("ID"), 
	"fname" CHAR(10), 
	"addr" INT, 
	FOREIGN KEY("addr") REFERENCES "Addresses"("ID")
);

drop table if exists "Department";
CREATE TABLE "Department" (
	"ID" INT, 
	"name" CHAR(10), 
	"city" CHAR(10), 
	"manager" INT, 
	PRIMARY KEY("ID"), 
	UNIQUE ("name", "city")
);

drop table if exists "People";
CREATE TABLE "People" (
	"ID" INT, 
	"fname" CHAR(10), 
	"addr" INT, 
	"deptName" CHAR(10), 
	"deptCity" CHAR(10), 
	PRIMARY KEY("ID"), 
	FOREIGN KEY("addr") REFERENCES "Addresses"("ID"), 
	FOREIGN KEY("deptName", "deptCity") REFERENCES "Department"("name", "city") 
);

ALTER TABLE "Department" ADD FOREIGN KEY("manager") REFERENCES "People"("ID");

drop table if exists "Tweets";
CREATE TABLE "Tweets" (
	"tweeter" INT,
	"when" TIMESTAMP,
	"text" CHAR(140),
	FOREIGN KEY("tweeter") REFERENCES "People"("ID")
);

drop table if exists "Projects";
CREATE TABLE "Projects" (
	"lead" INT,
	FOREIGN KEY ("lead") REFERENCES "People"("ID"),
	"name" VARCHAR(50), 
	UNIQUE ("lead", "name"), 
	"deptName" VARCHAR(50), 
	"deptCity" VARCHAR(50),
	UNIQUE ("name", "deptName", "deptCity"),
	FOREIGN KEY ("deptName", "deptCity") REFERENCES "Department"("name", "city")
);

drop table if exists "TaskAssignments";
CREATE TABLE "TaskAssignments" (
	"worker" INT,
	FOREIGN KEY ("worker") REFERENCES "People"("ID"),
	"project" VARCHAR(50), 
	PRIMARY KEY ("worker", "project"), 
	"deptName" VARCHAR(50), 
	"deptCity" VARCHAR(50),
	FOREIGN KEY ("worker") REFERENCES "People"("ID"),
	FOREIGN KEY ("project", "deptName", "deptCity") REFERENCES "Projects"("name", "deptName", "deptCity"),
	FOREIGN KEY ("deptName", "deptCity") REFERENCES "Department"("name", "city")
);
