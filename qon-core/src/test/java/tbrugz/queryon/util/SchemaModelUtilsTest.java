package tbrugz.queryon.util;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import tbrugz.sqldump.dbmodel.Query;

public class SchemaModelUtilsTest {
	
	@Test
	public void testNullBindingParameters0() {
		String sql = "select * from x";
		Query v = new Query();
		v.setParameterCount(0);
		v.setQuery(sql);
		boolean[] nullBindings = SchemaModelUtils.getNullBindingParameters(v);
		System.out.println("nullBindings="+Arrays.toString(nullBindings));
		Assert.assertNotNull(nullBindings);
		boolean[] defaultValue = new boolean[] {}; //see: SQL.processPatternBooleanArray
		Assert.assertArrayEquals(defaultValue, nullBindings);
	}

	@Test
	public void testNullBindingParameters1() {
		String sql = "/* bind-null-on-missing-parameters=false */ select * from x where p1 = ?";
		Query v = new Query();
		v.setParameterCount(1);
		v.setQuery(sql);
		boolean[] nullBindings = SchemaModelUtils.getNullBindingParameters(v);
		//System.out.println("nullBindings="+Arrays.toString(nullBindings));
		Assert.assertNotNull(nullBindings);
		Assert.assertArrayEquals(new boolean[] {false}, nullBindings);
	}

	@Test
	public void testNullBindingParameters2() {
		String sql = "/* bind-null-on-missing-parameters=false,true */ select * from x where p1 = ? and p2 = ?";
		Query v = new Query();
		v.setParameterCount(2);
		v.setQuery(sql);
		boolean[] nullBindings = SchemaModelUtils.getNullBindingParameters(v);
		//System.out.println("nullBindings="+Arrays.toString(nullBindings));
		Assert.assertNotNull(nullBindings);
		Assert.assertArrayEquals(new boolean[] {false, true}, nullBindings);
	}
	
	@Test
	public void testNullBindingNamedParameters1and1() {
		String sql = "/* bind-null-on-missing-parameters=true */ select * from x where p1 = :a";
		Query v = new Query();
		//v.setParameterCount(2);
		v.setNamedParameterNames(Arrays.asList(new String[] {"a"}));
		v.setQuery(sql);
		boolean[] nullBindingsNamed = SchemaModelUtils.getNullBindingNamedParameters(v);
		//System.out.println("nullBindings="+Arrays.toString(nullBindingsNamed));
		Assert.assertNotNull(nullBindingsNamed);
		Assert.assertArrayEquals(new boolean[] {true}, nullBindingsNamed);
	}

	@Test
	public void testNullBindingNamedParameters2and1() {
		String sql = "/* bind-null-on-missing-parameters=false,true */ select * from x where p1 = :a and p2 = :a";
		Query v = new Query();
		//v.setParameterCount(2);
		v.setNamedParameterNames(Arrays.asList(new String[] {"a", "a"}));
		v.setQuery(sql);
		boolean[] nullBindingsNamed = SchemaModelUtils.getNullBindingNamedParameters(v);
		//System.out.println("nullBindings="+Arrays.toString(nullBindingsNamed));
		Assert.assertNotNull(nullBindingsNamed);
		Assert.assertArrayEquals(new boolean[] {false}, nullBindingsNamed);
	}

	@Test
	public void testNullBindingNamedParameters2and2() {
		String sql = "/* bind-null-on-missing-parameters=false,true */ select * from x where p1 = :a and p2 = :b";
		Query v = new Query();
		//v.setParameterCount(2);
		v.setNamedParameterNames(Arrays.asList(new String[] {"a", "b"}));
		v.setQuery(sql);
		boolean[] nullBindingsNamed = SchemaModelUtils.getNullBindingNamedParameters(v);
		//System.out.println("nullBindings="+Arrays.toString(nullBindingsNamed));
		Assert.assertNotNull(nullBindingsNamed);
		Assert.assertArrayEquals(new boolean[] {false, true}, nullBindingsNamed);
	}

	@Test
	public void testNullBindingNamedParameters3and2() {
		String sql = "/* bind-null-on-missing-parameters=true,false,true */ select * from x where p1 = :a and p2 = :b and p2 = :a";
		Query v = new Query();
		//v.setParameterCount(2);
		v.setNamedParameterNames(Arrays.asList(new String[] {"a", "b", "a"}));
		v.setQuery(sql);
		boolean[] nullBindingsNamed = SchemaModelUtils.getNullBindingNamedParameters(v);
		//System.out.println("nullBindings="+Arrays.toString(nullBindingsNamed));
		Assert.assertNotNull(nullBindingsNamed);
		Assert.assertArrayEquals(new boolean[] {true, false}, nullBindingsNamed);
	}
	
	@Test
	public void testNullBindingNamedParameters3and2NoNull() {
		String sql = "/*\n"
				+ "	named-parameters=par1,par2,par1\n"
				+ "	*/\n"
				+ "	select cast(? as varchar) as c1\n"
				+ "	union all select cast(? as varchar)\n"
				+ "	union all select cast(? as varchar)";
		Query v = new Query();
		//v.setParameterCount(2);
		v.setNamedParameterNames(Arrays.asList(new String[] {"par1", "par2", "par1"}));
		v.setQuery(sql);
		boolean[] nullBindingsNamed = SchemaModelUtils.getNullBindingNamedParameters(v);
		//System.out.println("nullBindings="+Arrays.toString(nullBindingsNamed));
		Assert.assertNotNull(nullBindingsNamed);
		Assert.assertArrayEquals(new boolean[] {false, false}, nullBindingsNamed);
	}
	
	@Test
	public void testNullBindingNamedParameters3and2B() {
		String sql = "/*\n"
				+ "	named-parameters=par1,par2,par1\n"
				+ " bind-null-on-missing-parameters=true\n"
				+ "	*/\n"
				+ "	select cast(? as varchar) as c1\n"
				+ "	union all select cast(? as varchar)\n"
				+ "	union all select cast(? as varchar)";
		Query v = new Query();
		//v.setParameterCount(2);
		v.setNamedParameterNames(Arrays.asList(new String[] {"par1", "par2", "par1"}));
		v.setQuery(sql);
		boolean[] nullBindingsNamed = SchemaModelUtils.getNullBindingNamedParameters(v);
		//System.out.println("nullBindings="+Arrays.toString(nullBindingsNamed));
		Assert.assertNotNull(nullBindingsNamed);
		Assert.assertArrayEquals(new boolean[] {true, true}, nullBindingsNamed);
	}
	
}
