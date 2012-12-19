package tbrugz.queryon;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import tbrugz.queryon.http.WinstoneAndH2HttpRequestTest;
import tbrugz.queryon.r2rml.DirectMappingTest;
import tbrugz.queryon.resultset.ResultSetDecoratorsTest;
import tbrugz.queryon.resultset.ResultSetTest;

@RunWith(Suite.class)
@SuiteClasses({
	DirectMappingTest.class,
	ResultSetDecoratorsTest.class,
	ResultSetTest.class,
	WinstoneAndH2HttpRequestTest.class,
})
public class QueryOnTestSuite {

}
