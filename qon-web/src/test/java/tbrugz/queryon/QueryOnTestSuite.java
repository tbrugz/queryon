package tbrugz.queryon;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import tbrugz.queryon.http.ODataWebTest;
import tbrugz.queryon.http.WinstoneAndH2HttpRequestTest;
import tbrugz.queryon.r2rml.DirectMappingTest;
import tbrugz.queryon.resultset.ResultSetDecoratorsTest;
import tbrugz.queryon.resultset.ResultSetTest;
import tbrugz.queryon.sqlcmd.SqlCommandTest;
import tbrugz.queryon.util.StringUtilsTest;

@RunWith(Suite.class)
@SuiteClasses({
	SQLTest.class,
	DirectMappingTest.class,
	ResultSetDecoratorsTest.class,
	ResultSetTest.class,
	SqlCommandTest.class,
	StringUtilsTest.class,
	WinstoneAndH2HttpRequestTest.class,
	ODataWebTest.class,
})
public class QueryOnTestSuite {
	//public static final String basedir = "src/test/java";
}
