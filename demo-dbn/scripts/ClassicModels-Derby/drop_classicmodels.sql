
set schema classicmodels;

DROP TABLE Customers;
DROP TABLE Employees;
DROP TABLE Offices;
DROP TABLE OrderDetails;
DROP TABLE Orders;
DROP TABLE Payments;
DROP TABLE Products;

DROP TABLE countries;

DROP SCHEMA classicmodels RESTRICT;

set schema queryon;

drop table queryon.qon_queries;
drop table queryon.qon_execs;
drop table queryon.qon_pages;
drop table queryon.qon_tables;

DROP SCHEMA queryon RESTRICT;
