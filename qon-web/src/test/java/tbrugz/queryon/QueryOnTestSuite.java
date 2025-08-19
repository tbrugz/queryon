package tbrugz.queryon;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import tbrugz.queryon.http.GraphQlWebTest;
import tbrugz.queryon.http.ODataWebTest;
import tbrugz.queryon.http.SoapCodeGenTest;
//import tbrugz.queryon.http.SoapCodeGenCallTest;
//import tbrugz.queryon.http.SoapWebTest;
import tbrugz.queryon.http.WebDavWebTest;
import tbrugz.queryon.http.WinstoneAndH2HttpRequestTest;
//import tbrugz.queryon.r2rml.DirectMappingTest;
import tbrugz.queryon.resultset.ResultSetDecoratorsTest;
import tbrugz.queryon.resultset.ResultSetTest;
import tbrugz.queryon.sqlcmd.SqlCommandTest;
import tbrugz.queryon.util.StringUtilsTest;

@RunWith(Suite.class)
@SuiteClasses({
	SQLTest.class,
	//DirectMappingTest.class,
	ResultSetDecoratorsTest.class,
	ResultSetTest.class,
	SqlCommandTest.class,
	StringUtilsTest.class,
	WinstoneAndH2HttpRequestTest.class,
	ODataWebTest.class,
	GraphQlWebTest.class,
	//SoapWebTest.class, //XXX: java17+
	SoapCodeGenTest.class,
	//SoapCodeGenCallTest.class,
	WebDavWebTest.class,
})
public class QueryOnTestSuite {
	//public static final String basedir = "src/test/java";
}
