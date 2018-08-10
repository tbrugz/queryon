package tbrugz.queryon.resultset;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.datadump.FFCDataDump;
import tbrugz.sqldump.resultset.ResultSetListAdapter;

public class UnionResultSetTest {

	List<TestBean> l1;
	List<TestBean> l2;
	List<NamedTestBean> nl1;
	ResultSet baseRS;
	
	@Before
	public void init() {
		TestBean b1 = new TestBean(1, "one", "c1");
		TestBean b2 = new TestBean(2, "two", "c1");
		TestBean b3 = new TestBean(3, "three", "c2");
		NamedTestBean nt1 = new NamedTestBean(6, "sechs");
		NamedTestBean nt2 = new NamedTestBean(7, "sieben");
		
		l1 = new ArrayList<TestBean>();
		l1.add(b1); l1.add(b2); l1.add(b3);
		
		l2 = new ArrayList<TestBean>();
		l2.add(b2); l2.add(b3);

		nl1 = new ArrayList<NamedTestBean>();
		nl1.add(nt1); nl1.add(nt2);
	}

	@Test
	public void testWithSameCols() throws IntrospectionException, SQLException, IOException {
		ResultSetListAdapter<TestBean> rs1 = new ResultSetListAdapter<TestBean>("testbeanLA", TestBean.getUniqueCols(), TestBean.getAllCols(), l1, TestBean.class);
		ResultSetListAdapter<TestBean> rs2 = new ResultSetListAdapter<TestBean>("testbeanLA", TestBean.getUniqueCols(), TestBean.getAllCols(), l2, TestBean.class);
		List<ResultSet> rsl = new ArrayList<ResultSet>();
		rsl.add(rs1); rsl.add(rs2);
		
		UnionResultSet urs = new UnionResultSet(rsl, false);
		//DataDumpUtils.dumpRS(new FFCDataDump(), urs.getMetaData(), urs, "schema", "table", new PrintWriter(System.out, true), false);
		/*Writer w = new PrintWriter(System.out, true);
		DataDumpUtils.dumpRS(new FFCDataDump(), urs.getMetaData(), urs, "schema", "table", w, false);
		w.close();*/
		/*StringWriter sw = new StringWriter();
		DataDumpUtils.dumpRS(new FFCDataDump(), urs.getMetaData(), urs, "schema", "table", sw, false);
		System.out.println(sw);*/
		
		// rs1
		Assert.assertTrue(urs.next());
		Assert.assertEquals(1, urs.getInt(1));
		Assert.assertTrue(urs.next());
		Assert.assertEquals("two", urs.getString("description"));
		Assert.assertTrue(urs.next());
		Assert.assertEquals("c2", urs.getString(3));
		// rs2
		Assert.assertTrue(urs.next());
		Assert.assertEquals(2, urs.getInt(1));
		Assert.assertTrue(urs.next());
		Assert.assertEquals("three", urs.getString(2));
		
		Assert.assertFalse(urs.next());
		urs.close();
	}

	@Test
	public void testWithUnionOfDistincCols() throws IntrospectionException, SQLException, IOException {
		ResultSetListAdapter<TestBean> rs1 = new ResultSetListAdapter<TestBean>("tb", TestBean.getUniqueCols(), TestBean.getAllCols(), l1, TestBean.class);
		ResultSetListAdapter<NamedTestBean> rs2 = new ResultSetListAdapter<NamedTestBean>("ntb", NamedTestBean.getUniqueCols(), NamedTestBean.getAllCols(), nl1, NamedTestBean.class);
		List<ResultSet> rsl = new ArrayList<ResultSet>();
		rsl.add(rs1); rsl.add(rs2);
		
		UnionResultSet urs = new UnionResultSet(rsl, false);
		/*StringWriter sw = new StringWriter();
		DataDumpUtils.dumpRS(new FFCDataDump(), urs.getMetaData(), urs, "schema", "table", sw, false);
		System.out.println(sw);*/
		
		// rs1
		Assert.assertTrue(urs.next());
		Assert.assertEquals(1, urs.getInt(1));
		Assert.assertTrue(urs.next());
		Assert.assertEquals("two", urs.getString("description"));
		Assert.assertTrue(urs.next());
		Assert.assertEquals("c2", urs.getString(3));
		Assert.assertEquals(null, urs.getString("name"));
		// rs2
		Assert.assertTrue(urs.next());
		Assert.assertEquals(6, urs.getInt(1));
		Assert.assertEquals(null, urs.getString(2));
		Assert.assertTrue(urs.next());
		Assert.assertEquals(7, urs.getInt(1));
		Assert.assertEquals(null, urs.getString(2));
		Assert.assertEquals("sieben", urs.getString(4));
		Assert.assertEquals("sieben", urs.getString("name"));
		
		Assert.assertFalse(urs.next());
		urs.close();
	}

	@Test
	public void testWithIntersectOfDistincCols() throws IntrospectionException, SQLException, IOException {
		ResultSetListAdapter<TestBean> rs1 = new ResultSetListAdapter<TestBean>("tb", TestBean.getUniqueCols(), TestBean.getAllCols(), l1, TestBean.class);
		ResultSetListAdapter<NamedTestBean> rs2 = new ResultSetListAdapter<NamedTestBean>("ntb", NamedTestBean.getUniqueCols(), NamedTestBean.getAllCols(), nl1, NamedTestBean.class);
		List<ResultSet> rsl = new ArrayList<ResultSet>();
		rsl.add(rs1); rsl.add(rs2);
		
		UnionResultSet urs = new UnionResultSet(rsl, true);
		/*StringWriter sw = new StringWriter();
		DataDumpUtils.dumpRS(new FFCDataDump(), urs.getMetaData(), urs, "schema", "table", sw, false);
		System.out.println(sw);*/
		
		// rs1
		Assert.assertTrue(urs.next());
		Assert.assertEquals(1, urs.getInt(1));
		Assert.assertTrue(urs.next());
		Assert.assertEquals(null, urs.getString("description"));
		Assert.assertTrue(urs.next());
		Assert.assertEquals(null, urs.getString(3));
		Assert.assertEquals(null, urs.getString("name"));
		// rs2
		Assert.assertTrue(urs.next());
		Assert.assertEquals(6, urs.getInt(1));
		Assert.assertEquals(null, urs.getString(2));
		Assert.assertTrue(urs.next());
		Assert.assertEquals(7, urs.getInt(1));
		Assert.assertEquals(null, urs.getString(2));
		Assert.assertEquals(null, urs.getString(4));
		Assert.assertEquals(null, urs.getString("name"));
		
		Assert.assertFalse(urs.next());
		urs.close();
		
	}
	
}
