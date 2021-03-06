package tbrugz.queryon.graphql;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import graphql.GraphQLException;
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
import tbrugz.queryon.auth.LoginInfo;
import tbrugz.queryon.auth.UserInfo;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.dbmodel.Column;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.ExecutableParameter;
import tbrugz.sqldump.dbmodel.Query;
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
 * XXXdone: add "me" query (like /auth/info.jsp: authenticated, username, roles)
 */
public class GqlSchemaFactory { // GqlSchemaBuilder?
	
	static final Log log = LogFactory.getLog(GqlSchemaFactory.class);
	
	public static final String FILTER_KEY_PREPEND = "filter_by_";
	
	static final String TYPE_QUERY = "QueryType";
	static final String TYPE_MUTATION = "MutationType";
	static final String TYPE_UPDATE_INFO = "UpdateInfoType";
	static final String TYPE_EXECUTION_RETURN = "ExecuteReturnType";
	
	static final String FIELD_UPDATE_COUNT = "updateCount";
	static final String FIELD_RETURN_VALUE = "returnValue";
	
	static final String QUERY_CURRENTUSER = "currentUser";
	
	static final String MUTATION_LOGIN = "login";
	static final String MUTATION_LOGOUT = "logout";
	
	static final String[] queryBeansQueries = { QUERY_CURRENTUSER };
	static final Class<?>[] queryBeans = { UserInfo.class };
	static final String[] queryBeansRemarks = { "current user information" };

	static final List<String> mutationActions = new ArrayList<String>();
	static final List<Class<?>> mutationParamBeans = new ArrayList<Class<?>>();
	static final List<Class<?>> mutationReturnBeans = new ArrayList<Class<?>>();
	//XXX add mutationReturnBeansRemarks ?
	
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

	final boolean addFiltersToTypeField = true;
	final boolean addFiltersToQueryField = false;
	final boolean addMutations = true;
	final boolean useNamedParameters = true;

	final SchemaModel sm;
	final Map<String, QonAction> amap = new HashMap<>();
	final Map<String, Map<String, String>> colMap = new HashMap<>();
	transient Map<String, GraphQLObjectType> typeMap = new HashMap<>();
	
	public GqlSchemaFactory(SchemaModel sm) {
		this.sm = sm;
		if(GraphQlQonServlet.allowAuthentication) {
			// login
			mutationActions.add(MUTATION_LOGIN);
			mutationParamBeans.add(LoginInfo.class);
			mutationReturnBeans.add(UserInfo.class);

			//logout
			mutationActions.add(MUTATION_LOGOUT);
			mutationParamBeans.add(null);
			mutationReturnBeans.add(UserInfo.class);
		}
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
		GraphQLObjectType.Builder queryBuilder = GraphQLObjectType.newObject().name(TYPE_QUERY);
		
		for(Relation v: sm.getViews()) {
			addRelation(queryBuilder, v, df);
		}
		for(Relation t: sm.getTables()) {
			addRelation(queryBuilder, t, df);
		}
		for(int i=0;i<queryBeans.length;i++) {
			try {
				//XXX: distinct schema/db/model & bean datafetchers?
				addBean(queryBuilder, queryBeansQueries[i], queryBeans[i], queryBeansRemarks[i], df);
			} catch (IntrospectionException e) {
				log.warn("getSchema: addBean: "+e);
			}
		}
		gqlSchemaBuilder.query(queryBuilder.build());
		
		// INSERT/UPDATE/DELETE
		// https://graphql.org/learn/queries/#mutations
		// https://graphql.org/learn/schema/#the-query-and-mutation-types
		if(addMutations) {
			GraphQLObjectType.Builder mutationBuilder = GraphQLObjectType.newObject().name(TYPE_MUTATION);
			int mutationCount = 0;
			GraphQLObjectType updateType = getUpdateType(mutationBuilder);
			GraphQLObjectType executeType = getExecuteReturnType(mutationBuilder);
	
			for(Table t: sm.getTables()) {
				addMutation(mutationBuilder, t, updateType, df);
				mutationCount++;
			}
			for(ExecutableObject eo: sm.getExecutables()) {
				addMutation(mutationBuilder, eo, executeType, df);
				mutationCount++;
			}
			
			for(int i=0;i<mutationActions.size();i++) {
				try {
					//XXX: distinct schema/db/model & bean datafetchers?
					createBeanAction(mutationBuilder, mutationActions.get(i), mutationParamBeans.get(i), mutationReturnBeans.get(i), null, df);
					mutationCount++;
				} catch (IntrospectionException e) {
					log.warn("IntrospectionException:: getSchema: createBeanAction: "+e);
				}
			}
			
			if(mutationCount>0) {
				//add UpdateInfo type
				gqlSchemaBuilder.mutation(mutationBuilder.build());
			}
		}
		
		// XXXxx each type should have a query? -> listXxx, listSchemaName
		// https://github.com/okgrow/merge-graphql-schemas#merging-type-definitions

		return gqlSchemaBuilder.build();
	}
	
	void addRelation(GraphQLObjectType.Builder queryBuilder, Relation r, DataFetcher<?> df) {
		String name = normalizeName(r.getName());
		if(r.getColumnCount()==0) {
			log.debug("Relation '"+name+"' has no fields [ignoring]");
			return;
		}
		GraphQLObjectType.Builder builder = GraphQLObjectType.newObject()
				//.name(t.getQualifiedName())
				.name(name);
		try {
			
		Map<String, String> relColMap = new HashMap<>();
		colMap.put(name, relColMap);
		
		if(r.getRemarks()!=null) {
			builder.description(r.getRemarks());
		}
		for(int i=0;i<r.getColumnCount();i++) {
			String cName = normalizeName( r.getColumnNames().get(i) );
			String cType = r.getColumnTypes().get(i);
			relColMap.put(cName, r.getColumnNames().get(i));
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
		typeMap.put(name, gt);
		queryBuilder.field(createQueryField(gt, r, df));
		
		}
		catch(GraphQLException e) {
			log.warn("GraphQLException: "+e);
		}
	}
	
	void addBean(GraphQLObjectType.Builder queryBuilder, String queryName, Class<?> clazz, String remarks, DataFetcher<?> df) throws IntrospectionException {
		//log.info("addBean: "+name+" ; "+clazz.getSimpleName());
		
		try {
			String name = normalizeClassName(clazz);
			GraphQLObjectType gt = typeMap.get(name);
			if(gt==null) {
				gt = createBeanType(name, clazz, remarks);
			}
			
			typeMap.put(name, gt);
			queryBuilder.field(createQueryField(gt, queryName, df));
		}
		catch(GraphQLException e) {
			log.warn("GraphQLException: "+e);
		}
	}

	GraphQLObjectType createBeanType(String name, Class<?> clazz, String remarks) throws IntrospectionException {
		//log.info("createBeanType: "+name+" ; "+clazz.getSimpleName());
		GraphQLObjectType.Builder builder = GraphQLObjectType.newObject()
				.name(name);
		
		try {
			if(remarks!=null) {
				builder.description(remarks);
			}
			BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
			PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
			
			for(int i=0;i<pds.length;i++) {
				String cName = normalizeName( pds[i].getName() );
				if("class".equals(cName)) { continue; }
				GraphQLScalarType glType = getGlType(pds[i].getReadMethod().getReturnType());
				GraphQLFieldDefinition.Builder f = GraphQLFieldDefinition.newFieldDefinition()
						.name(cName)
						.type(glType);
				builder.field(f);
			}
			
			GraphQLObjectType gt = builder.build();
			return gt;
		}
		catch(GraphQLException e) {
			log.warn("GraphQLException: "+e);
			return null;
		}
	}
	
	void createBeanAction(GraphQLObjectType.Builder gqlBuilder, String actionName, Class<?> paramClazz, Class<?> returnClazz, String remarks, DataFetcher<?> df) throws IntrospectionException {
		try {
			String retName = normalizeClassName(returnClazz);
			GraphQLObjectType retT = typeMap.get(retName);
			if(retT==null) {
				retT = createBeanType(retName, returnClazz, remarks);
			}
			
			GraphQLFieldDefinition.Builder f = GraphQLFieldDefinition.newFieldDefinition()
					.name(actionName)
					.type(retT);
			
			if(paramClazz!=null) {
				BeanInfo beanInfo = Introspector.getBeanInfo(paramClazz);
				PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
				
				for(int i=0;i<pds.length;i++) {
					String cName = normalizeName( pds[i].getName() );
					if("class".equals(cName)) { continue; }
					GraphQLScalarType glType = getGlType(pds[i].getReadMethod().getReturnType());
					
					f.argument(GraphQLArgument.newArgument()
							.name(cName)
							.type(glType)
							);
				}
			}
			
			if(df!=null) { f.dataFetcher(df); }
			
			GraphQLFieldDefinition gfd = f.build();
			gqlBuilder.field(gfd);
			
			amap.put(actionName, new QonAction(ActionType.EXECUTE, DBObjectType.EXECUTABLE, gfd.getName()));
		}
		catch(GraphQLException e) {
			log.warn("GraphQLException: "+e);
		}
	}
	
	void addMutation(GraphQLObjectType.Builder mutationBuilder, Table t, GraphQLObjectType returnType, DataFetcher<?> df) {
		String name = normalizeName(t.getName());
		GraphQLObjectType gt = typeMap.get(name);
		if(gt==null) {
			log.warn("addMutation: object "+name+" not found in typeMap");
			return;
		}
		mutationBuilder.field(createInsertField(gt, returnType, t, df));
		GraphQLFieldDefinition updateField = createUpdateField(gt, returnType, t, df);
		if(updateField!=null) {
			mutationBuilder.field(updateField);
		}
		GraphQLFieldDefinition deleteField = createDeleteField(gt, returnType, t, df);
		if(deleteField!=null) {
			mutationBuilder.field(deleteField);
		}
	}

	void addMutation(GraphQLObjectType.Builder mutationBuilder, ExecutableObject eo, GraphQLObjectType returnType, DataFetcher<?> df) {
		//typeMap.put(eo.getName(), gt);
		//GraphQLObjectType gt = typeMap.get(eo.getName());
		
		mutationBuilder.field(createExecuteField(returnType, eo, df));
	}
	
	GraphQLObjectType getUpdateType(GraphQLObjectType.Builder mutationBuilder) {
		GraphQLObjectType.Builder builder = GraphQLObjectType.newObject()
			.name(TYPE_UPDATE_INFO)
			.description("update info")
			.field(GraphQLFieldDefinition.newFieldDefinition()
				.name(FIELD_UPDATE_COUNT)
				.type(Scalars.GraphQLInt));
		return builder.build();
	}

	GraphQLObjectType getExecuteReturnType(GraphQLObjectType.Builder mutationBuilder) {
		GraphQLObjectType.Builder builder = GraphQLObjectType.newObject()
			.name(TYPE_EXECUTION_RETURN)
			.description("execution return")
			.field(GraphQLFieldDefinition.newFieldDefinition()
				.name(FIELD_RETURN_VALUE)
				.type(Scalars.GraphQLString));
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

		if(r.getParameterCount()!=null) {
			if(useNamedParameters && r instanceof Query && ((Query)r).getNamedParameterNames()!=null) {
				Query q = (Query) r;
				List<String> namedParameters = SchemaModelUtils.getUniqueNamedParameterNames(q);
				List<String> namedParameterTypes = SchemaModelUtils.getUniqueNamedParameterTypes(q);
				for(int i=0;i <namedParameters.size() ;i++) {
					GraphQLScalarType type = getGlType(
							(namedParameterTypes!=null && namedParameterTypes.size()>i)?namedParameterTypes.get(i):"VARCHAR"
							);
					f.argument(GraphQLArgument.newArgument()
							.name(namedParameters.get(i))
							.type(new GraphQLNonNull(type))
							);
				}
			}
			else {
				for(int i=0;i<r.getParameterCount();i++) {
					GraphQLScalarType type = getGlType(
							(r.getParameterTypes()!=null && r.getParameterTypes().size()>i)?r.getParameterTypes().get(i):"VARCHAR"
							);
					f.argument(GraphQLArgument.newArgument()
							.name("p"+(i+1))
							.type(new GraphQLNonNull(type))
							);
				}
			}
		}
		
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

	GraphQLFieldDefinition createQueryField(GraphQLObjectType t, String queryFieldName, DataFetcher<?> df) {
		//String qFieldName = queryFieldName!=null ? queryFieldName : "get_"+t.getName();
		GraphQLFieldDefinition.Builder f = GraphQLFieldDefinition.newFieldDefinition()
			.name(queryFieldName)
			//.type(GraphQLList.list(t));
			.type(t);
		amap.put(queryFieldName, new QonAction(ActionType.SELECT, DBObjectType.RELATION, t.getName()));

		if(df!=null) { f.dataFetcher(df); }
		
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
			
		}

		if(df!=null) { f.dataFetcher(df); }
		
		return f.build();
	}

	GraphQLFieldDefinition createUpdateField(GraphQLObjectType t, GraphQLObjectType returnType, Table r, DataFetcher<?> df) {
		String qFieldName = "update_"+t.getName();
		GraphQLFieldDefinition.Builder f = GraphQLFieldDefinition.newFieldDefinition()
			.name(qFieldName)
			.type(returnType); //XXX return own record? type with updateCount (& own record)?
		amap.put(qFieldName, new QonAction(ActionType.UPDATE, DBObjectType.RELATION, t.getName())); // XXX add schemaName? NamedTypedDBObject?
		
		Constraint pk = SchemaModelUtils.getPK(r);
		if(pk==null) { return null; }

		//XXX: test shiro permission?

		for(int i=0;i<r.getColumnCount(); i++) {
			Column c = r.getColumns().get(i);
			String cname = normalizeName( c.getName() );
			String ctype = c.getType();
			//boolean generated = c.getAutoIncrement()!=null && c.getAutoIncrement();
			//boolean required = !c.isNullable() && !generated;
			
			f.argument(GraphQLArgument.newArgument()
					.name(cname)
					.type(getGlType(ctype))
					);
			
		}
		
		for(String col: pk.getUniqueColumns()) {
			f.argument(GraphQLArgument.newArgument()
				.name(FILTER_KEY_PREPEND+col)
				.type(new GraphQLNonNull(Scalars.GraphQLString)) //XXX: non-string col type?
				);
		}

		if(df!=null) { f.dataFetcher(df); }
		
		return f.build();
	}

	GraphQLFieldDefinition createDeleteField(GraphQLObjectType t, GraphQLObjectType returnType, Table r, DataFetcher<?> df) {
		String qFieldName = "delete_"+t.getName();
		GraphQLFieldDefinition.Builder f = GraphQLFieldDefinition.newFieldDefinition()
			.name(qFieldName)
			.type(returnType);
		amap.put(qFieldName, new QonAction(ActionType.DELETE, DBObjectType.RELATION, t.getName())); // XXX add schemaName? NamedTypedDBObject?
		
		Constraint pk = SchemaModelUtils.getPK(r);
		if(pk==null) { return null; }

		//XXX: test shiro permission?

		for(String col: pk.getUniqueColumns()) {
			f.argument(GraphQLArgument.newArgument()
				.name(FILTER_KEY_PREPEND+col)
				.type(new GraphQLNonNull(Scalars.GraphQLString)) //XXX: non-string col type?
				);
		}
		
		if(df!=null) { f.dataFetcher(df); }
		
		return f.build();
	}
	
	GraphQLFieldDefinition createExecuteField(GraphQLObjectType returnType, ExecutableObject eo, DataFetcher<?> df) {
		String qFieldName = "execute_"+normalizeName(eo.getName());
		GraphQLFieldDefinition.Builder f = GraphQLFieldDefinition.newFieldDefinition()
			.name(qFieldName)
			.type(returnType);
		amap.put(qFieldName, new QonAction(ActionType.EXECUTE, /*DBObjectType.EXECUTABLE*/ eo.getType(), eo.getName())); // XXX add schemaName? NamedTypedDBObject?
		
		//XXX: test shiro permission?
		
		for(int i=0;i<eo.getParams().size(); i++) {
			ExecutableParameter ep = eo.getParams().get(i);
			if(!SchemaModelUtils.isInParameter(ep)) { continue; }
			
			//String cname = ep.getName()!=null?ep.getName():"p"+(i+1);
			String cname = "p"+(i+1);
			String ctype = ep.getDataType();
			
			f.argument(GraphQLArgument.newArgument()
					.name(cname)
					.type(getGlType(ctype))
					);
		}
		
		// out type: struct? must refactor QueryOn...
		
		if(df!=null) { f.dataFetcher(df); }
		
		return f.build();
	}
	
	/*
	GraphQLObjectType getWhereSubtype(GraphQLObjectType.Builder mutationBuilder, Constraint pk) {
		GraphQLObjectType.Builder builder = GraphQLObjectType.newObject()
			.name("WhereType")
			.description("where info");
		for(String col: pk.getUniqueColumns()) {
			builder.field(
				GraphQLFieldDefinition.newFieldDefinition()
				.name(col)
				.type(Scalars.GraphQLString) //XXX: non-string col type?
				);
		}
		return builder.build();
	}
	*/
	
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

	public GraphQLScalarType getGlType(Class<?> clazz) {
		if(clazz==null) { return Scalars.GraphQLString; }
		
		boolean isInt = clazz.isAssignableFrom(Integer.TYPE) || clazz.isAssignableFrom(Integer.class);
		if(isInt) {
			return Scalars.GraphQLInt;
		}
		boolean isFloat = clazz.isAssignableFrom(Float.TYPE) || clazz.isAssignableFrom(Float.class) || clazz.isAssignableFrom(Double.TYPE) || clazz.isAssignableFrom(Double.class);
		if(isFloat) {
			return Scalars.GraphQLFloat;
		}
		boolean isBoolean = clazz.isAssignableFrom(Boolean.TYPE) || clazz.isAssignableFrom(Boolean.class);
		if(isBoolean) {
			return Scalars.GraphQLBoolean;
		}
		/*
		isBlob? isObject? date?
		*/
		//log.info("unknown class? "+clazz);
		return Scalars.GraphQLString;
	}
	
	// see: https://stackoverflow.com/questions/3322152/is-there-a-way-to-get-rid-of-accents-and-convert-a-whole-string-to-regular-lette
	static String normalizeName(String name) {
		if(name==null || name.length()==0) { return ""; }
		
		String ret = Normalizer.normalize(name, Normalizer.Form.NFD);
		ret = ret.replaceAll("[^\\p{ASCII}]", "");
		ret = ret.replaceAll("[^_0-9A-Za-z]", "_");
		char char1 = ret.charAt(0);
		if(char1 >= '0' && char1 <='9') { ret = "_"+ret; }
		/*if(log.isDebugEnabled() && ! name.equals(ret)) {
			log.debug("name normalized: '"+name+"' -> '"+ret+"'");
			//log.info("name normalized: '"+name+"' -> '"+ret+"'"+" [norm1="+Normalizer.normalize(name, Normalizer.Form.NFD)+"]");
		}*/
		return ret;
	}
	
	static String normalizeClassName(Class<?> clazz) {
		return normalizeName(clazz.getSimpleName());
	}
	
	static boolean isNormalizedName(String name) {
		String normal = normalizeName(name);
		return normal.equals(name);
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
