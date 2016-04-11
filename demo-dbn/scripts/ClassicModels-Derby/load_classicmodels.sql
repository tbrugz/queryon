
/* Load records into the tables. */

/* https://db.apache.org/derby/docs/10.7/tools/ctoolsimport16245.html */

CALL SYSCS_UTIL.SYSCS_IMPORT_TABLE 
	(null,'CUSTOMERS','${user.dir}/data/ClassicModels-MySQL/datafiles/Customers.txt',',','"',null,0);
	
