package tbrugz.queryon.http;

import java.math.*;

/*
 * http://www.h2database.com/html/features.html#user_defined_functions
 * http://www.h2database.com/html/grammar.html#create_alias
 * http://stackoverflow.com/questions/3098905/how-to-create-stored-procedure-using-h2-database
 * http://stackoverflow.com/questions/17293598/how-to-write-a-function-in-the-h2-database-without-using-java
 */
public class H2Functions {
	
	public static String PRIME_NAME = "IS_PRIME";
	public static String PRIME_SQL = "CREATE ALIAS "+PRIME_NAME+" FOR \""+H2Functions.class.getName()+".isPrime\";";
	
	public static boolean isPrime(int value) {
		return new BigInteger(String.valueOf(value)).isProbablePrime(100);
	}
	
}
