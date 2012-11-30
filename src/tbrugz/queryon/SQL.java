package tbrugz.queryon;

import javax.servlet.ServletException;

import tbrugz.queryon.QueryOn.LimitOffsetStrategy;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.util.Utils;

public class SQL {
	
	public static final String PARAM_WHERE_CLAUSE = "[where-clause]";
	public static final String PARAM_FILTER_CLAUSE = "[filter-clause]";
	//XXX add order-clause? limit/offset-clause?

	String sql;
	//XXX add Relation relation; to constructor?
	
	public SQL(String sql) {
		this.sql = sql;
	}
	
	public String getSql() {
		return sql;
	}
	
	public String getFinalSql() {
		return sql.replace(PARAM_WHERE_CLAUSE, "").replace(PARAM_FILTER_CLAUSE, "");
	}
	
	static String createSQLstr(Relation table, RequestSpec reqspec) {
		String columns = "*";
		if(reqspec.columns.size()>0) {
			columns = Utils.join(reqspec.columns, ", ");
		}
		String sql = "select "+columns+
			" from " + (table.getSchemaName()!=null?table.getSchemaName()+".":"") + table.getName()+
			" " + PARAM_WHERE_CLAUSE;
		return sql;
	}

	public static SQL createSQL(Relation table, RequestSpec reqspec) {
		return new SQL(createSQLstr(table, reqspec));
	}
	
	public void addFilter(String filter, Relation relation) {
		if(filter==null || filter.length()==0) { return; }
		
		if(sql.contains(PARAM_WHERE_CLAUSE)) {
			sql = sql.replace(PARAM_WHERE_CLAUSE, " where "+filter+" "+PARAM_FILTER_CLAUSE);
		}
		else if(sql.contains(PARAM_FILTER_CLAUSE)) {
			sql = sql.replace(PARAM_FILTER_CLAUSE, " and "+filter+" "+PARAM_FILTER_CLAUSE);
		}
		else if(filter.length()>0) {
			//FIXME: if selecting from Table object, do not need to wrap
			if(relation!=null && relation instanceof Query) {
				sql = "select * from ( "+sql+" )";
				//isSQLWrapped = true;
			}
			sql += " where "+filter+" "+PARAM_FILTER_CLAUSE;
			
			/*if(!isSQLWrapped) {
				log.warn("sql may be malformed. sql: "+sql);
			}*/
		}
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
				addFilter("rownum <= "+reqspec.limit, null);
				//sql = "select * from (\n"+sql+"\n) where rownum <= "+reqspec.limit; 
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
	
	@Override
	public String toString() {
		return "SQL[\n"+sql+"\n]";
	}
	
}
