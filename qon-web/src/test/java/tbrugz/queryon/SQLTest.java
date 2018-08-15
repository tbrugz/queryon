package tbrugz.queryon;

import org.junit.Assert;
import org.junit.Test;

import tbrugz.sqldump.dbmodel.Query;

public class SQLTest {

	Query q = new Query();
	boolean defaultAllow = true;
	
	@Test
	public void testAllowEncapsulation() {
		q.setQuery("select * from bla");
		SQL sql = SQL.createSQL(q, null, null);
		Assert.assertEquals(defaultAllow, sql.allowEncapsulation);
	}

	@Test
	public void testDontAllowEncapsulation() {
		q.setQuery("/* allow-encapsulation=false */ select * from bla");
		SQL sql = SQL.createSQL(q, null, null);
		Assert.assertEquals(false, sql.allowEncapsulation);
	}

	@Test
	public void testDontAllowNoMatch() {
		q.setQuery("/* allow-encapsulation=falsey */ select * from bla");
		SQL sql = SQL.createSQL(q, null, null);
		Assert.assertEquals(defaultAllow, sql.allowEncapsulation);
	}

	@Test
	public void testDontAllowMatch() {
		q.setQuery("/*allow-encapsulation =\nfalse,y*/ select * from bla");
		SQL sql = SQL.createSQL(q, null, null);
		Assert.assertEquals(false, sql.allowEncapsulation);
	}

	@Test
	public void testBooleanPattern() {
		String sql = "/* allow-encapsulation=false */";
		boolean bool = SQL.processPatternBoolean(sql, SQL.allowEncapsulationBooleanPattern, true);
		Assert.assertEquals(false, bool);
	}

	@Test
	public void testBooleanArrayPattern() {
		{
			String sql = "/* bind-null-on-missing-parameters=false */";
			boolean[] bool = SQL.processPatternBooleanArray(sql, SQL.bindNullOnMissingParamsPattern, true);
			Assert.assertArrayEquals(new boolean[]{false}, bool);
		}

		{
			String sql = "/* bind-null-on-missing-parameters=false,true,false */";
			boolean[] bool = SQL.processPatternBooleanArray(sql, SQL.bindNullOnMissingParamsPattern, true);
			Assert.assertArrayEquals(new boolean[]{false,true,false}, bool);
		}

		{
			String sql = "/* bind-null-on-missing-parameters=true,false,true */";
			boolean[] bool = SQL.processPatternBooleanArray(sql, SQL.bindNullOnMissingParamsPattern, true);
			Assert.assertArrayEquals(new boolean[]{true,false,true}, bool);
		}
	}
	
}
