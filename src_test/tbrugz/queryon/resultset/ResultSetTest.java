package tbrugz.queryon.resultset;

import java.beans.IntrospectionException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import tbrugz.sqldump.resultset.ResultSetCollectionAdapter;
import tbrugz.sqldump.resultset.ResultSetListAdapter;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ResultSetTest {
	
	List<TestBean> l1;
	
	@Before
	public void init() {
		TestBean b1 = new TestBean(1, "one", "c1");
		TestBean b2 = new TestBean(2, "two", "c1");
		TestBean b3 = new TestBean(3, "three", "c2");
		
		l1 = new ArrayList<TestBean>();
		l1.add(b1); l1.add(b2); l1.add(b3);
	}
	
	@Test
	public void testListAdapter() throws IntrospectionException, SQLException {
		ResultSetListAdapter<TestBean> rsla = new ResultSetListAdapter<TestBean>("testbeanLA", TestBean.getUniqueCols(), TestBean.getAllCols(), l1, TestBean.class);
		Assert.assertTrue("Must have 1st element", rsla.next());

		Assert.assertEquals("1", rsla.getString(1));
		Assert.assertTrue("1st element on 1st row must be equal to 1", "1".equals(rsla.getString(1)));
		
		Assert.assertEquals("one", rsla.getString(2));
		Assert.assertEquals("c1", rsla.getString(3));
		
		try {
			rsla.getString(4);
			Assert.fail("exception IndexOutOfBoundsException should have occured");
		}
		catch(IndexOutOfBoundsException e) {
			//OK!
		}
		
		Assert.assertTrue("Must have 2nd element", rsla.next());
		Assert.assertEquals("c1", rsla.getString(3));

		Assert.assertTrue("Must have 3rd element", rsla.next());
		Assert.assertEquals("c2", rsla.getString(3));
		
		Assert.assertFalse("Must not have 4th element", rsla.next());
	}

	@Test
	public void testRSAbsolute() throws IntrospectionException, SQLException {
		ResultSetListAdapter<TestBean> rs = new ResultSetListAdapter<TestBean>("testbeanLA", TestBean.getUniqueCols(), TestBean.getAllCols(), l1, TestBean.class);
		rs.absolute(1);
		Assert.assertEquals("1", rs.getString(1));
		rs.next();
		Assert.assertEquals("2", rs.getString(1));
		rs.absolute(3);
		Assert.assertEquals("3", rs.getString(1));
		rs.absolute(1);
		Assert.assertEquals("1", rs.getString(1));
	}

	@Test
	public void testRSRelative() throws IntrospectionException, SQLException {
		ResultSetListAdapter<TestBean> rs = new ResultSetListAdapter<TestBean>("testbeanLA", TestBean.getUniqueCols(), TestBean.getAllCols(), l1, TestBean.class);
		rs.relative(2);
		Assert.assertEquals("2", rs.getString(1));
		rs.relative(1);
		Assert.assertEquals("3", rs.getString(1));
		rs.relative(-2);
		Assert.assertEquals("1", rs.getString(1));

		rs.absolute(3);
		Assert.assertEquals("3", rs.getString(1));
		rs.absolute(1);
		Assert.assertEquals("1", rs.getString(1));
	}
	
	@Test
	public void testRSSize0() throws IntrospectionException, SQLException {
		l1 = new ArrayList<TestBean>();
		ResultSetListAdapter<TestBean> rs = new ResultSetListAdapter<TestBean>("testbeanLA", TestBean.getUniqueCols(), TestBean.getAllCols(), l1, TestBean.class);
		Assert.assertFalse("Must not have any element", rs.next());
	}
	
	@Test
	public void testRSCollectionAdapterWithList() throws IntrospectionException, SQLException {
		ResultSetCollectionAdapter<TestBean> rs = new ResultSetCollectionAdapter<TestBean>("testbeanLA", TestBean.getUniqueCols(), l1, TestBean.class);
		for(int i=1;i<=3;i++) {
			Assert.assertTrue("Must have "+i+" element", rs.next());
		}
		Assert.assertFalse("Must not have 4th element", rs.next());
	}

	@Test
	public void testRSCollectionAdapterWithSet() throws IntrospectionException, SQLException {
		Set<TestBean> set = new HashSet<TestBean>();
		set.addAll(l1);
		
		ResultSetCollectionAdapter<TestBean> rs = new ResultSetCollectionAdapter<TestBean>("testbeanLA", TestBean.getUniqueCols(), set, TestBean.class);
		for(int i=1;i<=3;i++) {
			Assert.assertTrue("Must have "+i+" element", rs.next());
		}
		Assert.assertFalse("Must not have 4th element", rs.next());
	}
	
	@Test
	public void testRSGetByColumnName() throws IntrospectionException, SQLException {
		ResultSetListAdapter<TestBean> rs = new ResultSetListAdapter<TestBean>("testbeanLA", TestBean.getUniqueCols(), TestBean.getAllCols(), l1, TestBean.class);
		
		Assert.assertTrue("Must have 1st element", rs.next());
		Assert.assertEquals("1st column of 1st line must be equals to '1'", "1", rs.getString(1));
		Assert.assertEquals("column 'id' of 1st line must be equals to '1'", "1", rs.getString("id"));
		
		Assert.assertTrue("Must have 2nd element", rs.next());
		Assert.assertEquals("column 'description' of 2nd line must be equals to 'two'", "two", rs.getString("description"));
		
		Assert.assertTrue("Must have 3rd element", rs.next());
		Assert.assertEquals("column 'category' of 3rd line must be equals to 'two'", "c2", rs.getString("category"));
		Assert.assertNull("column 'xyz' of 3rd line must be null", rs.getString("xyz"));
		
		Assert.assertFalse("Must not have 4th element", rs.next());
	}
}
