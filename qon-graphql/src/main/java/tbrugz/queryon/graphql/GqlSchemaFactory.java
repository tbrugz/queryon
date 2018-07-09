package tbrugz.queryon.graphql;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.QueryOn.ActionType;
import tbrugz.queryon.util.DBUtil;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;

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
		GraphQLObjectType.Builder queryBuilder = GraphQLObjectType.newObject().name("QueryType");
		
		for(Relation t: sm.getTables()) {
			addRelation(queryBuilder, t, df);
		}
		for(Relation v: sm.getViews()) {
			addRelation(queryBuilder, v, df);
		}
		gqlSchemaBuilder.query(queryBuilder.build());
		
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
			// if(df!=null) { f.dataFetcher(df); } //XXX
			builder.field(f);
			
		}
		GraphQLObjectType gt = builder.build();
		queryBuilder.field(createQueryField(gt, r, df));
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
		GraphQLFieldDefinition.Builder f = GraphQLFieldDefinition.newFieldDefinition()
			//.name(t.getName())
			.name(qFieldName)
			//.name("list"+capitalize(t.getName()))
			.type(GraphQLList.list(t));
		amap.put(qFieldName, new QonAction(ActionType.SELECT, DBObjectType.RELATION, t.getName())); // XXX add schemaName? NamedTypedDBObject?

		for(String p: REQ_PARAMS_INT) {
			f.argument(GraphQLArgument.newArgument()
				.name(p)
				.type(Scalars.GraphQLInt));
		}
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
			/*
			for(String p: RequestSpec.FILTERS_UNIPARAM) {
				f.argument(GraphQLArgument.newArgument()
					.name(p+"_"+cname)
					.type(getGlType(ctype)));
			}
	
			for(String p: RequestSpec.FILTERS_MULTIPARAM) {
				f.argument(GraphQLArgument.newArgument()
					.name(p+"_"+cname)
					.type(GraphQLList.list(getGlType(ctype))));
			}
			
			for(String p: RequestSpec.FILTERS_BOOL) {
				f.argument(GraphQLArgument.newArgument()
					.name(p+"_"+cname)
					.type(getGlType(ctype)));
			}
			*/
		}
		
		return f.build();
	}
	
	void addArgumentsToField(GraphQLFieldDefinition.Builder f, String ctype, String cname, boolean inlcudeColNameInFilter) {
		if(!allowFilterOnType(ctype)) { return; }
		
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
