package tbrugz.queryon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.QueryOn.LimitOffsetStrategy;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.def.DBMSResources;
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
	//XXX add limit/offset-clause?

	static StringDecorator sqlIdDecorator = new StringDecorator.StringQuoterDecorator(quoteString());
	
	static boolean validateOrderColumnNames = true;
	
	String sql;
	final Relation relation;
	boolean orderByApplyed = false;
	Integer originalBindParameterCount;
	List<String> bindParameterValues = new ArrayList<String>();
	//XXX add 'final String initialSql;'?
	
	protected SQL(String sql, Relation relation) {
		this.sql = sql;
		this.relation = relation;
	}
	
	public String getSql() {
		return sql;
	}
	
	public String getFinalSql() {
		return sql.replace(PARAM_WHERE_CLAUSE, "").replace(PARAM_FILTER_CLAUSE, "").replace(PARAM_ORDER_CLAUSE, "");
	}
	
	private static String createSQLstr(Relation table, RequestSpec reqspec) {
		String sql = "select "+PARAM_PROJECTION_CLAUSE+
			" from " + (table.getSchemaName()!=null?sqlIdDecorator.get(table.getSchemaName())+".":"") + sqlIdDecorator.get(table.getName())+
			" " + PARAM_WHERE_CLAUSE+
			" " + PARAM_ORDER_CLAUSE;
		return sql;
	}
	
	public static SQL createSQL(Relation relation, RequestSpec reqspec) {
		if(relation instanceof Query) { //class Query is subclass of View, so this test must come first
			//XXX: other query builder strategy besides [where-clause]? contains 'cursor'?
			Query q = (Query) relation;
			SQL sql = new SQL( q.getQuery() , relation);
			sql.originalBindParameterCount = q.getParameterCount(); 
			return sql;
		}
		else if(relation instanceof Table) {
			return new SQL(createSQLstr(relation, reqspec), relation);
		}
		else if(relation instanceof View) {
			return new SQL(createSQLstr(relation, reqspec), relation);
		}
		throw new IllegalArgumentException("unknown relation type: "+relation.getClass().getName());
		
		//return new SQL(createSQLstr(relation, reqspec), relation);
	}

	public static SQL createInsertSQL(Relation relation) {
		String sql = "insert into "+
				(relation.getSchemaName()!=null?sqlIdDecorator.get(relation.getSchemaName())+".":"") + sqlIdDecorator.get(relation.getName())+
				" (" + PARAM_INSERT_COLUMNS_CLAUSE + ")" +
				" values (" + PARAM_INSERT_VALUES_CLAUSE+")";
		return new SQL(sql, relation);
	}

	public static SQL createUpdateSQL(Relation relation) {
		String sql = "update "+
				(relation.getSchemaName()!=null?sqlIdDecorator.get(relation.getSchemaName())+".":"") + sqlIdDecorator.get(relation.getName())+
				" set " + PARAM_UPDATE_SET_CLAUSE +
				" " + PARAM_WHERE_CLAUSE;
		return new SQL(sql, relation);
	}

	public static SQL createDeleteSQL(Relation relation) {
		String sql = "delete "+
				"from " + (relation.getSchemaName()!=null?relation.getSchemaName()+".":"") + relation.getName()+
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
		else if(filter.length()>0) {
			if(relation!=null && relation instanceof Query) {
				sql = "select * from (\n"+sql+"\n)";
			}
			sql += " where "+filter+" "+PARAM_FILTER_CLAUSE;
		}
	}
	
	private void addProjection(String columns) {
		String sqlFilter = "";
		if(!sql.contains(PARAM_WHERE_CLAUSE) && !sql.contains(PARAM_FILTER_CLAUSE)) {
			sqlFilter = " " + PARAM_WHERE_CLAUSE;
		}
		String sqlOrder = "";
		if(!sql.contains(PARAM_ORDER_CLAUSE)) {
			sqlOrder = " " + PARAM_ORDER_CLAUSE;
		}
		sql = "select "+columns+" from (\n"+sql+"\n)"+sqlFilter+sqlOrder;
	}
	
	public void applyOrder(RequestSpec reqspec) {
		//TODOne: validate columns
		if(reqspec.orderCols.size()==0) return;
		
		List<String> relationCols = null;
		if(relation!=null) {
			relationCols = relation.getColumnNames();
		}
		
		StringBuffer sb = new StringBuffer();
		sb.append("order by ");
		for(int i=0;i<reqspec.orderCols.size();i++) {
			String col = reqspec.orderCols.get(i);
			String ascDesc = reqspec.orderAscDesc.get(i);
			if(col==null || col.equals("")) { continue; }
			//XXXdone option to ignore unknown columns? or to not validate?
			if(validateOrderColumnNames && relationCols!=null && !relationCols.contains(col)) {
				throw new BadRequestException("can't order by '"+col+"' (unknown column)");
			}
			
			sb.append((i==0?"":", ")+sqlIdDecorator.get(col)+" "+ascDesc);
		}
		
		if(sql.contains(PARAM_ORDER_CLAUSE)) {
			sql = sql.replace(PARAM_ORDER_CLAUSE, sb.toString());
		}
		else {
			sql = "select * from (\n"+sql+"\n) "+sb.toString();
		}
		orderByApplyed = true;
	}

	public void applyUpdate(String set) {
		sql = sql.replace(PARAM_UPDATE_SET_CLAUSE, set);
	}

	public void applyInsert(String cols, String values) {
		sql = sql.replace(PARAM_INSERT_COLUMNS_CLAUSE, cols).replace(PARAM_INSERT_VALUES_CLAUSE, values);
	}
	
	public void addLimitOffset(LimitOffsetStrategy strategy, RequestSpec reqspec) throws ServletException {
		if(reqspec.limit<=0 && reqspec.offset<=0) { return; }
		if(strategy==LimitOffsetStrategy.RESULTSET_CONTROL) { return; }
		
		if(strategy==LimitOffsetStrategy.SQL_LIMIT_OFFSET) {
			//XXX assumes that the original query has no limit/offset clause
			if(reqspec.limit>0) {
				sql += "\nlimit "+reqspec.limit;
			}
			if(reqspec.offset>0) {
				sql += "\noffset "+reqspec.offset;
			}
		}
		else if(strategy==LimitOffsetStrategy.SQL_ROWNUM) {
			if(reqspec.limit>0 && reqspec.offset>0) {
				sql = "select * from " 
					+"( select a.*, ROWNUM rnum from (\n"
					+ sql
					+"\n) a " 
					+"where ROWNUM <= "+(reqspec.limit+reqspec.offset)+" ) "
					+"where rnum > "+reqspec.offset;
			}
			else if(reqspec.limit>0) {
				if(orderByApplyed) {
					sql = "select * from (\n"+sql+"\n) where rownum <= "+reqspec.limit; 
				}
				else {
					addFilter("rownum <= "+reqspec.limit);
				}
			}
			else {
				sql = "select * from " 
					+"( select a.*, ROWNUM rnum from (\n"
					+ sql
					+"\n) a ) " 
					+"where rnum > "+reqspec.offset;
			}
		}
		else {
			throw new ServletException("Unknown Limit/Offset strategy: "+strategy);
		}
		
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
				if(tabCols.contains(reqColumn)) {
					sqlCols.add(reqColumn);
				}
				else {
					log.warn("column not found: "+reqColumn+" [table:"+table.getName()+"]");
				}
			}
			if(sqlCols.size()>0) {
				columns = Utils.join(sqlCols, ", ", sqlIdDecorator);
			}
			else {
				log.warn("no valid column specified. defaulting to 'all'");
			}
		}
		if(reqspec.distinct) {
			columns = "distinct "+columns;
		}
		return columns;
	}
	
	@Override
	public String toString() {
		return "SQL[\n"+sql+"\n[bindpar="+bindParameterValues+"]]";
	}
	
	public static String quoteString() {
		return DBMSResources.instance().getIdentifierQuoteString();
	}
	
}
