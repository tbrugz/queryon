package tbrugz.queryon;

import org.junit.Assert;
import org.junit.Test;

import tbrugz.sqldump.dbmodel.Query;

public class SQLTest {

	@Test
	public void testNoParamSql() {
		Query q = new Query();
		q.setName("q1");
		q.setQuery("select * from table1");
		SQL sql = SQL.createSQL(q, null, null);
		Assert.assertEquals("select * from table1", sql.getInitialSql());
		Assert.assertEquals("select * from table1", sql.getFinalSql());
	}

	@Test
	public void testNamedParamSql() {
		Query q = new Query();
		q.setName("q1");
		q.setQuery("select * from table1 where a = :abc");
		SQL sql = SQL.createSQL(q, null, null);
		Assert.assertEquals("select * from table1 where a = :abc", sql.getInitialSql());
		Assert.assertEquals("select * from table1 where a = ?   ", sql.getFinalSql());
	}
	
	@Test
	public void testSqlWithFilterClause() {
		Query q = new Query();
		q.setName("q1");
		q.setQuery("select * from table1 where a = :abc $filter_clause");
		SQL sql = SQL.createSQL(q, null, null);
		Assert.assertEquals("select * from table1 where a = :abc ", sql.getInitialSql());
		Assert.assertEquals("select * from table1 where a = ?    ", sql.getFinalSql());
	}

	@Test
	public void testSqlWithUsername() {
		Query q = new Query();
		q.setQuery("select * from table1 where a = :abc and username = $username");
		{
			SQL sql = SQL.createSQL(q, null, null);
			Assert.assertEquals("select * from table1 where a = :abc and username = ''", sql.getInitialSql());
			Assert.assertEquals("select * from table1 where a = ?    and username = ''", sql.getFinalSql());
		}
		{
			SQL sql = SQL.createSQL(q, null, "john");
			Assert.assertEquals("select * from table1 where a = :abc and username = 'john'", sql.getInitialSql());
			Assert.assertEquals("select * from table1 where a = ?    and username = 'john'", sql.getFinalSql());
		}
	}

	@Test
	public void testSqlAddFilter() {
		Query q = new Query();
		q.setQuery("select * from table1 where a = ?");
		SQL sql = SQL.createSQL(q, null, null);
		sql.addFilter("b = ?");
		Assert.assertEquals("select * from (\n"
				+ "select * from table1 where a = ?\n"
				+ ") qon_filter where b = ?", sql.getFinalSql().trim());
	}

	@Test
	public void testSqlAddFilterUsingFilterClause() {
		Query q = new Query();
		q.setQuery("select * from table1 where a = ? $filter_clause");
		SQL sql = SQL.createSQL(q, null, null);
		sql.addFilter("b = ?");
		Assert.assertEquals("select * from table1 where a = ? and b = ?", sql.getFinalSql().trim());
	}

	@Test
	public void testSqlAddFilterUsingWhereClause() {
		Query q = new Query();
		q.setQuery("select * from table1 $where_clause");
		SQL sql = SQL.createSQL(q, null, null);
		sql.addFilter("b = ?");
		Assert.assertEquals("select * from table1 where b = ?", sql.getFinalSql().trim());
	}
	
}
