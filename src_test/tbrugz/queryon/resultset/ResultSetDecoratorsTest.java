package tbrugz.queryon.resultset;

import java.beans.IntrospectionException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import tbrugz.sqldump.resultset.ResultSetListAdapter;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ResultSetDecoratorsTest {
	
	List<TestBean> l1;
	ResultSet baseRS;
	
	@Before
	public void init() throws IntrospectionException {
		TestBean b1 = new TestBean(1, "one", "c1");
		TestBean b2 = new TestBean(2, "two", "c1");
		TestBean b3 = new TestBean(3, "three", "c2");
		
		l1 = new ArrayList<TestBean>();
		l1.add(b1); l1.add(b2); l1.add(b3);
		
		baseRS = new ResultSetListAdapter<TestBean>("testbeanLA", TestBean.getUniqueCols(), TestBean.getAllCols(), l1, TestBean.class);
	}
	
	@Test
	public void testListFilter() throws IntrospectionException, SQLException {
		List<Integer> lPos = Arrays.asList(new Integer[]{3});
		List<String> lValue = Arrays.asList(new String[]{"c1"});
		ResultSet rsfd = new ResultSetFilterDecorator(baseRS, lPos, lValue);
		
		Assert.assertTrue("Must have 1st element", rsfd.next());
		Assert.assertEquals("one", rsfd.getString(2));
		
		Assert.assertTrue("Must have 2nd element", rsfd.next());
		Assert.assertEquals("2", rsfd.getString(1));

		Assert.assertFalse("Must not have 3rd element", rsfd.next());
		rsfd.close();
	}
	
	@Test
	public void testLimitOffsetL1O1() throws SQLException, IntrospectionException {
		ResultSet rs = new ResultSetLimitOffsetDecorator(baseRS,1,1);
		
		Assert.assertTrue("Must have 1st element", rs.next());
		Assert.assertEquals("two", rs.getString(2));
		
		Assert.assertFalse("Must not have 2nd element", rs.next());
		rs.close();
	}

	@Test
	public void testLimitOffsetL2O1() throws SQLException, IntrospectionException {
		ResultSet rs = new ResultSetLimitOffsetDecorator(baseRS,2,1);
		
		Assert.assertTrue("Must have 1st element", rs.next());
		Assert.assertEquals("two", rs.getString(2));
		
		Assert.assertTrue("Must have 2nd element", rs.next());
		Assert.assertEquals("c2", rs.getString(3));

		Assert.assertFalse("Must not have 3rd element", rs.next());
		rs.close();
	}

	@Test
	public void testLimitOffsetL2O0() throws SQLException, IntrospectionException {
		ResultSet rs = new ResultSetLimitOffsetDecorator(baseRS,2,0);
		
		Assert.assertTrue("Must have 1st element", rs.next());
		Assert.assertEquals("one", rs.getString(2));
		
		Assert.assertTrue("Must have 2nd element", rs.next());
		Assert.assertEquals("c1", rs.getString(3));

		Assert.assertFalse("Must not have 3rd element", rs.next());
		rs.close();
	}
	
}
