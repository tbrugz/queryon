package tbrugz.queryon.http;

import java.math.*;

// http://www.h2database.com/html/features.html#user_defined_functions
public class H2Functions {
	
	public static String NAME = "IS_PRIME";
	public static String SQL = "CREATE ALIAS "+NAME+" FOR \""+H2Functions.class.getName()+".isPrime\";";
	//public static String SQL = "CREATE ALIAS "+NAME+" FOR \"tbrugz.queryon.http.H2PrimeFunction.isPrime\";";
	
	public static boolean isPrime(int value) {
		return new BigInteger(String.valueOf(value)).isProbablePrime(100);
	}
	
}
