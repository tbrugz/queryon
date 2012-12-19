INSERT INTO "Addresses" ("ID", "city", "state") VALUES (18, 'Cambridge', 'MA');

INSERT INTO "PeopleSimple" ("ID", "fname", "addr") VALUES (7, 'Bob', 18);
INSERT INTO "PeopleSimple" ("ID", "fname", "addr") VALUES (8, 'Sue', NULL);

INSERT INTO "People" ("ID", "fname", "addr", "deptName", "deptCity") VALUES (8, 'Sue', NULL, NULL, NULL);

INSERT INTO "Department" ("ID", "name", "city", "manager") VALUES (23, 'accounting', 'Cambridge', 8);

INSERT INTO "People" ("ID", "fname", "addr", "deptName", "deptCity") VALUES (7, 'Bob', 18, 'accounting', 'Cambridge');

INSERT INTO "Tweets" ("tweeter", "when", "text") VALUES (7, '2010-08-30 01:33:00', 'I really like lolcats.');
INSERT INTO "Tweets" ("tweeter", "when", "text") VALUES (7, '2010-08-30 09:01:00', 'I take it back.');

INSERT INTO "Projects" ("lead", "name", "deptName", "deptCity") VALUES (8, 'pencil survey', 'accounting', 'Cambridge');
INSERT INTO "Projects" ("lead", "name", "deptName", "deptCity") VALUES (8, 'eraser survey', 'accounting', 'Cambridge');

INSERT INTO "TaskAssignments" ("worker", "project", "deptName", "deptCity") VALUES (7, 'pencil survey', 'accounting', 'Cambridge');
