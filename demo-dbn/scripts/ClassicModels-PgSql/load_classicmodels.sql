
/* First make sure all the tables are empty */

DELETE FROM Customers;
DELETE FROM Employees;
DELETE FROM Offices;
DELETE FROM OrderDetails;
DELETE FROM Orders;
DELETE FROM Payments;
DELETE FROM Products;

/* Load records into the tables. */

COPY Customers FROM '${user.dir}/data/ClassicModels-MySQL/datafiles/Customers.txt' with csv NULL AS 'NULL' ENCODING 'LATIN1';

COPY Employees FROM '${user.dir}/data/ClassicModels-MySQL/datafiles/Employees.txt' with csv NULL AS 'NULL' ENCODING 'LATIN1';

COPY Offices FROM '${user.dir}/data/ClassicModels-MySQL/datafiles/Offices.txt' with csv NULL AS 'NULL' ENCODING 'LATIN1';

COPY OrderDetails FROM '${user.dir}/data/ClassicModels-MySQL/datafiles/OrderDetails.txt' with csv NULL AS 'NULL' ENCODING 'LATIN1';

COPY Orders FROM '${user.dir}/data/ClassicModels-MySQL/datafiles/Orders.txt' with csv NULL AS 'NULL' ENCODING 'LATIN1';

COPY Payments FROM '${user.dir}/data/ClassicModels-MySQL/datafiles/Payments.txt' with csv NULL AS 'NULL' ENCODING 'LATIN1';

COPY Products FROM '${user.dir}/data/ClassicModels-MySQL/datafiles/Products.txt' with csv NULL AS 'NULL' ENCODING 'LATIN1';
