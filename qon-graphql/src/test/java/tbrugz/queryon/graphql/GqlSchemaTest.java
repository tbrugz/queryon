package tbrugz.queryon.graphql;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import tbrugz.sqldump.JAXBSchemaXMLSerializer;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.def.SchemaModelGrabber;

public class GqlSchemaTest {

	static final String workDir = "work";
	
	SchemaModel getSchemaModel() {
		String xmlPath = "../qon-web/src/test/resources/tbrugz/queryon/http/empdept.jaxb.xml";
		SchemaModelGrabber sg = new JAXBSchemaXMLSerializer();
		Properties p = new Properties();
		p.setProperty("sqldump.xmlserialization.jaxb.infile", xmlPath);
		sg.setProperties(p);
		return sg.grabSchema();
	}
	
	GraphQL getGraphQL() {
		GqlSchemaFactory gs = new GqlSchemaFactory(getSchemaModel());
		GraphQLSchema graphQLSchema = gs.getSchema();
		
		return GraphQL.newGraphQL(graphQLSchema).build();
	}
	
	@Test
	public void printSchema() throws IOException {
		GqlSchemaFactory gs = new GqlSchemaFactory(getSchemaModel());
		GraphQLSchema schema = gs.getSchema();
		
		SchemaPrinter sp = new SchemaPrinter();
		String schemaStr = sp.print(schema);
		
		//System.out.println(schemaStr);
		new File(workDir).mkdirs();
		FileWriter fw = new FileWriter(workDir+"/schema1.graphqls");
		fw.write(schemaStr);
		fw.close();
	}
	
	@Test
	public void testIntrospection() {
		GqlSchemaFactory gs = new GqlSchemaFactory(getSchemaModel());
		GraphQLSchema graphQLSchema = gs.getSchema();
		
		GraphQL build = GraphQL.newGraphQL(graphQLSchema).build();
		//ExecutionResult executionResult = build.execute("{hello}");
		
		// https://graphql.org/learn/introspection/
		String query = "{ __schema { types { name } } }";
		query = "{ __schema { queryType { name } } }";
		query = "{ __type(name: \"EMP\") { name } }";
		query = "{ __type(name: \"EMP\") { name kind } }";
		query = "{ __type(name: \"EMP\") { name kind fields { name type { name kind } } } }";
		query = "{ __type(name: \"EMP\") { name kind fields { name type { name kind ofType { name kind } } } } }";
		query = "{ __type(name: \"DEPT\") { name description } }";
		ExecutionResult executionResult = build.execute(query);

		System.out.println(executionResult);
		Assert.assertNotNull(executionResult.getData());
		System.out.println(executionResult.getData().toString());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testQueryType() {
		GraphQL graphql = getGraphQL();
		String query = "{ __schema { queryType { name } } }";
		ExecutionResult executionResult = graphql.execute(query);
		
		Object data = executionResult.getData();
		Assert.assertNotNull(data);
		System.out.println(data.toString());
		Assert.assertThat(data, CoreMatchers.instanceOf(Map.class));
		Map<String, Object> m = (Map<String, Object>) data;
		Assert.assertNotNull(m.get("__schema"));
		m = (Map<String, Object>) m.get("__schema");
		Assert.assertNotNull(m.get("queryType"));
		m = (Map<String, Object>) m.get("queryType");
		System.out.println("queryType = "+m);
		String name = (String) m.get("name");
		Assert.assertEquals("QueryType", name);
	}

	@Test
	public void testQuerySyntax() {
		GqlSchemaFactory gs = new GqlSchemaFactory(getSchemaModel());
		GraphQLSchema graphQLSchema = gs.getSchema();
		
		GraphQL build = GraphQL.newGraphQL(graphQLSchema).build();

		//---
		
		String query = "query { list_DEPT (limit:10, offset: 0) { ID NAME } }";
		ExecutionResult executionResult = build.execute(query);

		//System.out.println(executionResult);
		Assert.assertNotNull(executionResult.getData());
		Assert.assertEquals(0, executionResult.getErrors().size());
		System.out.println(executionResult.getData().toString());
		
		//---
		
		query = "{ list_DEPT(limit:10, offset: 0) { ID NAME } }";
		executionResult = build.execute(query);
		
		Assert.assertNotNull(executionResult.getData());
		Assert.assertEquals(0, executionResult.getErrors().size());
		System.out.println(executionResult.getData().toString());

		//---
		
		query = "{ list_DEPT { ID NAME } }";
		executionResult = build.execute(query);
		
		Assert.assertNotNull(executionResult.getData());
		Assert.assertEquals(0, executionResult.getErrors().size());
		System.out.println(executionResult.getData().toString());

		//---

        /*
		query = "{ DEPT(limit:10, fin_ID: [1]) { ID NAME } }";
		executionResult = build.execute(query);
		
		Assert.assertNotNull(executionResult.getData());
		Assert.assertEquals(0, executionResult.getErrors().size());
		System.out.println(executionResult.getData().toString());

		//---

		query = "{ DEPT(limit:10, fin_ID: [1]) { ID (fnin: [2,3]) NAME (flk: \"abc%\") } }";
		executionResult = build.execute(query);
		
		Assert.assertNotNull(executionResult.getData());
		Assert.assertEquals(0, executionResult.getErrors().size());
		System.out.println(executionResult.getData().toString());
		*/
        
		//---

		query = "{ list_DEPT { ID NAME (flk: [\"abc%\", \"%def\"]) } }";
		executionResult = build.execute(query);
		
		Assert.assertNotNull(executionResult.getData());
		Assert.assertEquals(0, executionResult.getErrors().size());
		System.out.println(executionResult.getData().toString());
		
		//--- ERR
		
		query = "query { list_DEPT(limit:10, offset: 0) { } }";
		executionResult = build.execute(query);
		
		Assert.assertNull(executionResult.getData());
		Assert.assertEquals(1, executionResult.getErrors().size());
		System.out.println(executionResult.getErrors().toString());
		
		//--- ERR
		
		query = "query { list_DEPT(limit:10, offset: 0) }";
		executionResult = build.execute(query);
		
		Assert.assertNull(executionResult.getData());
		Assert.assertEquals(1, executionResult.getErrors().size());
		System.out.println(executionResult.getErrors().toString());

	}
	
	@Test
	public void testQuerySyntax1() {
		GqlSchemaFactory gs = new GqlSchemaFactory(getSchemaModel());
		DataFetcher<?> df = new LogDataFetcher<>();
		GraphQLSchema graphQLSchema = gs.getSchema(df);
		
		GraphQL build = GraphQL.newGraphQL(graphQLSchema).build();

		//---
		
		String query = "query { list_DEPT (limit:10, offset: 0) { ID (fin: [1,2]) NAME } }";
		System.out.println("query: "+query);
		ExecutionResult executionResult = build.execute(query);

		//System.out.println(executionResult);
		Assert.assertNotNull(executionResult.getData());
		Assert.assertEquals(0, executionResult.getErrors().size());
		System.out.println(executionResult.getData().toString());
	}

	@Test
	public void testQueryWithLogger() {
		GqlSchemaFactory gs = new GqlSchemaFactory(getSchemaModel());
		DataFetcher<?> df = new LogDataFetcher<>();
		GraphQLSchema graphQLSchema = gs.getSchema(df);
		
		GraphQL build = GraphQL.newGraphQL(graphQLSchema).build();

		//---

		String query = "{ list_DEPT(limit:10) { ID (fnin: [2,3]) NAME (flk: \"abc%\") } }";
		ExecutionResult executionResult = build.execute(query);
		
		Assert.assertNotNull(executionResult.getData());
		Assert.assertEquals(0, executionResult.getErrors().size());
		System.out.println(executionResult.getData().toString());
		
	}

	@Test
	public void testQueryWithDf1() {
		GqlSchemaFactory gs = new GqlSchemaFactory(getSchemaModel());
		DataFetcher<?> df = new DF1<>();
		GraphQLSchema graphQLSchema = gs.getSchema(df);
		
		GraphQL build = GraphQL.newGraphQL(graphQLSchema).build();

		//---

		String query = "{ list_DEPT(limit:10) { ID (fnin: [2,3]) NAME (flk: \"abc%\") } }";
		ExecutionResult executionResult = build.execute(query);
		
		Assert.assertNotNull(executionResult.getData());
		Assert.assertEquals(0, executionResult.getErrors().size());
		System.out.println(executionResult.getData().toString());
		
	}
	
}
