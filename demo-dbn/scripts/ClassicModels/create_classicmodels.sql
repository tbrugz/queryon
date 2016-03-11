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

/* DROP the existing tables. Comment this out if it is not needed. */

DROP TABLE IF EXISTS Customers;
DROP TABLE IF EXISTS Employees;
DROP TABLE IF EXISTS Offices;
DROP TABLE IF EXISTS OrderDetails;
DROP TABLE IF EXISTS Orders;
DROP TABLE IF EXISTS Payments;
DROP TABLE IF EXISTS Products;

/* Create the full set of Classic Models Tables */

CREATE TABLE Customers (
  customerNumber INTEGER NOT NULL,
  customerName VARCHAR(50) NOT NULL,
  contactLastName VARCHAR(50) NOT NULL,
  contactFirstName VARCHAR(50) NOT NULL,
  phone VARCHAR(50) NOT NULL,
  addressLine1 VARCHAR(50) NOT NULL,
  addressLine2 VARCHAR(50) NULL,
  city VARCHAR(50) NOT NULL,
  state VARCHAR(50) NULL,
  postalCode VARCHAR(15) NULL,
  country VARCHAR(50) NOT NULL,
  salesRepEmployeeNumber INTEGER NULL,
  creditLimit DOUBLE PRECISION NULL,
  PRIMARY KEY (customerNumber)
);

CREATE TABLE Employees (
  employeeNumber INTEGER NOT NULL,
  lastName VARCHAR(50) NOT NULL,
  firstName VARCHAR(50) NOT NULL,
  extension VARCHAR(10) NOT NULL,
  email VARCHAR(100) NOT NULL,
  officeCode VARCHAR(20) NOT NULL,
  reportsTo INTEGER NULL,
  jobTitle VARCHAR(50) NOT NULL,
  PRIMARY KEY (employeeNumber)
);

CREATE TABLE Offices (
  officeCode VARCHAR(50) NOT NULL,
  city VARCHAR(50) NOT NULL,
  phone VARCHAR(50) NOT NULL,
  addressLine1 VARCHAR(50) NOT NULL,
  addressLine2 VARCHAR(50) NULL,
  state VARCHAR(50) NULL,
  country VARCHAR(50) NOT NULL,
  postalCode VARCHAR(10) NOT NULL,
  territory VARCHAR(10) NOT NULL,
  PRIMARY KEY (officeCode)
);

CREATE TABLE OrderDetails (
  orderNumber INTEGER NOT NULL,
  productCode VARCHAR(50) NOT NULL,
  quantityOrdered INTEGER NOT NULL,
  priceEach DOUBLE PRECISION NOT NULL,
  orderLineNumber SMALLINT NOT NULL,
  PRIMARY KEY (orderNumber, productCode)
);

CREATE TABLE Orders (
  orderNumber INTEGER NOT NULL,
  orderDate TIMESTAMP NOT NULL,
  requiredDate TIMESTAMP NOT NULL,
  shippedDate TIMESTAMP NULL,
  status VARCHAR(15) NOT NULL,
  comments TEXT NULL,
  customerNumber INTEGER NOT NULL,
  PRIMARY KEY (orderNumber)
);

CREATE TABLE Payments (
  customerNumber INTEGER NOT NULL,
  checkNumber VARCHAR(50) NOT NULL,
  paymentDate TIMESTAMP NOT NULL,
  amount DOUBLE PRECISION NOT NULL,
  PRIMARY KEY (customerNumber, checkNumber)
);

CREATE TABLE Products (
  productCode VARCHAR(50) NOT NULL,
  productName VARCHAR(70) NOT NULL,
  productLine VARCHAR(50) NOT NULL,
  productScale VARCHAR(10) NOT NULL,
  productVendor VARCHAR(50) NOT NULL,
  productDescription TEXT NOT NULL,
  quantityInStock SMALLINT NOT NULL,
  buyPrice DOUBLE PRECISION NOT NULL,
  MSRP DOUBLE PRECISION NOT NULL,
  PRIMARY KEY (productCode)
);

