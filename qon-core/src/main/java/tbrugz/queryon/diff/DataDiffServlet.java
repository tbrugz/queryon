package tbrugz.queryon.diff;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.AbstractHttpServlet;
import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.NamedTypedDBObject;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.QueryOnSchema;
import tbrugz.queryon.QueryOnSchemaInstant;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.SQL;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.util.DBObjectUtils;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.QOnContextUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.queryon.util.ShiroUtils;
import tbrugz.queryon.util.WebUtils;
import tbrugz.sqldiff.datadiff.DataDiff;
import tbrugz.sqldiff.datadiff.DiffSyntax;
import tbrugz.sqldiff.datadiff.HTMLDiff;
import tbrugz.sqldiff.datadiff.ResultSetDiff;
import tbrugz.sqldiff.datadiff.SQLDataDiffSyntax;
import tbrugz.sqldump.datadump.DataDump;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.Column;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.NamedDBObject;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.resultset.ResultSetColumnMetaData;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.Utils;

public class DataDiffServlet extends AbstractHttpServlet {

	private static final long serialVersionUID = 1L;
	static final Log log = LogFactory.getLog(DataDiffServlet.class);

	static final String SYNTAX_HTML = "html";
	static final String SYNTAX_SQL = "sql";
	
	static final long DEFAULT_LOOP_LIMIT = 1000L;
	
	static final String PROP_LIMIT_MAX = "queryon.datadiff.limit.max";
	
	static final String PARAM_MIMETYPE = "mimetype";
	static final String PARAM_DATADIFFTYPES = "dmlops";
	
	static final String PREFIX_IGNORECOL = "ignorecol:";
	static final String PREFIX_ALTERNATE_UK = "altuk:";

	static final String PREFIX_FILTER_IN = "fin:";
	static final String PREFIX_FILTER_NOT_IN = "fnin:";
	static final String PREFIX_FILTER_GT = "fgt:";
	static final String PREFIX_FILTER_GE = "fge:";
	static final String PREFIX_FILTER_LT = "flt:";
	static final String PREFIX_FILTER_LE = "fle:";
	static final String PREFIX_FILTER_NULL = "fnull:";
	static final String PREFIX_FILTER_NOT_NULL = "fnotnull:";
	static final String PREFIX_FILTER_LIKE = "flk:";
	static final String PREFIX_FILTER_NOT_LIKE = "fnlk:";
	
	//final boolean instant = true;
	long loopLimit = DEFAULT_LOOP_LIMIT;
	
	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		List<String> partz = QueryOnSchema.parseQS(req);
		if(partz.size()<2 || partz.size()>3) {
			throw new BadRequestException("Malformed URL");
		}
		log.info("partz: "+partz);
		
		Properties prop = QOnContextUtils.getProperties(getServletContext());
		
		String modelISource = SchemaModelUtils.getModelId(req, DiffServlet.PARAM_MODEL_SOURCE, false);
		String modelIdTarget = SchemaModelUtils.getModelId(req, DiffServlet.PARAM_MODEL_TARGET, false);
		if(modelISource.equals(modelIdTarget)) {
			log.warn("equal models being compared [id="+modelISource+"], no diffs can be generated");
		}
		
		List<NamedTypedDBObject> objs = NamedTypedDBObject.getObjectList(partz);
		
		Subject currentUser = ShiroUtils.getSubject(prop, req);
		for(NamedTypedDBObject obj: objs) {
			// shiro authorization - XXX use auth other than SELECT ?
			ShiroUtils.checkPermission(currentUser, obj.getType()+":"+PrivilegeType.SELECT, obj.getFullObjectName());
		}
		
		setupProperties(prop);
		
		String mimeType = req.getParameter(PARAM_MIMETYPE);
		Integer limit = WebUtils.getIntegerParameter(req, RequestSpec.PARAM_LIMIT);
		
		//String metadataId = SchemaModelUtils.getModelId(req, "metadata");
		//log.debug("metadataId: "+metadataId+" / req="+req.getParameter("metadata"));
		//XXX: 'common'(metadata) boolean parameter: get common columns from both models 
		//String metadataId = req.getParameter("metadata");
		//if(metadataId==null) { metadataId = modelIdSource; }
		
		/*SchemaModel model = SchemaModelUtils.getModel(req.getSession().getServletContext(), metadataId);
		if(model==null) {
			throw new BadRequestException("Unknown model: "+metadataId);
		}*/
		
		QueryOnSchemaInstant qos = new QueryOnSchemaInstant();
		//Table table = getTable(qos, obj, prop, model, metadataId);
		
		Connection connSource = null;
		Connection connTarget = null;
		
		try {
			connSource = DBUtil.initDBConn(prop, modelISource);
			connTarget = DBUtil.initDBConn(prop, modelIdTarget);

			final Map<String, String[]> cols2ignore = new HashMap<String, String[]>();
			final Map<String, String[]> altUk = new HashMap<String, String[]>();
			final Map<String, String[]> filterIn = new HashMap<String, String[]>();
			final Map<String, String[]> filterNotIn = new HashMap<String, String[]>();
			final Map<String, String[]> filterLike = new HashMap<String, String[]>();
			final Map<String, String[]> filterNotLike = new HashMap<String, String[]>();
			final Map<String, String> filterGt = new HashMap<String, String>();
			final Map<String, String> filterGe = new HashMap<String, String>();
			final Map<String, String> filterLt = new HashMap<String, String>();
			final Map<String, String> filterLe = new HashMap<String, String>();
			final Set<String> filterNull = new HashSet<String>();
			final Set<String> filterNotNull = new HashSet<String>();

			Map<String,String[]> reqParams = req.getParameterMap();
			for(Map.Entry<String,String[]> entry: reqParams.entrySet()) {
				String key = entry.getKey();
				String[] value = entry.getValue();
				
				RequestSpec.setMultiParam(PREFIX_IGNORECOL, key, value, cols2ignore);
				RequestSpec.setMultiParam(PREFIX_ALTERNATE_UK, key, value, altUk);
				//filters
				RequestSpec.setMultiParam(PREFIX_FILTER_IN, key, value, filterIn);
				RequestSpec.setMultiParam(PREFIX_FILTER_NOT_IN, key, value, filterNotIn);
				RequestSpec.setMultiParam(PREFIX_FILTER_LIKE, key, value, filterLike);
				RequestSpec.setMultiParam(PREFIX_FILTER_NOT_LIKE, key, value, filterNotLike);
				RequestSpec.setUniParam(PREFIX_FILTER_GT, key, value[0], filterGt);
				RequestSpec.setUniParam(PREFIX_FILTER_GE, key, value[0], filterGe);
				RequestSpec.setUniParam(PREFIX_FILTER_LT, key, value[0], filterLt);
				RequestSpec.setUniParam(PREFIX_FILTER_LE, key, value[0], filterLe);
				RequestSpec.setBooleanParam(PREFIX_FILTER_NULL, key, filterNull);
				RequestSpec.setBooleanParam(PREFIX_FILTER_NOT_NULL, key, filterNotNull);
			}
			String dmlOps = req.getParameter(PARAM_DATADIFFTYPES);
			List<DataDiffType> dmlTypes = getDataDiffTypes(dmlOps);
			
			boolean firstObject = true;
			DiffSyntax ds = null;
			
			for(NamedTypedDBObject obj: objs) {
				
			Table tSource = (Table) qos.getObject(DBObjectType.TABLE, obj.getSchemaName(), obj.getName(), connSource);
			Table tTarget = (Table) qos.getObject(DBObjectType.TABLE, obj.getSchemaName(), obj.getName(), connTarget);
			
			if(tSource==null) {
				throw new BadRequestException("relation "+obj+" not found ["+DiffServlet.PARAM_MODEL_SOURCE+"]");
			}
			if(tTarget==null) {
				throw new BadRequestException("relation "+obj+" not found ["+DiffServlet.PARAM_MODEL_TARGET+"]");
			}

			List<Column> cols = DataDiff.getCommonColumns(tSource, tTarget);
			String[] colarr = cols2ignore.get(obj.getName());
			if(colarr!=null) {
				List<String> ignoreCols = new ArrayList<String>();
				ignoreCols.addAll(Arrays.asList( colarr ));
				List<String> ignoredCols = new ArrayList<String>();

				int ignored = 0;
				for(int i=cols.size()-1;i>=0;i--) {
					String colName = cols.get(i).getName();
					if(ignoreCols.contains( colName )) {
						cols.remove(i);
						ignoreCols.remove(colName);
						ignoredCols.add(colName);
						ignored++;
					}
				}
				if(ignoreCols.size()>0) {
					String message = "ignorecols not found: "+ignoreCols+" [obj="+obj.getName()+"; #"+ignoreCols.size()+"]";
					log.warn(message);
					throw new IllegalArgumentException(message);
				}
				log.info("ignored cols ["+obj.getName()+"/#"+ignored+"]: "+ignoredCols);
			}
			String columnsForSelect = DataDiff.getColumnsForSelect(cols);
			List<String> keyColsSource = getKeyCols(tSource);
			List<String> keyColsTarget = getKeyCols(tTarget);
			log.debug("keyCols: s="+keyColsSource+" t="+keyColsTarget+" / equals?="+keyColsSource.equals(keyColsTarget));
			if(! keyColsSource.equals(keyColsTarget)) {
				String message = "source key cols ["+keyColsSource+"] differ from target key cols ["+keyColsTarget+"]";
				log.warn(message);
			}
			
			//XXX test if keycols are the same in both models ?
			List<String> keyCols = keyColsSource;
			String[] tableAltUk = altUk.get(obj.getName());
			if(tableAltUk!=null) {
				keyCols = Arrays.asList(tableAltUk);
				List<String> commonCols = DBObjectUtils.getColumnNames(DataDiff.getCommonColumns(tSource, tTarget));
				
				for(int i=keyCols.size()-1;i>=0;i--) {
					String colName = keyCols.get(i);
					if(!commonCols.contains( colName )) {
						String message = "keyCols/altuk: column not found: "+colName+" [commonCols="+commonCols+" ; keyCols="+keyCols+"]";
						log.warn(message);
						throw new IllegalArgumentException(message);
					}
				}

				log.info("keyCols/altuk: "+keyCols);
			}
			Table table = tSource;
			
			DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(connSource.getMetaData());
			DBMSFeatures featTarget = DBMSResources.instance().getSpecificFeatures(connTarget.getMetaData());
			String quote = feat.getIdentifierQuoteString();
			String quoteTarget = featTarget.getIdentifierQuoteString();
			
			String sqlSource = null;
			String sqlTarget = null;
			
			List<String> filters = new ArrayList<String>();
			List<Object> parameters = new ArrayList<Object>();
			/*{
				String sqlFilterIn = createSqlFilter(filterIn, "in", obj.getName());
				if(sqlFilterIn!=null) { filters.add(sqlFilterIn); }
			}
			{
				String sqlFilterNotIn = createSqlFilter(filterNotIn, "not in", obj.getName());
				if(sqlFilterNotIn!=null) { filters.add(sqlFilterNotIn); }
			}*/

			addSqlInFilter(filterIn, "in", obj.getName(), filters, parameters);
			addSqlInFilter(filterNotIn, "not in", obj.getName(), filters, parameters);
			addSqlLikeFilter(filterLike, "like", obj.getName(), filters, parameters);
			addSqlLikeFilter(filterNotLike, "not like", obj.getName(), filters, parameters);
			addSqlFilter(filterGt, ">", obj.getName(), filters, parameters);
			addSqlFilter(filterGe, ">=", obj.getName(), filters, parameters);
			addSqlFilter(filterLt, "<", obj.getName(), filters, parameters);
			addSqlFilter(filterLe, "<=", obj.getName(), filters, parameters);
			addSqlBoolFilter(filterNull, "is null", obj.getName(), filters);
			addSqlBoolFilter(filterNotNull, "is not null", obj.getName(), filters);

			String whereClause = Utils.join(filters, " and ");
			if("".equals(whereClause)) { whereClause = null; }
			else {
				log.debug("whereClause: "+whereClause);
			}
			
			if(tableAltUk!=null) {
				sqlSource = DataDump.getQuery(table, columnsForSelect, whereClause, Utils.join(keyCols, ", "), false, quote);
				sqlTarget = DataDump.getQuery(table, columnsForSelect, whereClause, Utils.join(keyCols, ", "), false, quoteTarget);
			}
			else {
				sqlSource = DataDump.getQuery(table, columnsForSelect, whereClause, null, true, quote);
				sqlTarget = DataDump.getQuery(table, columnsForSelect, whereClause, null, true, quoteTarget);
			}
			
			if(firstObject) {
				ds = getSyntax(obj, (partz.size()>2 ? partz.get(2):null) );
				RequestSpec.setSyntaxProps(ds, req, feat, prop);
				if(mimeType==null) {
					mimeType = ds.getMimeType();
				}
				resp.setContentType(mimeType);
				//resp.addHeader(ResponseSpec.HEADER_CONTENT_DISPOSITION, "inline");
				String filename = partz.get(1)+"."+ds.getDefaultFileExtension();
				//resp.addHeader(ResponseSpec.HEADER_CONTENT_DISPOSITION, "attachment; filename=" + filename);
				log.debug("filename: "+filename+" mimetype: "+ds.getMimeType());
				firstObject = false;
			}
			runDiff(connSource, connTarget, sqlSource, sqlTarget, parameters, table, keyCols, modelISource, modelIdTarget, ds, dmlTypes, limit, resp.getWriter());
			
			}
		}
		catch(BadRequestException e) {
			throw e;
		}
		// java7+: multicatch
		catch(RuntimeException e) {
			throw new InternalServerException(e.getMessage(), e);
		}
		catch (SQLException e) {
			throw new InternalServerException(e.getMessage(), e);
		}
		catch (ClassNotFoundException e) {
			throw new InternalServerException(e.getMessage(), e);
		}
		catch (NamingException e) {
			throw new InternalServerException(e.getMessage(), e);
		}
		finally {
			ConnectionUtil.closeConnection(connSource);
			ConnectionUtil.closeConnection(connTarget);
		}
	}

	void runDiff(Connection connSource, Connection connTarget, String sqlSource, String sqlTarget, List<Object> parameters, NamedDBObject table, List<String> keyCols,
			String modelIdSource, String modelIdTarget, DiffSyntax ds, List<DataDiffType> ddTypes, Integer limit, Writer writer) throws SQLException, IOException {
		ResultSet rsSource = runQuery(connSource, sqlSource, parameters, modelIdSource, getQualifiedName(table));
		ResultSet rsTarget = runQuery(connTarget, sqlTarget, parameters, modelIdTarget, getQualifiedName(table));
		
		// testing column types equality
		ResultSetColumnMetaData sRSColmd = new ResultSetColumnMetaData(rsSource.getMetaData());
		ResultSetColumnMetaData tRSColmd = new ResultSetColumnMetaData(rsTarget.getMetaData()); 
		if(!sRSColmd.equals(tRSColmd)) {
			log.warn("["+table+"] metadata from ResultSets differ");
			log.debug("["+table+"] diff:\nsource: "+sRSColmd+" ;\ntarget: "+tRSColmd);
		}
		
		ResultSetDiff rsdiff = new ResultSetDiff();
		if(limit!=null) {
			rsdiff.setLimit(limit);
		}
		else {
			rsdiff.setLimit(loopLimit);
		}
		if(ddTypes!=null) {
			rsdiff.setDumpInserts(false);
			rsdiff.setDumpUpdates(false);
			rsdiff.setDumpDeletes(false);
			rsdiff.setDumpEquals(false);
			for(DataDiffType dt: ddTypes) { 
				switch (dt) {
				case INSERT:
					rsdiff.setDumpInserts(true);
					break;
				case UPDATE:
					rsdiff.setDumpUpdates(true);
					break;
				case DELETE:
					rsdiff.setDumpDeletes(true);
					break;
				case EQUALS:
					rsdiff.setDumpEquals(true);
					break;
				default:
					break;
				}
			}
		}
		
		//DBMSResources.instance().updateMetaData(connSource.getMetaData()); // SQLDataDiffSyntax needs DBMSFeatures setted
		log.debug("diff for table '"+table+"'...");
		rsdiff.diff(rsSource, rsTarget, table.getSchemaName(), table.getName(), keyCols, ds, writer);
		log.info("table '"+table+"' data diff: "+rsdiff.getStats());
		
		rsSource.close(); rsTarget.close();
	}
	
	/*QueryOnSchema getQOS() {
		if(instant) {
			return new QueryOnSchemaInstant(); // XXXxx use factory? new QueryOnSchema() / QueryOnSchemaInstant() ...
		}
		return new QueryOnSchema();
	}*/
	
	/*Table getTable(QueryOnSchema qos, NamedTypedDBObject obj, Properties prop, SchemaModel model, String modelId) throws ClassNotFoundException, SQLException, NamingException {
		DBIdentifiable dbid = qos.getObject(obj.getType(), obj.getSchemaName(), obj.getName(), model, prop, modelId);
		if(dbid instanceof Table) {
			return (Table) dbid;
		}
		throw new BadRequestException("Object '"+obj+"' is not a table");
	}*/
	
	List<String> getKeyCols(Table table) {
		List<String> keyCols = null;
		Constraint ctt = SchemaModelUtils.getPK(table);
		if(ctt!=null) {
			keyCols = ctt.getUniqueColumns();
		}
		if(keyCols==null) {
			throw new BadRequestException("table '"+table+"' has no PK or UNIQUE constraints. diff disabled");
		}
		return keyCols;
	}
	
	ResultSet runQuery(Connection conn, String sql, List<Object> parameters, String modelId, String tableName) {
		try {
			log.debug("runQuery:\nsql: "+sql+"\nparameters: "+parameters);
			PreparedStatement stmt = conn.prepareStatement(sql);
			if(parameters!=null) {
				for(int i=0;i<parameters.size();i++) {
					stmt.setObject(i+1, parameters.get(i));
				}
			}
			return stmt.executeQuery();
		}
		catch(SQLException e) {
			String message = "error in sql exec [model="+modelId+" ; '"+tableName+"']: "+e.toString().trim()+"\nsql: "+sql;
			log.warn(message);
			throw new BadRequestException(message);
		}
	}
	
	//XXX: get syntax based on URL or accept header
	static DiffSyntax getSyntax(NamedTypedDBObject obj, String lastUrlPart) throws SQLException {
		DiffSyntax ds = null;
		String syntax = lastUrlPart;
		if(syntax==null) { syntax = obj.getMimeType(); }
		if(SYNTAX_SQL.equals(syntax)) {
			ds = new SQLDataDiffSyntax();
		}
		else if(SYNTAX_HTML.equals(syntax) || obj.getMimeType()==null) {
			ds = new HTMLDiff();
		}
		else {
			throw new BadRequestException("unknown data type: "+obj.getMimeType());
		}
		
		return ds;
	}
	
	static String getQualifiedName(NamedDBObject obj) {
		return (obj.getSchemaName()!=null?obj.getSchemaName()+".":"")+obj.getName();
	}
	
	static List<DataDiffType> getDataDiffTypes(String dmlOps) {
		List<DataDiffType> dmlTypes = null;
		if(dmlOps!=null) {
			dmlTypes = new ArrayList<DataDiffType>();
			List<String> ops = Utils.getStringList(dmlOps, ",");
			for(String s: ops) {
				dmlTypes.add(DataDiffType.valueOf(s));
			}
		}
		return dmlTypes;
	}

	void setupProperties(Properties prop) {
		loopLimit = Utils.getPropLong(prop, PROP_LIMIT_MAX, Utils.getPropLong(prop, QueryOn.PROP_MAX_LIMIT, DEFAULT_LOOP_LIMIT));
		//log.info("loopLimit = "+loopLimit);
	}
	
	/*
	static final Pattern allowedNumbers = Pattern.compile("[\\d]+");
	static final Pattern allowedStrings = Pattern.compile("[\\w]*");
	
	String createSqlFilter(final Map<String, String[]> valueMap, String compareExpression, String relationName) {
		if(valueMap==null || valueMap.size()==0) { return null; }
		
		List<String> filters = new ArrayList<String>();
		for(String col: valueMap.keySet()) {
			StringBuilder sb = new StringBuilder();
			sb.append(SQL.sqlIdDecorator.get(col)+" "+compareExpression+" (");
			String[] values = valueMap.get(col);
			for(int i=0;i<values.length;i++) {
				String value = values[i];
				if(allowedNumbers.matcher(value).matches()) {
					sb.append((i>0?", ":"") + value);
				}
				else if(allowedStrings.matcher(value).matches()) {
					sb.append((i>0?", ":"") + "'" + value + "'");
				}
				else {
					String message = "value not allowed in filter: '"+value+"'";
					log.warn(message);
					throw new BadRequestException(message);
				}
			}
			sb.append(")");
			filters.add(sb.toString());
		}
		return Utils.join(filters, "\n and ");
	}
	*/

	void addSqlInFilter(final Map<String, String[]> valueMap, String compareExpression, String relationName,
			List<String> filters, List<Object> parameters) {
		if(valueMap==null || valueMap.size()==0) { return; }
		
		for(String col: valueMap.keySet()) {
			StringBuilder sb = new StringBuilder();
			sb.append(SQL.sqlIdDecorator.get(col)+" "+compareExpression+" (");
			String[] values = valueMap.get(col);
			for(int i=0;i<values.length;i++) {
				sb.append((i>0?", ":"") + "?");
				String value = values[i];
				parameters.add(value);
			}
			sb.append(")");
			filters.add(sb.toString());
		}
	}

	void addSqlLikeFilter(final Map<String, String[]> valueMap, String compareExpression, String relationName,
			List<String> filters, List<Object> parameters) {
		if(valueMap==null || valueMap.size()==0) { return; }
		
		for(String col: valueMap.keySet()) {
			String[] values = valueMap.get(col);
			for(int i=0;i<values.length;i++) {
				String filter = SQL.sqlIdDecorator.get(col)+" "+compareExpression+" ?";
				filters.add(filter);
				parameters.add(values[i]);
			}
		}
	}

	void addSqlFilter(final Map<String, String> valueMap, String compareExpression, String relationName,
			List<String> filters, List<Object> parameters) {
		if(valueMap==null || valueMap.size()==0) { return; }
		
		for(String col: valueMap.keySet()) {
			StringBuilder sb = new StringBuilder();
			sb.append(SQL.sqlIdDecorator.get(col)+" "+compareExpression+" ?");
			String value = valueMap.get(col);
			parameters.add(value);
			filters.add(sb.toString());
		}
	}

	void addSqlBoolFilter(final Set<String> valueSet, String compareExpression, String relationName,
			List<String> filters) {
		if(valueSet==null || valueSet.size()==0) { return; }
		
		for(String col: valueSet) {
			StringBuilder sb = new StringBuilder();
			sb.append(SQL.sqlIdDecorator.get(col)+" "+compareExpression);
			filters.add(sb.toString());
		}
	}

	@Override
	public String getDefaultUrlMapping() {
		return "/datadiff/*";
	}

}
