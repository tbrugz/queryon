package tbrugz.queryon;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.Part;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.QueryOn.LimitOffsetStrategy;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.exception.NotFoundException;
import tbrugz.queryon.model.QonRelation;
import tbrugz.queryon.model.QonTable;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.queryon.util.MiscUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.datadump.DumpSyntax;
import tbrugz.sqldump.datadump.DumpSyntaxInt;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.ParametrizedDBObject;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.sqlrun.tokenzr.TokenizerUtil;
import tbrugz.sqldump.sqlrun.tokenzr.TokenizerUtil.QueryParameter;
import tbrugz.sqldump.util.IOUtil;
import tbrugz.sqldump.util.ParametrizedProperties;
import tbrugz.sqldump.util.StringDecorator;
import tbrugz.sqldump.util.Utils;

public class SQL {

	static final Log log = LogFactory.getLog(SQL.class);
	
	public static final String PARAM_WHERE_CLAUSE = "$where_clause";
	public static final String PARAM_FILTER_CLAUSE = "$filter_clause";
	public static final String PARAM_PROJECTION_CLAUSE = "$projection_clause";
	public static final String PARAM_ORDER_CLAUSE = "$order_clause";

	public static final String PARAM_UPDATE_SET_CLAUSE = "[update-set-clause]";
	public static final String PARAM_INSERT_COLUMNS_CLAUSE = "[insert-columns-clause]";
	public static final String PARAM_INSERT_VALUES_CLAUSE = "[insert-values-clause]";
	public static final String PARAM_INSERT_VALUES_AND_COLUMNS_CLAUSE = "[insert-values-and-columns-clause]";
	
	public static final String VARIABLE_USERNAME = "$username";
	//public static final String VARIABLE_USERROLES = "$userroles";
	public static final String EMPTY_USERNAME = "''";
	
	//XXX add limit/offset-clause?

	public enum FilteredInsertStrategy {
		BASE,
		VALUES,
		FROM_DUAL,
		;

		static final String PROPFILE_DBMS_SPECIFIC = "/dbms-specific-queryon.properties";
		static final ParametrizedProperties prop = new ParametrizedProperties();
		static {
			try {
				prop.load(IOUtil.getResourceAsStream(PROPFILE_DBMS_SPECIFIC));
			} catch (IOException e) {
				log.warn("FilteredInsertStrategy: "+e);
				throw new ExceptionInInitializerError(e);
			}
		}
		
		public static FilteredInsertStrategy getStrategy(String dbid) {
			String strategyStr = prop.getProperty("dbid."+dbid+".insert-with-filter-strategy");
			log.debug("getStrategy["+dbid+"]: "+strategyStr);
			FilteredInsertStrategy strat = FilteredInsertStrategy.valueOf(strategyStr);
			return strat;
		}

		public String createInsert(Relation relation) {
			String sql = null;
			String relationName = (SQL.valid(relation.getSchemaName())?sqlIdDecorator.get(relation.getSchemaName())+".":"") + sqlIdDecorator.get(relation.getName());

			switch(this) {
				case BASE:
					sql = "insert into " + relationName +
						" (" + PARAM_INSERT_COLUMNS_CLAUSE + ")\n" +
						"select " + PARAM_INSERT_COLUMNS_CLAUSE + " from (\n" +
						"	select " + PARAM_INSERT_VALUES_AND_COLUMNS_CLAUSE + "\n" +
						") qon_select\n" +
						PARAM_WHERE_CLAUSE;
					break;
				case VALUES:
					sql = "insert into " + relationName +
						" (" + PARAM_INSERT_COLUMNS_CLAUSE + ")\n" +
						"select " + PARAM_INSERT_COLUMNS_CLAUSE + " from (\n" +
						"	values (" + PARAM_INSERT_VALUES_CLAUSE + ")\n" +
						") qon_select (" + PARAM_INSERT_COLUMNS_CLAUSE + ")\n" +
						PARAM_WHERE_CLAUSE;
					break;
				case FROM_DUAL:
					sql = "insert into " + relationName +
						" (" + PARAM_INSERT_COLUMNS_CLAUSE + ")\n" +
						"select " + PARAM_INSERT_COLUMNS_CLAUSE + " from (\n" +
						"	select " + PARAM_INSERT_VALUES_AND_COLUMNS_CLAUSE + "\n" +
						"	from dual\n" +
						") qon_select\n" +
						PARAM_WHERE_CLAUSE;
					break;
			}
			return sql;
		}

	}

	public static StringDecorator sqlIdDecorator = new StringDecorator.StringQuoterDecorator(quoteString()); //FIXME: StringDecorator should not be static
	
	public static final List<DateFormat> dateFormats = new ArrayList<DateFormat>();
	
	static boolean validateOrderColumnNames = true; //TODO: should not be static
	
	final String initialSql;
	String sql;
	public boolean sqlLoEncapsulated = false;
	
	final Relation relation;
	final boolean allowEncapsulation;
	final int originalBindParameterCount;
	final Integer limit;
	final Integer limitMax;
	final String username;
	//XXX final String userroles;
	final List<String> namedParameters;
	final boolean[] bindNullOnMissingParameters;
	
	static DBMSFeatures features = null; //FIXME: DBMSFeatures should not be static
	
	Integer applyedLimit;
	//Integer applyedOffset; //XXX add applyedOffset?
	boolean orderByApplyed = false;
	final List<Object> bindParameterValues = new ArrayList<Object>(); // XXX private?
	//XXX add 'final String initialSql;'?
	
	protected SQL(String sql, Relation relation, Integer originalBindParameterCount, Integer reqspecLimit, String username) {
		this(sql, relation, originalBindParameterCount, reqspecLimit, username, true);
	}
	
	protected SQL(String sql, Relation relation, Integer originalBindParameterCount, Integer reqspecLimit, String username, boolean validate) {
		this.initialSql = sql;
		this.sql = sql;
		this.relation = relation;
		//this.allowEncapsulation = processPatternBoolean(sql, allowEncapsulationBooleanPattern, true);
		this.allowEncapsulation = allowEncapsulation(sql);
		Integer limitDefault = processPatternInteger(sql, limitDefaultIntPattern);
		this.limitMax = processPatternInteger(sql, limitMaxIntPattern);
		int bindParameterCountTmp = originalBindParameterCount != null ? originalBindParameterCount : 0;
		
		Integer limit = limitDefault!=null ? limitDefault : null;
		limit = reqspecLimit!=null ? reqspecLimit : limit;
		//if(limitMax!=null && limit!=null) { limit = Math.min(limitMax, limit); }
		//else if(limitMax!=null) { limit = limitMax; }
		//limit = limitMax!=null ? (limit!=null ? Math.min(limitMax, limit) : limitMax) : limit;
		/*		(limitMax!=null && reqspecLimit!=null) ?
				(limitMax < reqspecLimit ? limitMax : reqspecLimit) :
				(limitMax!=null ? limitMax : reqspecLimit);*/
		this.limit = limit;
		//log.info("limit="+limit+" ; limitDefault="+limitDefault+" ; reqspecLimit="+reqspecLimit+" ; limitMax="+limitMax);
		
		this.username = getFinalVariableValue(username);
		List<String> namedParametersTmp = getNamedParameterNames(sql);
		if(namedParametersTmp==null) {
			List<QueryParameter> qpl = TokenizerUtil.getNamedParameters(sql);
			if(qpl.size()>0) {
				namedParametersTmp = TokenizerUtil.getParameterNames(qpl);
				this.sql = TokenizerUtil.replaceNamedParameters(sql, qpl);
			}
		}
		this.namedParameters = namedParametersTmp;
		
		/*
		//log.info("validate == "+validate+" ; bindParameterCountTmp == "+bindParameterCountTmp+" ; this.namedParameters == "+this.namedParameters+" ; bindNullOnMissingParameters() == "+Arrays.toString(bindNullOnMissingParameters()));
		if(validate) {
			this.originalBindParameterCount = bindParameterCountTmp;
			validateNamedParametersWithParamCount(namedParameters, this.originalBindParameterCount);
			//this.bindNullOnMissingParameters = MiscUtils.expandBooleanArray(bindNullOnMissingParameters(), this.originalBindParameterCount);
			//log.info("bindNullOnMissingParameters == "+Arrays.toString(bindNullOnMissingParameters));
		}
		else {
			if(originalBindParameterCount == null && this.namedParameters!=null && this.namedParameters.size()>0) {
				this.originalBindParameterCount = this.namedParameters.size();
			}
			else {
				this.originalBindParameterCount = bindParameterCountTmp;
			}
		}
		*/
		if(originalBindParameterCount == null && this.namedParameters!=null && this.namedParameters.size()>0) {
			this.originalBindParameterCount = this.namedParameters.size();
		}
		else {
			this.originalBindParameterCount = bindParameterCountTmp;
		}
		this.bindNullOnMissingParameters = MiscUtils.expandBooleanArray(bindNullOnMissingParameters(), this.originalBindParameterCount);

		// default columns
		List<String> defaultColsTmp = getDefaultColumns(sql);
		if(defaultColsTmp!=null) {
			if(relation instanceof QonRelation) {
				QonRelation qr = (QonRelation) relation;
				//List<String> colNames = qr.getColumnNames();
				List<String> rdcn = qr.getDefaultColumnNames();
				if(rdcn==null || rdcn.size()==0) {
					if(rdcn==null) {
						qr.setDefaultColumnNames(new ArrayList<String>());
					}
					//log.debug("adding default columns: "+defaultColsTmp);
					for(String s: defaultColsTmp) {
						qr.getDefaultColumnNames().add(s);
					}
				}
			}
			else {
				String message = "relation "+relation+" is not of type QonRelation";
				log.warn(message); //, new RuntimeException(message));
			}
		}
		
		if(validate) {
			validateNamedParametersWithParamCount(namedParameters, this.originalBindParameterCount);
			if(bindNullOnMissingParameters != null &&
					bindNullOnMissingParameters.length != this.originalBindParameterCount) {
				String message = "'"+PTRN_BIND_NULL_ON_MISSING_PARAMS+"' count [#"+bindNullOnMissingParameters.length+"] should be equal to 1 or bind parameters count [#"+this.originalBindParameterCount+"]";
				log.warn(message);
				throw new BadRequestException(message);
			}
		}
	}

	protected SQL(String sql, Relation relation, Integer originalBindParameterCount, Integer reqspecLimit) {
		this(sql, relation, originalBindParameterCount, reqspecLimit, null);
	}
	
	protected SQL(String sql, Relation relation) {
		this(sql, relation, null, null);
	}
	
	public String getSql() {
		return sql;
	}
	
	public String getFinalSql() {
		return getFinalSql(sql, username);
	}
	
	public static String getFinalSqlNoUsername(String sql) {
		return getFinalSql(sql, null);
	}
	
	private static String getFinalSql(String sql, String username) {
		if(username==null) {
			username = EMPTY_USERNAME;
		}

		// parameters already replaced in constructor...
		//sql = TokenizerUtil.replaceNamedParameters(sql);

		//log.info("getFinalSql: "+username);
		return replaceClauses(sql, username);
	}

	public String getInitialSql() {
		return replaceClauses(initialSql, username);
	}
	
	public String getSqlWithNamedParameters() {
		return replaceClauses(sql, EMPTY_USERNAME);
	}

	public static String getSqlWithNamedParameters(String sql) {
		return replaceClauses(sql, EMPTY_USERNAME);
	}
	
	private static String replaceClauses(String sql, String username) {
		return sql.replace(PARAM_PROJECTION_CLAUSE, "*")
				.replace(PARAM_WHERE_CLAUSE, "").replace(PARAM_FILTER_CLAUSE, "").replace(PARAM_ORDER_CLAUSE, "")
				.replace(VARIABLE_USERNAME, username);
	}

	public static String replaceVariablesWithEmptyValues(String sql) {
		return sql.replace(VARIABLE_USERNAME, EMPTY_USERNAME);
	}

	private static String createSQLstr(Relation table, RequestSpec reqspec) {
		String sql = "select "+PARAM_PROJECTION_CLAUSE+
			" from " + (SQL.valid(table.getSchemaName())?sqlIdDecorator.get(table.getSchemaName())+".":"") + sqlIdDecorator.get(table.getName())+
			" " + PARAM_WHERE_CLAUSE+
			" " + PARAM_ORDER_CLAUSE;
		return sql;
	}
	
	public static SQL createSQL(Relation relation, RequestSpec reqspec, String username) {
		return createSQL(relation, reqspec, username, true);
	}
	
	public static SQL createSQL(Relation relation, RequestSpec reqspec, String username, boolean validateParameters) {
		if(relation instanceof Query) { //class Query is subclass of View, so this test must come first
			//XXX: other query builder strategy besides [where-clause]? contains 'cursor'?
			Query q = (Query) relation;
			SQL sql = new SQL( q.getQuery() , relation, q.getParameterCount(), reqspec!=null?reqspec.limit:null, username, validateParameters);
			//processSqlXtraMetadata(sql);
			//sql.originalBindParameterCount = q.getParameterCount();
			return sql;
		}
		else if(relation instanceof Table) {
			SQL sql = new SQL(createSQLstr(relation, reqspec), relation, null, reqspec!=null?reqspec.limit:null);
			/*if(relation instanceof QonTable) {
				QonTable qt = (QonTable) relation;
				if(qt.hasSqlFilter()) {
					sql.addFilter(qt.getSqlFilter());
				}
			}*/
			return sql;
		}
		else if(relation instanceof View) {
			return new SQL(createSQLstr(relation, reqspec), relation, null, reqspec!=null?reqspec.limit:null);
		}
		throw new IllegalArgumentException("unknown relation type: "+relation.getClass().getName());
		
		//return new SQL(createSQLstr(relation, reqspec), relation);
	}

	/*public static SQL createSQL(Relation relation, RequestSpec reqspec) {
		return createSQL(relation, reqspec, null);
	}*/
	
	public static SQL createInsertSQL(Relation relation, String dialect) {
		String sql = null;
		if(hasSqlFilter(relation)) {
			FilteredInsertStrategy fis = FilteredInsertStrategy.getStrategy(dialect);
			sql = fis.createInsert(relation);
		}
		else {
			sql = "insert into "+
				(SQL.valid(relation.getSchemaName())?sqlIdDecorator.get(relation.getSchemaName())+".":"") + sqlIdDecorator.get(relation.getName())+
				" (" + PARAM_INSERT_COLUMNS_CLAUSE + ")" +
				" values (" + PARAM_INSERT_VALUES_CLAUSE+")";
		}
		return new SQL(sql, relation);
	}

	public static SQL createUpdateSQL(Relation relation) {
		String sql = "update "+
				(SQL.valid(relation.getSchemaName())?sqlIdDecorator.get(relation.getSchemaName())+".":"") + sqlIdDecorator.get(relation.getName())+
				" set " + PARAM_UPDATE_SET_CLAUSE +
				" " + PARAM_WHERE_CLAUSE;
		return new SQL(sql, relation);
	}

	public static SQL createDeleteSQL(Relation relation) {
		String sql = "delete "+
				"from " + (SQL.valid(relation.getSchemaName())?relation.getSchemaName()+".":"") + relation.getName()+
				" " + PARAM_WHERE_CLAUSE;
		return new SQL(sql, relation);
	}
	
	public static String createExecuteSQLstr(ExecutableObject eo) {
		StringBuilder sql = new StringBuilder();
		sql.append("{ "); //sql.append("begin ");
		if(eo.getType()==DBObjectType.FUNCTION) {
			sql.append("?= "); //sql.append("? := ");
		}
		sql.append("call ");
		sql.append(
			(!MiscUtils.isNullOrEmpty(eo.getSchemaName())?eo.getSchemaName()+".":"")+
			(!MiscUtils.isNullOrEmpty(eo.getPackageName())?eo.getPackageName()+".":"")+
			eo.getName());
		int paramArgCount = eo.getParams()!=null ? eo.getParams().size() : 0;
		if(paramArgCount>0) {
			sql.append("(");
			for(int i=0;i<paramArgCount;i++) {
				sql.append((i>0?", ":"")+"?");
			}
			sql.append(")");
		}
		sql.append(" }"); //sql.append("; end;");
		return sql.toString();
	}

	public static String createExecuteSqlFromBody(ExecutableObject eo, String username) {
		//if(eo==null) { return null; }
		String body = eo.getBody();
		if(body==null) {
			return null;
		}
		return body.replace(VARIABLE_USERNAME, getFinalVariableValue(username));
	}
	
	public static SQL createSelectCountSQL(Relation relation) {
		String sql = "select count(*) as counter from "+
				(SQL.valid(relation.getSchemaName())?sqlIdDecorator.get(relation.getSchemaName())+".":"") + sqlIdDecorator.get(relation.getName())+
				" " + PARAM_WHERE_CLAUSE;
		return new SQL(sql, relation);
	}

	public void addFilter(String filter) {
		if(filter==null || filter.length()==0) { return; }
		
		if(sql.contains(PARAM_WHERE_CLAUSE)) {
			sql = sql.replace(PARAM_WHERE_CLAUSE, "where "+filter+" "+PARAM_FILTER_CLAUSE);
		}
		else if(sql.contains(PARAM_FILTER_CLAUSE)) {
			sql = sql.replace(PARAM_FILTER_CLAUSE, "and "+filter+" "+PARAM_FILTER_CLAUSE);
		}
		else {
			addEncapsulatingFilter(filter, true);
		}
	}

	public void addEncapsulatingFilter(String filter, boolean addFilterClause) {
		//if(relation!=null && relation instanceof Query) {
			/*if(! allowEncapsulation) {
				throw new BadRequestException("filter not allowed in query "+relation.getName());
			}*/
		sql = "select * from (\n"+sql+"\n) qon_filter";
		//}
		sql += " where "+filter+" "+(addFilterClause?PARAM_FILTER_CLAUSE:"");
	}
	
	private void addProjection(String columns) {
		/*if(! allowEncapsulation) {
			throw new BadRequestException("projection not allowed in query "+relation.getName());
		}*/

		String sqlFilter = "";
		if(!sql.contains(PARAM_WHERE_CLAUSE) && !sql.contains(PARAM_FILTER_CLAUSE)) {
			sqlFilter = " " + PARAM_WHERE_CLAUSE;
		}
		String sqlOrder = "";
		if(!sql.contains(PARAM_ORDER_CLAUSE)) {
			sqlOrder = " " + PARAM_ORDER_CLAUSE;
		}
		sql = "select "+columns+" from (\n"+sql+"\n) qon_projection "+sqlFilter+sqlOrder;
	}
	
	// XXX: should be private?
	protected void addCount() {
		sql = "select count(*) as count from (\n"+sql+"\n) qon_count ";
	}
	
	public void applyCount(RequestSpec reqspec) {
		if(!reqspec.count) { return; }
		addCount();
	}
	
	public void applyOrder(RequestSpec reqspec) {
		//TODOne: validate columns
		if(reqspec.orderCols.size()==0) return;
		
		List<String> relationCols = null;
		if(hasGroupByOrAggregate(reqspec)) {
			relationCols = reqspec.groupby;
		}
		else if(relation!=null) {
			relationCols = relation.getColumnNames();
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("order by ");
		for(int i=0;i<reqspec.orderCols.size();i++) {
			String col = reqspec.orderCols.get(i);
			String ascDesc = reqspec.orderAscDesc.get(i);
			if(col==null || col.equals("")) { continue; }
			//log.debug("order by '"+col+"': cols: "+relationCols);
			//XXXdone option to ignore unknown columns? or to not validate?
			if(validateOrderColumnNames && relationCols!=null && !MiscUtils.containsIgnoreCase(relationCols, col)) {
				throw new BadRequestException("can't order by '"+col+"' (unknown column)");
			}
			
			sb.append((i==0?"":", ")+sqlIdDecorator.get(col)+" "+ascDesc);
		}
		
		if(sql.contains(PARAM_ORDER_CLAUSE)) {
			sql = sql.replace(PARAM_ORDER_CLAUSE, sb.toString());
		}
		else {
			sql = "select * from (\n"+sql+"\n) qon_order "+sb.toString();
		}
		orderByApplyed = true;
	}

	public void applyUpdate(String set) {
		sql = sql.replace(PARAM_UPDATE_SET_CLAUSE, set);
	}

	@Deprecated
	public void applyInsert(String cols, String values) {
		sql = sql.replace(PARAM_INSERT_COLUMNS_CLAUSE, cols).replace(PARAM_INSERT_VALUES_CLAUSE, values);
	}

	public void applyInsert(List<String> allColumnNames, List<String> columnNames, List<String> colTypes) {
		List<String> defaultColumns = new ArrayList<>();
		defaultColumns.addAll(allColumnNames);
		defaultColumns.removeAll(columnNames);
		String colsStr = Utils.join(columnNames, ", ");
		List<String> valuesAndCols = new ArrayList<>();
		List<String> values = new ArrayList<>();
		for(int i=0;i<columnNames.size();i++) {
			String c = columnNames.get(i);
			String ct = colTypes.get(i);
			valuesAndCols.add("cast(? as "+ct+") as "+c);
			values.add("?");
		}
		List<String> defaultValuesAndCols = new ArrayList<>();
		for(int i=0;i<defaultColumns.size();i++) {
			defaultValuesAndCols.add("null as "+defaultColumns.get(i));
		}
		valuesAndCols.addAll(defaultValuesAndCols);
		String valuesAndColsStr = Utils.join(valuesAndCols, ", ");
		String valuesStr = Utils.join(values, ", ");
		//XXX replace 'valuesAndColsStr'
		sql = sql.replace(PARAM_INSERT_COLUMNS_CLAUSE, colsStr)
			.replace(PARAM_INSERT_VALUES_AND_COLUMNS_CLAUSE, valuesAndColsStr)
			.replace(PARAM_INSERT_VALUES_CLAUSE, valuesStr);
		//log.debug("applyInsert: "+sql);
	}

	/*public void addLimitOffset(LimitOffsetStrategy strategy, int offset) throws ServletException {
		addLimitOffset(strategy, this.limit, offset);
	}*/
	
	protected void addLimitOffset(LimitOffsetStrategy strategy, Integer limit, int offset) throws ServletException {
		if(limit==null) { limit = 0; }
		else { applyedLimit = limit; }
		if(limit<=0 && offset<=0) { return; }
		if(strategy==LimitOffsetStrategy.RESULTSET_CONTROL) { return; }
		
		if(strategy==LimitOffsetStrategy.SQL_LIMIT_OFFSET) {
			//XXX assumes that the original query has no limit/offset clause
			if(limit>0) {
				sql += "\nlimit "+limit;
			}
			if(offset>0) {
				sql += "\noffset "+offset;
			}
		}
		else if(strategy==LimitOffsetStrategy.SQL_ROWNUM) {
			if(limit>0 && offset>0) {
				/*if(! allowEncapsulation) {
					throw new BadRequestException("limit/offset not allowed in query "+relation.getName());
				}*/
				/* //works, but query below is simpler
				sql =
					 "select * from\n"
					+"  ( select * from\n" 
					+"    ( select a.*, ROWNUM rnum from\n"
					+"      (\n"
					+       sql+"\n"
					+"      ) a\n"
					+"    )\n" 
					+"    where rnum <= "+(limit+offset)+"\n"
					+"  )\n"
					+"where rnum > "+offset;
				*/
				sql =
					 "select * from\n"
					+"   ( select a.*, ROWNUM rnum from\n"
					+"      (\n"
					+       sql+"\n"
					+"      ) a\n"
					+"   )\n" 
					+"where rnum <= "+(limit+offset)+"\n"
					+"and rnum > "+offset;
			}
			else if(limit>0) {
				/*if(orderByApplyed) {
					sql = "select * from (\n"+sql+"\n) where rownum <= "+limit; 
				}
				else {
				*/
				addEncapsulatingFilter("rownum <= "+limit, false);
				//}
			}
			else {
				/*if(! allowEncapsulation) {
					throw new BadRequestException("filter not allowed in query "+relation.getName());
				}*/
				sql = "select * from " 
					+"( select a.*, ROWNUM rnum from (\n"
					+ sql
					+"\n) a ) " 
					+"where rnum > "+offset;
			}
		}
		else {
			throw new InternalServerException("Unknown Limit/Offset strategy: "+strategy);
		}
		sqlLoEncapsulated = true;
		
		return;
	}
	
	public void applyProjection(RequestSpec reqspec) {
		String projectionClause = createSQLColumns(reqspec, relation);
		//log.debug("applyProjection: projectionClause = "+projectionClause);
		applyProjection(reqspec, projectionClause);
	}
	
	public void applyProjection(RequestSpec reqspec, String projectionClause) {
		//log.debug("applyProjection: projectionClause: "+projectionClause+" [table="+table+"]");
		if(sql.contains(PARAM_PROJECTION_CLAUSE)) {
			sql = sql.replace(PARAM_PROJECTION_CLAUSE, projectionClause);
		}
		else {
			if(reqspec.columns.size()>0 || hasDefaultColumnNames(relation) || reqspec.distinct) {
				addProjection(projectionClause);
				if(!(relation instanceof Query)) {
					log.warn("relation of type "+relation.getRelationType()+" (not Query) with no "+PARAM_PROJECTION_CLAUSE+" ?");
				}
			}
			else {
				// no columns selected...
				//log.warn("no columns selected...");
				// XXX: select all from table.getColumnNames()?
				if(reqspec.aliases.size()>0) {
					throw new BadRequestException("aliases requested without fields");//+" [aliases: "+reqspec.aliases+"]");
				}
			}
		}
	}
	
	private static String createSQLColumns(RequestSpec reqspec, Relation table) {
		String columns = "*";
		List<String> aliases = hasGroupByOrAggregate(reqspec) ? null : reqspec.aliases;
		
		if(reqspec.columns.size()>0) {
			List<String> tabColsList = table.getColumnNames();
			if(tabColsList==null) {
				throw new BadRequestException("relation's columns not found [relation: "+table.getQualifiedName()+"]");
			}
			//log.debug("createSQLColumns: tabColsList: "+tabColsList);
			if(aliases!=null) { checkAliases(aliases, reqspec.columns); }
			Set<String> tabCols = new HashSet<String>(tabColsList);
			List<String> sqlCols = new ArrayList<String>(); 
			for(String reqColumn: reqspec.columns) {
				if(MiscUtils.containsIgnoreCase(tabCols, reqColumn)) {
					sqlCols.add(reqColumn);
				}
				else {
					String message = "column not found: '"+reqColumn+"' [relation: "+table.getQualifiedName()+"]";
					log.warn(message);
					throw new NotFoundException(message);
				}
			}
			if(sqlCols.size()>0) {
				columns = getColumnsStr(sqlCols, aliases);
			}
			else {
				log.warn("no valid column specified. defaulting to 'all'");
			}
		}
		else {
			List<String> cols = table.getColumnNames();
			//log.debug("createSQLColumns: cols: "+cols);
			if(table instanceof QonRelation) {
				QonRelation qr = (QonRelation) table;
				List<String> qrCols = qr.getDefaultColumnNames();
				log.debug("getDefaultColumnNames: qrCols = "+qrCols);
				if(qrCols!=null && qrCols.size()>0) {
					cols = qrCols;
				}
			}
			if(aliases!=null) { checkAliases(aliases, cols); }
			if(cols!=null && cols.size()>0) {
				columns = getColumnsStr(cols, aliases);
			}
		}
		if(reqspec.distinct) {
			columns = "distinct "+columns;
		}
		return columns;
	}

	static boolean hasDefaultColumnNames(Relation relation) {
		if(relation instanceof QonRelation) {
			QonRelation qr = (QonRelation) relation;
			List<String> qrCols = qr.getDefaultColumnNames();
			log.debug("getDefaultColumnNames: qrCols = "+qrCols);
			return (qrCols!=null) && qrCols.size()>0;
		}
		return false;
	}
	
	/*static void checkAliases(RequestSpec reqspec) {
		checkAliases(reqspec.aliases, reqspec.columns);
	}*/
	
	static void checkAliases(Collection<String> aliases, Collection<String> cols) {
		if(aliases==null) { return; }
		
		int asize = aliases.size();
		int csize = cols!=null?cols.size():0;
		if(asize>0 && asize!=csize) {
			log.debug("checkAliases: exception: cols="+cols+" ; aliases="+aliases);
			throw new BadRequestException("Field count [#"+csize+"] != aliases count [#"+asize+"]");
		}
	}
	
	static String getColumnsStr(List<String> cols, List<String> aliases) {
		if(aliases==null || aliases.size()==0) {
			return Utils.join(cols, ", ", sqlIdDecorator);
		}
		if(cols.size()!=aliases.size()) {
			log.warn("getColumnsStr: cols.size() ["+cols.size()+"] != aliases.size() ["+aliases.size()+"] - ignoring aliases");
			return Utils.join(cols, ", ", sqlIdDecorator);
		}
		StringBuilder buffer = new StringBuilder();
		for(int i=0;i<cols.size();i++) {
			if(i>0) {
				buffer.append(", ");
			}
			String alias = aliases.get(i);
			buffer.append( sqlIdDecorator.get(cols.get(i)) +
					( (alias!=null && !alias.equals(""))? " as " + sqlIdDecorator.get(alias) : "")
					);
		}
		
		return buffer.toString();
	}
	
	@Override
	public String toString() {
		List<String> sl = new ArrayList<String>();
		for(Object o: bindParameterValues) {
			sl.add(substring(String.valueOf(o), 30));
		}
		return "SQL[\n"+sql+"\n[bindpar="+sl+"]]";
	}
	
	protected static String quoteString() {
		if(features!=null) {
			features.getIdentifierQuoteString();
		}
		return "\"";
	}
	
	public static boolean valid(String s) {
		return s!=null && !s.equals("");
	}
	
	public static void setDBMSFeatures(DBMSFeatures feat) {
		features = feat;
	}
	
	/* ----------------- extra SQL metadata ----------------- */
	
	static final String PTRN_ALLOW_ENCAPSULATION = "allow-encapsulation";
	static final String PTRN_DEFAULT_COLUMNS = "default-columns";
	static final String PTRN_LIMIT_MAX = "limit-max";
	static final String PTRN_LIMIT_DEFAULT = "limit-default";
	static final String PTRN_NAMED_PARAMETERS = "named-parameters";
	static final String PTRN_BIND_NULL_ON_MISSING_PARAMS = "bind-null-on-missing-parameters";
	
	static final String PTRN_MATCH_BOOLEAN = "true|false";
	static final String PTRN_MATCH_INT = "\\d+";
	static final String PTRN_MATCH_STRINGLIST = "[\\w\\-,]+";
	static final String PTRN_MATCH_BOOLEANLIST = "[true|false][,[true|false]]*";
	
	static final Pattern allowEncapsulationBooleanPattern = Pattern.compile("/\\*.*\\b"+Pattern.quote(PTRN_ALLOW_ENCAPSULATION)+"\\s*=\\s*("+PTRN_MATCH_BOOLEAN+")\\b.*\\*/", Pattern.DOTALL);
	static final Pattern defaultColumnsPattern = Pattern.compile("/\\*.*\\b"+Pattern.quote(PTRN_DEFAULT_COLUMNS)+"\\s*=\\s*("+PTRN_MATCH_STRINGLIST+")\\b.*\\*/", Pattern.DOTALL);
	static final Pattern limitMaxIntPattern = Pattern.compile("/\\*.*\\b"+Pattern.quote(PTRN_LIMIT_MAX)+"\\s*=\\s*("+PTRN_MATCH_INT+")\\b.*\\*/", Pattern.DOTALL);
	static final Pattern limitDefaultIntPattern = Pattern.compile("/\\*.*\\b"+Pattern.quote(PTRN_LIMIT_DEFAULT)+"\\s*=\\s*("+PTRN_MATCH_INT+")\\b.*\\*/", Pattern.DOTALL);
	static final Pattern namedParametersPattern = Pattern.compile("/\\*.*\\b"+Pattern.quote(PTRN_NAMED_PARAMETERS)+"\\s*=\\s*("+PTRN_MATCH_STRINGLIST+")\\b.*\\*/", Pattern.DOTALL);
	static final Pattern bindNullOnMissingParamsPattern = Pattern.compile("/\\*.*\\b"+Pattern.quote(PTRN_BIND_NULL_ON_MISSING_PARAMS)+"\\s*=\\s*("+PTRN_MATCH_BOOLEANLIST+")\\b.*\\*/", Pattern.DOTALL);
	
	static boolean processPatternBoolean(String sql, Pattern pattern, boolean defaultValue) {
		Matcher m = pattern.matcher(sql);
		//log.info("s: "+sql+" matcher: "+m);
		if(!m.find()) { return defaultValue; }
		
		String g1 = m.group(1);
		if(g1!=null) {
			return g1.equals("true");
		}
		return defaultValue;
	}

	static boolean[] processPatternBooleanArray(String str, Pattern pattern, boolean defaultValue) {
		List<Boolean> ret = new ArrayList<Boolean>();
		Matcher m = pattern.matcher(str);
		//log.info("str: "+str+" matcher: "+m);
		if(!m.find()) {
			boolean bret[] = new boolean[]{ defaultValue };
			return bret;
		}
		
		String g1 = m.group(1);
		if(g1!=null) {
			String[] bools = g1.split(",");
			for(String s: bools) {
				ret.add(s.trim().equals("true"));
				//log.info("- "+s+" / "+s.trim().equals("true"));
			}
		}
		boolean[] bret = new boolean[ret.size()];
		for(int i=0;i<ret.size();i++) {
			bret[i] = ret.get(i);
		}
		return bret;
	}
	
	static Integer processPatternInteger(String sql, Pattern pattern) {
		return processPatternInteger(sql, pattern, null);
	}
	
	static Integer processPatternInteger(String sql, Pattern pattern, Integer defaultValue) {
		Matcher m = pattern.matcher(sql);
		//log.info("s: "+s+" matcher: "+m);
		if(!m.find()) { return defaultValue; }
		
		String g1 = m.group(1);
		if(g1!=null) {
			return Integer.parseInt(g1);
		}
		return defaultValue;
	}

	static String processPatternString(String sql, Pattern pattern, String defaultValue) {
		Matcher m = pattern.matcher(sql);
		//log.info("s: "+s+" matcher: "+m);
		if(!m.find()) { return defaultValue; }
		
		String g1 = m.group(1);
		if(g1!=null) {
			return g1;
		}
		return defaultValue;
	}
	
	static String substring(String s, int maxlength) {
		if(s==null) { return null; }
		if(s.length()>maxlength) { return s.substring(0, maxlength-1)+" (...)"; }
		return s;
	}
	
	static String getFinalVariableValue(String username) {
		return username!=null ? "'"+username.replace("'", "")+"'" : "''";
	}
	
	public List<Object> getParameterValues() {
		return bindParameterValues;
	}
	
	public void addParameter(Object value, String type) {
		//log.debug("addParameter:: bind value "+value+" ; type="+type);
		if(value instanceof String) {
			String str = (String) value;
			type = type!=null ? type.toUpperCase() : null;
			// XXXdone test for empty string ("")
			if(DBUtil.INT_COL_TYPES_LIST.contains(type)) {
				try {
					bindParameterValues.add(Long.parseLong(str));
				}
				catch(NumberFormatException e) {
					if(str.isEmpty()) {
						bindParameterValues.add(null);
					}
					else {
						log.warn("Error parsing long '"+value+"', trying as String");
						bindParameterValues.add(value);
					}
				}
			}
			else if(DBUtil.FLOAT_COL_TYPES_LIST.contains(type)) {
				try {
					bindParameterValues.add(Double.parseDouble(str));
				}
				catch(NumberFormatException e) {
					if(str.isEmpty()) {
						bindParameterValues.add(null);
					}
					else {
						log.warn("Error parsing double '"+value+"', trying as String");
						bindParameterValues.add(value);
					}
				}
			}
			else if(startsWithAny(type, DBUtil.DATE_COL_TYPES_LIST)) {
				try {
					Date dt = DBUtil.parseDateMultiFormat(str, dateFormats);
					bindParameterValues.add(dt);
					//log.info(">> value: "+value+" ctype: "+type+" dt: "+dt);
				}
				catch(ParseException e) {
					if(str.isEmpty()) {
						bindParameterValues.add(null);
					}
					else {
						log.warn("Error parsing date '"+value+"', trying as String");
						bindParameterValues.add(value);
					}
				}
			}
			else {
				bindParameterValues.add(value);
			}
		}
		else {
			bindParameterValues.add(value);
		}
	}
	
	public void addParameter(Object value) {
		bindParameterValues.add(value);
	}
	
	static void initDateFormats(DumpSyntaxUtils dsutils) {
		DumpSyntax dsHtmlx = dsutils.getDumpSyntax("htmlx");
		DumpSyntax dsHtml = dsutils.getDumpSyntax("html");
		
		dateFormats.clear();
		dateFormats.add(DBUtil.isoDateFormat);
		if(dsHtmlx!=null) {
			dateFormats.add(dsHtmlx.dateFormatter);
		}
		if(dsHtml!=null) {
			dateFormats.add(dsHtml.dateFormatter);
		}
	}
	
	static boolean startsWithAny(String str, List<String> list) {
		if(str==null) { return false; }
		for(String s: list) {
			if(s.startsWith(str)) { return true; }
		}
		return false;
	}
	
	public void bindParameters(PreparedStatement st) throws SQLException, IOException {
		for(int i=0 ; i<bindParameterValues.size() ; i++) {
			Object value = bindParameterValues.get(i);
			if(value instanceof Long) {
				st.setLong(i+1, (Long) value);
			}
			else if(value instanceof Double) {
				st.setDouble(i+1, (Double) value);
			}
			else if(value instanceof String) {
				st.setString(i+1, (String) value);
				/*
				log.debug("param["+i+"] setString: "+value);
				if(((String) value).length()>0) {
					String s = (String) value;
					log.debug("param["+i+"] setString[0]: "+s.getBytes("UTF-8")[0] + " / to-utf8: "+new String(s.getBytes("iso-8859-1"), QueryOn.UTF8));
				}
				*/
			}
			else if(value instanceof Date) {
				st.setTimestamp(i+1, new Timestamp(((Date) value).getTime()));
			}
			else if(value instanceof InputStream) {
				st.setBinaryStream(i+1, (InputStream) value);
			}
			else if(value instanceof Reader) {
				st.setCharacterStream(i+1, (Reader) value);
				//log.debug("param["+i+"] setReader...");
			}
			else if(value instanceof Part) {
				Part p = (Part) value;
				//XXX guess if binary or character stream... based on p.getContentType() or column type??
				//st.setBinaryStream(i+1, p.getInputStream());
				st.setCharacterStream(i+1, new InputStreamReader(p.getInputStream()));
				//log.debug("param["+i+"] setPart...");
			}
			else if(value==null) {
				st.setObject(i+1, null);
			}
			else {
				log.warn("bindParameters: unknown value type: " + value.getClass().getName() );
			}
		}
	}
	
	void addOriginalParameters(RequestSpec reqspec) throws SQLException {
		int informedParams = reqspec.params.size();
		int bindParamsLoop = -1; // bind none
		if(originalBindParameterCount > informedParams) {
			//log.debug("addOriginalParameters: will bind NULL on missing bind parameters [informedParams="+informedParams+";bindParameterCount="+originalBindParameterCount+"]");
			for(int i=informedParams; i < originalBindParameterCount; i++) {
				if(bindNullOnMissingParameters!=null && !bindNullOnMissingParameters[i]) {
					throw new BadRequestException("Query '"+reqspec.object+"' needs "+originalBindParameterCount+" parameters but "
							+((informedParams>0)?"only "+informedParams:"none")
							+((informedParams>1)?" were":" was")
							+" informed"
							+", and can't bind null on parameter "+(i+1)+" by default");
				}
				//log.info("-- ["+i+"] binding null on parameter "+(reqspec.params.size()+1));
				reqspec.params.add(null);
			}
		}
		bindParamsLoop = originalBindParameterCount;
		
		if(SchemaModelUtils.hasParameters(relation)) {
			ParametrizedDBObject pobject = (ParametrizedDBObject) relation;
			//log.info("using addParameter: types="+relation.getParameterTypes());
			for(int i=0;i<bindParamsLoop;i++) {
				addParameter(reqspec.params.get(i), pobject.getParameterTypes().get(i));
			}
		}
		else {
			//log.info("using bindParameterValues: types="+relation.getParameterTypes()+" ; values="+reqspec.params);
			for(int i=0;i<bindParamsLoop;i++) {
				addParameter(reqspec.params.get(i), null);
				//bindParameterValues.add(reqspec.params.get(i));
			}
		}
		//log.debug("addOriginalParameters: bindParameterValues [#"+bindParameterValues.size()+"] = "+bindParameterValues);
	}
	
	public static boolean hasGroupByOrAggregate(RequestSpec reqspec) {
		return reqspec.groupby.size()>0 || reqspec.aggregate.size()>0;
	}
	
	public void applyGroupByOrAggregate(RequestSpec reqspec) {
		if(!hasGroupByOrAggregate(reqspec)) { return; }
		
		List<String> relationCols = null;
		if(relation!=null) {
			relationCols = relation.getColumnNames();
		}
		
		List<String> sqlCols = new ArrayList<String>();
		for(String reqColumn: reqspec.groupby) {
			if(relationCols==null || MiscUtils.containsIgnoreCase(relationCols, reqColumn)) {
				sqlCols.add(reqColumn);
			}
			else {
				String message = "column not found: '"+reqColumn+"' [relation: "+relation.getQualifiedName()+"]";
				log.warn(message);
				throw new NotFoundException(message);
			}
		}
		String columnsWithAliases = getColumnsStr(sqlCols, reqspec.aliases);
		String columns = getColumnsStr(sqlCols, null);
		
		// aggregates
		String aggs = getColumnAggregates(reqspec.aggregate, relationCols);
		if(aggs!=null) {
			if(sqlCols.size()>0) {
				columnsWithAliases += ", "+aggs;
			}
			else {
				columnsWithAliases = aggs;
			}
		}
		
		sql = "select "+columnsWithAliases+" from (\n"+sql+"\n) qon_group" +
				(sqlCols.size()>0?"\ngroup by "+columns:"");
		if(sql.contains(PARAM_ORDER_CLAUSE)) {
			sql = sql.replace(PARAM_ORDER_CLAUSE, "");
		}
		sql += "\n"+PARAM_ORDER_CLAUSE;
	}
	
	static String getFunctionStart(String f) {
		if(f.equals("avg")) {
			return "avg( ";
		}
		if(f.equals("count")) {
			return "count( ";
		}
		if(f.equals("distinct-count")) {
			return "count( distinct ";
		}
		if(f.equals("max")) {
			return "max( ";
		}
		if(f.equals("min")) {
			return "min( ";
		}
		if(f.equals("sum")) {
			return "sum( ";
		}

		//XXX: var? stdev?
		//XXX: see available functions by DBMS?
		// median, mode, rank, percentile_cont
		throw new IllegalArgumentException("unknown aggregate function: "+f);
	}

	static String getFunctionEnd(String f) {
		return " )";
	}

	static String getFunctionName(String f) {
		if(f.equals("avg")) {
			return "avg";
		}
		if(f.equals("count")) {
			return "count";
		}
		if(f.equals("distinct-count")) {
			return "distinct_count";
		}
		if(f.equals("max")) {
			return "max";
		}
		if(f.equals("min")) {
			return "min";
		}
		if(f.equals("sum")) {
			return "sum";
		}
		//XXX: distinct-count
		//XXX: var? stdev?
		//XXX: see available functions by DBMS?
		// median, mode, rank, percentile_cont
		throw new IllegalArgumentException("unknown aggregate function: "+f);
	}
	
	//XXX: add aliases
	String getColumnAggregates(Map<String, String[]> aggregate, List<String> colNames) {
		if(aggregate==null || aggregate.size()==0) { return null; }
		
		int count = 0;
		StringBuilder sb = new StringBuilder();
		for(Map.Entry<String, String[]> entry: aggregate.entrySet()) {
			String col = entry.getKey();
			if(colNames!=null && !colNames.contains(col)) {
				throw new IllegalArgumentException("unknown column: "+col);
			}
			String[] functions = entry.getValue();
			for(String f: functions) {
				String funcName = getFunctionName(f);
				String funcStart = getFunctionStart(f);
				String funcEnd = getFunctionEnd(f);
				sb.append((count>0?", ":"")+
						funcStart+" "+sqlIdDecorator.get(col)+" "+funcEnd+" as "+sqlIdDecorator.get(funcName+"_"+col));
				count++;
			}
		}
		return sb.toString();
	}

	protected static Properties processSqlParameterProperties(String sql, DumpSyntaxInt ds) {
		String propsKey = ds.getSyntaxId()+".allowed-sql-parameters";
		List<String> pkeys = Utils.getStringListFromProp(RequestSpec.syntaxProperties, propsKey, ",");
		Properties newprops = new Properties();
		
		if(pkeys!=null) {
			for(String key: pkeys) {
				String propKey = ds.getSyntaxId()+".parameter@"+key+".prop";
				String regexKey = ds.getSyntaxId()+".parameter@"+key+".regex";
				String propRegex = RequestSpec.syntaxProperties.getProperty(regexKey);
				
				Pattern ptrn = Pattern.compile("/\\*.*\\b"+Pattern.quote(ds.getSyntaxId()+":"+key)+"\\s*=\\s*("+propRegex+")\\b.*\\*/", Pattern.DOTALL);
				//log.info("- ptrn "+ptrn);
				
				Matcher m = ptrn.matcher(sql);
				//log.info("- ptrn: "+ptrn+" matcher: "+m);
				if(m.find()) {
					String g1 = m.group(1);
					if(g1!=null) {
						String finalPropKey = RequestSpec.syntaxProperties.getProperty(propKey);
						newprops.setProperty(finalPropKey, g1);
					}
					//return defaultValue;
				}
			}
		}
		
		/*if(newprops.size()>0) {
			log.info("newprops: "+newprops);
		}
		else {
			log.info("no new props...");
		}*/
		
		return newprops;
	}
	
	public static boolean allowEncapsulation(String sql) {
		return processPatternBoolean(sql, SQL.allowEncapsulationBooleanPattern, true);
	}
	
	public boolean[] bindNullOnMissingParameters() {
		return processPatternBooleanArray(sql, SQL.bindNullOnMissingParamsPattern, false);
	}
	
	static List<String> getNamedParameterNames(String sql) {
		List<String> namedParameters = null;
		String namedParamsStr = processPatternString(sql, namedParametersPattern, null);
		if(namedParamsStr!=null) {
			namedParameters = Utils.getStringList(namedParamsStr, ",");
		}
		return namedParameters;
	}
	
	public static List<String> getAllNamedParameterNames(String sql) {
		List<String> ret = getNamedParameterNames(sql);
		if(ret!=null) {
			return ret;
		}
		List<QueryParameter> qpl = TokenizerUtil.getNamedParameters(sql);
		if(qpl.size()>0) {
			return TokenizerUtil.getParameterNames(qpl);
		}
		return null;
	}

	static List<String> getDefaultColumns(String sql) {
		List<String> defaultColumns = null;
		String defaultColumnsStr = processPatternString(sql, defaultColumnsPattern, null);
		if(defaultColumnsStr!=null) {
			defaultColumns = Utils.getStringList(defaultColumnsStr, ",");
		}
		return defaultColumns;
	}
	
	public static void validateNamedParametersWithParamCount(List<String> namedParameters, int bindParameterCount) {
		if(namedParameters==null) { return; }
		int namedParameterCount = namedParameters.size();
		if(namedParameterCount != bindParameterCount) {
			String message = "'named-parameters' count [#"+namedParameterCount+"] should be equal to bind parameters count [#"+bindParameterCount+"]";
			log.warn(message);
			throw new IllegalStateException(message);
		}
	}
	
	/*
	public boolean hasSqlChanged() {
		return initialSql.equals(getFinalSql());
	}
	*/

	public static int indexOfInitialSql(String finalSql, String initialSql) {
		return finalSql.indexOf(initialSql);
	}

	public static int lineOfInitialSql(String finalSql, String initialSql) {
		int idx = indexOfInitialSql(finalSql, initialSql);
		if(idx==-1) { return -1; }
		String initialPart = finalSql.substring(0, idx);
		int countNL = MiscUtils.countChars(initialPart, '\n');
		return countNL+1;
	}
	
	/*
	public int indexOfInitialSql() {
		return indexOfInitialSql(getFinalSql(), initialSql);
	}

	public int lineOfInitialSql() {
		return lineOfInitialSql(getFinalSql(), initialSql);
	}
	*/
	
	public static boolean hasSqlFilter(Relation relation) {
		if(relation instanceof QonTable) {
			QonTable t = (QonTable) relation;
			return t.hasSqlFilter();
		}
		return false;
	}

}
