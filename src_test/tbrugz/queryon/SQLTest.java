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
}
