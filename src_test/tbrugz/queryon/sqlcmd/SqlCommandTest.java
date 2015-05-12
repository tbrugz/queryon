package tbrugz.queryon.sqlcmd;

import org.junit.Assert;
import org.junit.Test;

public class SqlCommandTest {

	@Test
	public void testShowSchemas() {
		ShowSchemas sc = new ShowSchemas();

		String sql1 = "$schemas";
		Assert.assertTrue(sc.matches(sql1));

		String sql2 = "$schemas abc";
		Assert.assertTrue(sc.matches(sql2));
		Assert.assertEquals("abc", sc.schema);
	}

	@Test
	public void testShowTables() {
		ShowTables sc = new ShowTables();
		
		String sql = "$tables";
		Assert.assertTrue(sc.matches(sql));
		Assert.assertNull(sc.schema);
		
		String sql2 = "$tables abc.bcd";
		Assert.assertTrue(sc.matches(sql2));
		Assert.assertEquals("abc", sc.schema);
		Assert.assertEquals("bcd", sc.table);

		String sql3 = "$tables abc";
		Assert.assertTrue(sc.matches(sql3));
		Assert.assertEquals("abc", sc.schema);
		Assert.assertNull(sc.table);
	}

	@Test
	public void testShowColumns() {
		ShowColumns sc = new ShowColumns();
		
		String sql2 = "$columns abc";
		Assert.assertTrue(sc.matches(sql2));
		Assert.assertEquals("abc", sc.table);
		
		String sql = "$columns";
		Assert.assertTrue(sc.matches(sql));
		Assert.assertNull(sc.table);
		
		String sql3 = "$columns abc.bcd";
		Assert.assertTrue(sc.matches(sql3));
		Assert.assertEquals("abc", sc.schema);
		Assert.assertEquals("bcd", sc.table);
		
		String sql4 = "$columns abc.bcd.cde%";
		Assert.assertTrue(sc.matches(sql4));
		Assert.assertEquals("abc", sc.schema);
		Assert.assertEquals("bcd", sc.table);
		Assert.assertEquals("cde%", sc.column);
	}
}
