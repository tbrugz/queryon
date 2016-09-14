/******************************************************************************
 * Copyright (c) 2005 Actuate Corporation.
 * All rights reserved. This file and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial implementation
 *
 * Classic Models Inc. sample database developed as part of the
 * Eclipse BIRT Project. For more information, see http:\\www.eclipse.org\birt
 *
 *******************************************************************************/

/* Loads the Classic Models tables using the MySQL LOAD command */

/* Preparing the load files for importing. Input file requirements:
     - Column order in the file must be the same as the columns in the table
     - Columns are Comma delimited
     - Text is quoted (")
     - NULL columns must be ,NULL,  ( ,, is not acceptable)
     - Dates must be in Y/M/D format

   Input files expected in the datafiles direcory, parallel to this script.
*/


/* Load records into the tables. There should be no warnings. */

LOAD DATA LOCAL INFILE 'data/ClassicModels-MySQL/datafiles/Customers.txt' INTO TABLE Customers
          FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\r\n';

SHOW WARNINGS LIMIT 10;

LOAD DATA LOCAL INFILE 'data/ClassicModels-MySQL/datafiles/Employees.txt' INTO TABLE Employees
          FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\r\n';

SHOW WARNINGS LIMIT 10;

LOAD DATA LOCAL INFILE 'data/ClassicModels-MySQL/datafiles/Offices.txt' INTO TABLE Offices
          FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\r\n';

SHOW WARNINGS LIMIT 10;

LOAD DATA LOCAL INFILE 'data/ClassicModels-MySQL/datafiles/OrderDetails.txt' INTO TABLE OrderDetails
          FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\r\n';

SHOW WARNINGS LIMIT 10;

LOAD DATA LOCAL INFILE 'data/ClassicModels-MySQL/datafiles/Orders.txt' INTO TABLE Orders
          FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\r\n';

SHOW WARNINGS LIMIT 10;

LOAD DATA LOCAL INFILE 'data/ClassicModels-MySQL/datafiles/Payments.txt' INTO TABLE Payments
          FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\r\n';

SHOW WARNINGS LIMIT 10;

LOAD DATA LOCAL INFILE 'data/ClassicModels-MySQL/datafiles/Products.txt' INTO TABLE Products
          FIELDS TERMINATED BY ',' ENCLOSED BY '"' LINES TERMINATED BY '\r\n';

