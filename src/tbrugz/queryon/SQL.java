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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.Part;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.QueryOn.LimitOffsetStrategy;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.queryon.util.MiscUtils;
import tbrugz.sqldump.datadump.DumpSyntax;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.util.StringDecorator;
import tbrugz.sqldump.util.Utils;

public class SQL {

	static final Log log = LogFactory.getLog(SQL.class);
	
	public static final String PARAM_WHERE_CLAUSE = "$where_clause";
	public static final String PARAM_FILTER_CLAUSE = "$filter_clause";
	public static final String PARAM_PROJECTION_CLAUSE = "$projection_clause";
	public static final String PARAM_UPDATE_SET_CLAUSE = "[update-set-clause]";
	public static final String PARAM_INSERT_COLUMNS_CLAUSE = "[insert-columns-clause]";
	public static final String PARAM_INSERT_VALUES_CLAUSE = "[insert-values-clause]";
	public static final String PARAM_ORDER_CLAUSE = "$order_clause";

	public static final String VARIABLE_USERNAME = "$username";
	//public static final String VARIABLE_USERROLES = "$userroles";
	
	//XXX add limit/offset-clause?

	public static StringDecorator sqlIdDecorator = new StringDecorator.StringQuoterDecorator(quoteString());
	
	public static List<DateFormat> dateFormats = new ArrayList<DateFormat>();
	
	static boolean validateOrderColumnNames = true;
	
	//final String initialSql;
	String sql;
	boolean sqlLoEncapsulated = false;
	
	final Relation relation;
	final boolean allowEncapsulation;
	final Integer originalBindParameterCount;
	final Integer limit;
	final Integer limitMax;
	final String username;
	//XXX final String userroles;
	
	static DBMSFeatures features = null; //FIXME: DBMSFeatures should not be static
	
	boolean orderByApplyed = false;
	List<Object> bindParameterValues = new ArrayList<Object>();
	//XXX add 'final String initialSql;'?
	
	protected SQL(String sql, Relation relation, Integer originalBindParameterCount, Integer reqspecLimit, String username) {
		//this.initialSql = sql;
		this.sql = sql;
		this.relation = relation;
		this.allowEncapsulation = processPatternBoolean(sql, allowEncapsulationBooleanPattern, true);
		Integer limitDefault = processPatternInteger(sql, limitDefaultIntPattern);
		this.limitMax = processPatternInteger(sql, limitMaxIntPattern);
		this.originalBindParameterCount = originalBindParameterCount;
		
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
			username = "''";
		}
		//log.info("getFinalSql: "+username);
		return sql.replace(PARAM_WHERE_CLAUSE, "").replace(PARAM_FILTER_CLAUSE, "").replace(PARAM_ORDER_CLAUSE, "")
				.replace(VARIABLE_USERNAME, username);
	}
	
	private static String createSQLstr(Relation table, RequestSpec reqspec) {
		String sql = "select "+PARAM_PROJECTION_CLAUSE+
			" from " + (SQL.valid(table.getSchemaName())?sqlIdDecorator.get(table.getSchemaName())+".":"") + sqlIdDecorator.get(table.getName())+
			" " + PARAM_WHERE_CLAUSE+
			" " + PARAM_ORDER_CLAUSE;
		return sql;
	}
	
	public static SQL createSQL(Relation relation, RequestSpec reqspec, String username) {
		if(relation instanceof Query) { //class Query is subclass of View, so this test must come first
			//XXX: other query builder strategy besides [where-clause]? contains 'cursor'?
			Query q = (Query) relation;
			SQL sql = new SQL( q.getQuery() , relation, q.getParameterCount(), reqspec!=null?reqspec.limit:null, username);
			//processSqlXtraMetadata(sql);
			//sql.originalBindParameterCount = q.getParameterCount();
			return sql;
		}
		else if(relation instanceof Table) {
			return new SQL(createSQLstr(relation, reqspec), relation, null, reqspec!=null?reqspec.limit:null);
		}
		else if(relation instanceof View) {
			return new SQL(createSQLstr(relation, reqspec), relation, null, reqspec!=null?reqspec.limit:null);
		}
		throw new IllegalArgumentException("unknown relation type: "+relation.getClass().getName());
		
		//return new SQL(createSQLstr(relation, reqspec), relation);
	}

	public static SQL createSQL(Relation relation, RequestSpec reqspec) {
		return createSQL(relation, reqspec, null);
	}
	
	public static SQL createInsertSQL(Relation relation) {
		String sql = "insert into "+
				(SQL.valid(relation.getSchemaName())?sqlIdDecorator.get(relation.getSchemaName())+".":"") + sqlIdDecorator.get(relation.getName())+
				" (" + PARAM_INSERT_COLUMNS_CLAUSE + ")" +
				" values (" + PARAM_INSERT_VALUES_CLAUSE+")";
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
			(eo.getSchemaName()!=null?eo.getSchemaName()+".":"")+
			(eo.getPackageName()!=null?eo.getPackageName()+".":"")+
			eo.getName());
		if(eo.getParams()!=null) {
			sql.append("(");
			for(int i=0;i<eo.getParams().size();i++) {
				//ExecutableParameter ep = eo.params.get(i);
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
	
	public void addFilter(String filter) {
		if(filter==null || filter.length()==0) { return; }
		
		if(sql.contains(PARAM_WHERE_CLAUSE)) {
			sql = sql.replace(PARAM_WHERE_CLAUSE, "where "+filter+" "+PARAM_FILTER_CLAUSE);
		}
		else if(sql.contains(PARAM_FILTER_CLAUSE)) {
			sql = sql.replace(PARAM_FILTER_CLAUSE, "and "+filter+" "+PARAM_FILTER_CLAUSE);
		}
		else if(filter.length()>0) {
			if(relation!=null && relation instanceof Query) {
				/*if(! allowEncapsulation) {
					throw new BadRequestException("filter not allowed in query "+relation.getName());
				}*/
				sql = "select * from (\n"+sql+"\n) qon_filter";
			}
			sql += " where "+filter+" "+PARAM_FILTER_CLAUSE;
		}
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
	
	protected void addCount() {
		sql = "select count(*) as count from (\n"+sql+"\n) qon_count ";
	}
	
	public void applyOrder(RequestSpec reqspec) {
		//TODOne: validate columns
		if(reqspec.orderCols.size()==0) return;
		
		List<String> relationCols = null;
		if(relation!=null) {
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

	public void applyInsert(String cols, String values) {
		sql = sql.replace(PARAM_INSERT_COLUMNS_CLAUSE, cols).replace(PARAM_INSERT_VALUES_CLAUSE, values);
	}
	
	/*public void addLimitOffset(LimitOffsetStrategy strategy, int offset) throws ServletException {
		addLimitOffset(strategy, this.limit, offset);
	}*/
	
	protected void addLimitOffset(LimitOffsetStrategy strategy, Integer limit, int offset) throws ServletException {
		if(limit==null) { limit = 0; }
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
				if(orderByApplyed) {
					/*if(! allowEncapsulation) {
						throw new BadRequestException("filter not allowed in query "+relation.getName());
					}*/
					sql = "select * from (\n"+sql+"\n) where rownum <= "+limit; 
				}
				else {
					addFilter("rownum <= "+limit);
				}
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
	
	public void applyProjection(RequestSpec reqspec, Relation table) {
		String columns = createSQLColumns(reqspec, table);
		if(sql.contains(PARAM_PROJECTION_CLAUSE)) {
			sql = sql.replace(PARAM_PROJECTION_CLAUSE, columns);
		}
		else {
			if(reqspec.columns.size()>0 || reqspec.distinct) {
				addProjection(columns);
				if(!(relation instanceof Query)) {
					log.warn("relation of type "+relation.getRelationType()+" (not Query) with no "+PARAM_PROJECTION_CLAUSE+" ?");
				}
			}
			else {
				// no columns selected...
			}
		}
	}
	
	private static String createSQLColumns(RequestSpec reqspec, Relation table) {
		String columns = "*";
		if(reqspec.columns.size()>0) {
			Set<String> tabCols = new HashSet<String>(table.getColumnNames()); 
			List<String> sqlCols = new ArrayList<String>(); 
			for(String reqColumn: reqspec.columns) {
				if(MiscUtils.containsIgnoreCase(tabCols, reqColumn)) {
					sqlCols.add(reqColumn);
				}
				else {
					String message = "column not found: "+reqColumn+" [table:"+table.getName()+"]";
					log.warn(message);
					throw new BadRequestException(message);
				}
			}
			if(sqlCols.size()>0) {
				columns = Utils.join(sqlCols, ", ", sqlIdDecorator);
			}
			else {
				log.warn("no valid column specified. defaulting to 'all'");
			}
		}
		else {
			List<String> cols = table.getColumnNames();
			if(cols!=null) {
				columns = Utils.join(cols, ", ", sqlIdDecorator);
			}
		}
		if(reqspec.distinct) {
			columns = "distinct "+columns;
		}
		return columns;
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
	static final String PTRN_LIMIT_MAX = "limit-max";
	static final String PTRN_LIMIT_DEFAULT = "limit-default";
	
	static final String PTRN_MATCH_BOOLEAN = "true|false";
	static final String PTRN_MATCH_INT = "\\d+";
	
	static final Pattern allowEncapsulationBooleanPattern = Pattern.compile("/\\*.*\\b"+Pattern.quote(PTRN_ALLOW_ENCAPSULATION)+"\\s*=\\s*("+PTRN_MATCH_BOOLEAN+")\\b.*\\*/", Pattern.DOTALL);
	static final Pattern limitMaxIntPattern = Pattern.compile("/\\*.*\\b"+Pattern.quote(PTRN_LIMIT_MAX)+"\\s*=\\s*("+PTRN_MATCH_INT+")\\b.*\\*/", Pattern.DOTALL);
	static final Pattern limitDefaultIntPattern = Pattern.compile("/\\*.*\\b"+Pattern.quote(PTRN_LIMIT_DEFAULT)+"\\s*=\\s*("+PTRN_MATCH_INT+")\\b.*\\*/", Pattern.DOTALL);
	
	static boolean processPatternBoolean(String sql, Pattern pattern, boolean defaultValue) {
		Matcher m = pattern.matcher(sql);
		//log.info("s: "+s+" matcher: "+m);
		if(!m.find()) { return defaultValue; }
		
		String g1 = m.group(1);
		if(g1!=null) {
			return g1.equals("true");
		}
		return defaultValue;
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
			// XXXdone test for empty string ("")
			if(DBUtil.INT_COL_TYPES_LIST.contains(type.toUpperCase())) {
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
			else if(DBUtil.FLOAT_COL_TYPES_LIST.contains(type.toUpperCase())) {
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
			else if(startsWithAny(type.toUpperCase(), DBUtil.DATE_COL_TYPES_LIST)) {
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
	
	static void initDateFormats(DumpSyntaxUtils dsutils) {
		DumpSyntax dsHtmlx = dsutils.getDumpSyntax("htmlx");
		DumpSyntax dsHtml = dsutils.getDumpSyntax("html");
		
		dateFormats = new ArrayList<DateFormat>();
		dateFormats.add(DBUtil.isoDateFormat);
		if(dsHtmlx!=null) {
			dateFormats.add(dsHtmlx.dateFormatter);
		}
		if(dsHtml!=null) {
			dateFormats.add(dsHtml.dateFormatter);
		}
	}
	
	static boolean startsWithAny(String str, List<String> list) {
		for(String s: list) {
			if(s.startsWith(str)) { return true; }
		}
		return false;
	}
	
	void bindParameters(PreparedStatement st) throws SQLException, IOException {
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

}
