package tbrugz.queryon.graphql;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.QueryOn.ActionType;
import tbrugz.queryon.util.DBUtil;
import tbrugz.sqldump.dbmodel.Column;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;

/**
 * see:
 *  
 * https://graphql-java.readthedocs.io/en/latest/schema.html#creating-a-schema-programmatically
 * https://github.com/graphql-java/graphql-java/blob/master/src/test/groovy/graphql/StarWarsSchema.java
 * https://github.com/howtographql/graphql-java/blob/master/pom.xml
 * 
 * @author tbrugz
 */
/*
 * XXXxx: add (gql)field-name->(qon)schema-object/method (needed by GqlRequestSpec)?
 */
public class GqlSchemaFactory { // GqlSchemaBuilder?
	
	public static class QonAction {
		final ActionType atype;
		final DBObjectType dbType;
		final String objectName;
		
		public QonAction(ActionType atype, DBObjectType dbType, String name) {
			this.atype = atype;
			this.dbType = dbType;
			this.objectName = name;
		}
	}

	boolean addFiltersToTypeField = true;
	boolean addFiltersToQueryField = false;
	
	final SchemaModel sm;
	final Map<String, QonAction> amap = new HashMap<>();
	transient Map<String, GraphQLObjectType> typeMap = new HashMap<>();
	
	public GqlSchemaFactory(SchemaModel sm) {
		this.sm = sm;
	}
	
	public GraphQLSchema getSchema() {
		return getSchema(null);
	}
	
	public GraphQLSchema getSchema(DataFetcher<?> df) {
		/*
		 * GraphQLObjectType fooType = GraphQLObjectType.newObject() .name("Foo")
		 * .field(GraphQLFieldDefinition.newFieldDefinition() .name("bar")
		 * .type(Scalars.GraphQLString)) .build();
		 */

		//List<GraphQLObjectType> types = new ArrayList<>();
		GraphQLSchema.Builder gqlSchemaBuilder = GraphQLSchema.newSchema();
		
		// SELECT
		GraphQLObjectType.Builder queryBuilder = GraphQLObjectType.newObject().name("QueryType");
		
		for(Relation t: sm.getTables()) {
			addRelation(queryBuilder, t, df);
		}
		for(Relation v: sm.getViews()) {
			addRelation(queryBuilder, v, df);
		}
		gqlSchemaBuilder.query(queryBuilder.build());
		
		// INSERT/UPDATE/DELETE
		// https://graphql.org/learn/queries/#mutations
		// https://graphql.org/learn/schema/#the-query-and-mutation-types
		GraphQLObjectType.Builder mutationBuilder = GraphQLObjectType.newObject().name("MutationType");
		int mutationCount = 0;
		GraphQLObjectType updateType = getUpdateType(mutationBuilder);
		for(Table t: sm.getTables()) {
			addMutation(mutationBuilder, t, updateType, df);
			mutationCount++;
		}
		if(mutationCount>0) {
			//add UpdateInfo type
			gqlSchemaBuilder.mutation(mutationBuilder.build());
		}
		
		// XXXxx each type should have a query? -> listXxx, listSchemaName
		// XXX add executables (mutations)
		// XXX add relation's Mutation's? insert/update/delete
		// https://github.com/okgrow/merge-graphql-schemas#merging-type-definitions

		return gqlSchemaBuilder.build();
	}
	
	void addRelation(GraphQLObjectType.Builder queryBuilder, Relation r, DataFetcher<?> df) {
		GraphQLObjectType.Builder builder = GraphQLObjectType.newObject()
				//.name(t.getQualifiedName())
				.name(r.getName());
		if(r.getRemarks()!=null) {
			builder.description(r.getRemarks());
		}
		for(int i=0;i<r.getColumnCount();i++) {
			String cName = r.getColumnNames().get(i);
			String cType = r.getColumnTypes().get(i);
			GraphQLFieldDefinition.Builder f = GraphQLFieldDefinition.newFieldDefinition()
					.name(cName)
					.type(getGlType(cType));
			if(addFiltersToTypeField) {
				addArgumentsToField(f, cType, cName, false);
			}
			// if(df!=null) { f.dataFetcher(df); } // add dataFetcher to Relation's fields? don't think so...
			builder.field(f);
			
		}
		GraphQLObjectType gt = builder.build();
		typeMap.put(r.getName(), gt);
		queryBuilder.field(createQueryField(gt, r, df));
	}

	void addMutation(GraphQLObjectType.Builder mutationBuilder, Table t, GraphQLObjectType returnType, DataFetcher<?> df) {
		GraphQLObjectType gt = typeMap.get(t.getName());
		mutationBuilder.field(createInsertField(gt, returnType, t, df));
	}
	
	GraphQLObjectType getUpdateType(GraphQLObjectType.Builder mutationBuilder) {
		GraphQLObjectType.Builder builder = GraphQLObjectType.newObject()
			.name("UpdateInfoType")
			.description("update info")
			.field(GraphQLFieldDefinition.newFieldDefinition()
				.name("updateCount")
				.type(Scalars.GraphQLInt));
		return builder.build();
	}
	
	/*
	?? public static final String PARAM_FIELDS = "fields";
	public static final String PARAM_ORDER = "order";
	public static final String PARAM_LIMIT = "limit";
	public static final String PARAM_OFFSET = "offset";
	
	public static final String[] FILTERS_UNIPARAM = { "feq", "fne", "fgt", "fge", "flt", "fle" };
	public static final String[] FILTERS_MULTIPARAM = { "fin", "fnin", "flk", "fnlk" };
	-- public static final String[] FILTERS_MULTIPARAM_STRONLY = { "flk", "fnlk" };
	public static final String[] FILTERS_BOOL = { "fnull", "fnotnull" };
	 */
	static final String[] REQ_PARAMS_INT = { RequestSpec.PARAM_LIMIT, RequestSpec.PARAM_OFFSET };
	static final String[] REQ_PARAMS_BOOL = { RequestSpec.PARAM_DISTINCT };
	// XXX PARAM_FIELDS, PARAM_ORDER
	
	GraphQLFieldDefinition createQueryField(GraphQLObjectType t, Relation r, DataFetcher<?> df) {
		//XXX listXXX, findXXX, allXXX
		String qFieldName = "list_"+t.getName();
		//.name("list"+capitalize(t.getName()))
		GraphQLFieldDefinition.Builder f = GraphQLFieldDefinition.newFieldDefinition()
			.name(qFieldName)
			.type(GraphQLList.list(t));
		amap.put(qFieldName, new QonAction(ActionType.SELECT, DBObjectType.RELATION, t.getName())); // XXX add schemaName? NamedTypedDBObject?

		for(String p: REQ_PARAMS_INT) {
			f.argument(GraphQLArgument.newArgument()
				.name(p)
				.type(Scalars.GraphQLInt));
		}
		// XXX: see RequestSpec.PROP_DISTINCT_ALLOW
		for(String p: REQ_PARAMS_BOOL) {
			f.argument(GraphQLArgument.newArgument()
				.name(p)
				.type(Scalars.GraphQLBoolean));
		}

		for(int i=0;i<r.getColumnCount(); i++) {

			if(addFiltersToQueryField) {
				String ctype = r.getColumnTypes().get(i);
				String cname = r.getColumnNames().get(i);
				addArgumentsToField(f, ctype, cname, true);
			}
			
			if(df!=null) { f.dataFetcher(df); }
		}
		
		return f.build();
	}
	
	GraphQLFieldDefinition createInsertField(GraphQLObjectType t, GraphQLObjectType returnType, Table r, DataFetcher<?> df) {
		String qFieldName = "insert_"+t.getName();
		GraphQLFieldDefinition.Builder f = GraphQLFieldDefinition.newFieldDefinition()
			.name(qFieldName)
			.type(returnType); //XXX return own record? type with updateCount (& own record)?
		amap.put(qFieldName, new QonAction(ActionType.INSERT, DBObjectType.RELATION, t.getName())); // XXX add schemaName? NamedTypedDBObject?

		//XXX: test shiro permission?

		for(int i=0;i<r.getColumnCount(); i++) {
			Column c = r.getColumns().get(i);
			String cname = c.getName();
			String ctype = c.getType();
			boolean generated = c.getAutoIncrement()!=null && c.getAutoIncrement();
			boolean required = !c.isNullable() && !generated;
			
			f.argument(GraphQLArgument.newArgument()
					.name(cname)
					.type( required ? new GraphQLNonNull(getGlType(ctype)) : getGlType(ctype) )
					);
			
			if(df!=null) { f.dataFetcher(df); }
		}
		
		return f.build();
	}
	
	void addArgumentsToField(GraphQLFieldDefinition.Builder f, String ctype, String cname, boolean inlcudeColNameInFilter) {
		if(!allowFilterOnType(ctype)) { return; }
		
		//XXX: see RequestSDpec.PROP_FILTERS_ALLOWED
		for(String p: RequestSpec.FILTERS_UNIPARAM) {
			f.argument(GraphQLArgument.newArgument()
				.name( p+(inlcudeColNameInFilter?"_" + cname:""))
				.type(getGlType(ctype)));
		}

		for(String p: RequestSpec.FILTERS_MULTIPARAM) {
			GraphQLScalarType type = getGlType(ctype);
			if(!Scalars.GraphQLString.equals(type) && Arrays.binarySearch(RequestSpec.FILTERS_MULTIPARAM_STRONLY_ORDERED, p)>=0) { continue; }
			f.argument(GraphQLArgument.newArgument()
				.name( p+(inlcudeColNameInFilter?"_" + cname:""))
				.type(GraphQLList.list(getGlType(ctype))));
		}
		
		for(String p: RequestSpec.FILTERS_BOOL) {
			f.argument(GraphQLArgument.newArgument()
				.name( p+(inlcudeColNameInFilter?"_" + cname:""))
				.type(Scalars.GraphQLBoolean));
				//.type(getGlType(ctype)));
			
		}
	}
	
	public GraphQLScalarType getGlType(String colType) {
		if(colType==null) { return Scalars.GraphQLString; }
		String upper = colType.toUpperCase();
		
		boolean isInt = DBUtil.INT_COL_TYPES_LIST.contains(upper);
		if(isInt) {
			return Scalars.GraphQLInt;
		}
		boolean isFloat = DBUtil.FLOAT_COL_TYPES_LIST.contains(upper);
		if(isFloat) {
			return Scalars.GraphQLFloat;
		}
		boolean isBoolean = DBUtil.BOOLEAN_COL_TYPES_LIST.contains(upper);
		if(isBoolean) {
			return Scalars.GraphQLBoolean;
		}
		/*
		boolean isBlob = DBUtil.BLOB_COL_TYPES_LIST.contains(upper);
		boolean isObject = DBUtil.OBJECT_COL_TYPES_LIST.contains(upper);
		*/
		//XXX: date?
		//log.info("unknown? "+upper);
		return Scalars.GraphQLString;
	}
	
	public static String capitalize(String s) {
		if(s==null) { return null; }
		if(s.length()<=1) { return s.toUpperCase(); }
		return s.substring(0,1).toUpperCase() + s.substring(1).toLowerCase();
	}
	
	boolean allowFilterOnType(String colType) {
		if(colType==null) { return true; }
		String upper = colType.toUpperCase();
		
		boolean isBlob = DBUtil.BLOB_COL_TYPES_LIST.contains(upper);
		if(isBlob) {
			return false;
		}
		boolean isObject = DBUtil.OBJECT_COL_TYPES_LIST.contains(upper);
		if(isObject) {
			return false;
		}
		return true;
	}
}
