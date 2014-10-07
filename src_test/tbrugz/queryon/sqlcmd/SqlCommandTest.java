package tbrugz.queryon.sqlcmd;

import org.junit.Assert;
import org.junit.Test;

public class SqlCommandTest {

	@Test
	public void testShowSchemas() {
		ShowSchemas sc = new ShowSchemas();
		String sql = "$schemas";
		Assert.assertTrue(sc.matches(sql));
	}

	@Test
	public void testShowTables() {
		ShowTables sc = new ShowTables();
		
		String sql = "$tables";
		Assert.assertTrue(sc.matches(sql));
		Assert.assertNull(sc.schema);
		
		String sql2 = "$tables abc";
		Assert.assertTrue(sc.matches(sql2));
		Assert.assertEquals("abc", sc.schema);
	}

	@Test
	public void testShowColumns() {
		ShowColumns sc = new ShowColumns();
		
		String sql2 = "$columns abc";
		Assert.assertTrue(sc.matches(sql2));
		Assert.assertEquals("abc", sc.name);
		
		String sql = "$columns";
		Assert.assertTrue(sc.matches(sql));
		Assert.assertNull(sc.name);
		
	}
}
