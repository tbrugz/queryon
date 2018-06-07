package tbrugz.queryon.resultset;

import java.beans.IntrospectionException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.PrivilegeType;
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

	@Test @Ignore
	public void testListFilterOR() throws IntrospectionException, SQLException {
		List<Integer> lPos = Arrays.asList(new Integer[]{2,2});
		List<String> lValue = Arrays.asList(new String[]{"one", "three"});
		ResultSet rsfd = new ResultSetFilterDecorator(baseRS, lPos, lValue);
		
		Assert.assertTrue("Must have 1st element", rsfd.next());
		Assert.assertEquals("one", rsfd.getString(2));
		
		Assert.assertTrue("Must have 2nd (3rd original) element", rsfd.next());
		Assert.assertEquals("3", rsfd.getString(1));

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
	
	@Test
	public void testGrantsRS() throws SQLException, IntrospectionException {
		Set<String> roles = new HashSet<String>();
		roles.add("admin");
		
		Set<Grant> grantsUser = new HashSet<Grant>();
		grantsUser.add(new Grant("Owner",PrivilegeType.SELECT, "user"));
		Set<Grant> grantsAdmin = new HashSet<Grant>();
		grantsAdmin.add(new Grant("Owner",PrivilegeType.SELECT, "admin"));
		TestBean b1 = new TestBean(1, "one", grantsUser.toString());
		TestBean b2 = new TestBean(2, "two", null);
		TestBean b3 = new TestBean(3, "three", "[");
		TestBean b4 = new TestBean(4, "four", grantsAdmin.toString());
		l1 = new ArrayList<TestBean>();
		l1.add(b1); l1.add(b2); l1.add(b3); l1.add(b4);
		
		baseRS = new ResultSetListAdapter<TestBean>("testbeanLA", TestBean.getUniqueCols(), TestBean.getAllCols(), l1, TestBean.class);
		ResultSet rs = new ResultSetGrantsFilterDecorator(baseRS, roles, PrivilegeType.SELECT, "description", "category");
		int countRows = 0;
		while(rs.next()) {
			countRows++;
			//System.out.println(">>"+rs.getString("category"));
		}
		rs.close();
		Assert.assertEquals(3, countRows);
	}
	
}
