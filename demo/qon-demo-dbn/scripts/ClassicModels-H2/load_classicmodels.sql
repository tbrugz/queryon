
/* Load records into the tables. */

insert into Customers SELECT * FROM CSVREAD('data/ClassicModels-MySQL/datafiles/Customers.txt', null, 'null=NULL');

insert into Employees SELECT * FROM CSVREAD('data/ClassicModels-MySQL/datafiles/Employees.txt', null, 'null=NULL');

insert into Offices SELECT * FROM CSVREAD('data/ClassicModels-MySQL/datafiles/Offices.txt', null, 'null=NULL');

insert into OrderDetails SELECT * FROM CSVREAD('data/ClassicModels-MySQL/datafiles/OrderDetails.txt', null, 'null=NULL');

/* http://stackoverflow.com/questions/15844014/h2-db-csvread-command-converting-value-to-date-before-placing-into-varchar */
insert into Orders (orderNumber, orderDate, requiredDate, shippedDate, status, comments, customerNumber) SELECT orderNumber,
  parsedatetime(orderDate, 'yyyy/M/d H:mm:ss') as orderDate,
  parsedatetime(requiredDate, 'yyyy/M/d H:mm:ss') as requiredDate,
  parsedatetime(shippedDate, 'yyyy/M/d H:mm:ss') as shippedDate,
  status, comments, customerNumber
  FROM CSVREAD('data/ClassicModels-MySQL/datafiles/Orders.txt', 'ORDERNUMBER, ORDERDATE, REQUIREDDATE, SHIPPEDDATE, STATUS, COMMENTS, CUSTOMERNUMBER', 'null=NULL');

insert into Payments SELECT customerNumber, checkNumber,
  parsedatetime(paymentDate, 'yyyy/M/d H:mm:ss') as paymentDate,
  amount FROM CSVREAD('data/ClassicModels-MySQL/datafiles/Payments.txt', 'CUSTOMERNUMBER, CHECKNUMBER, PAYMENTDATE, AMOUNT', 'null=NULL');

insert into Products SELECT * FROM CSVREAD('data/ClassicModels-MySQL/datafiles/Products.txt', null, 'null=NULL');
