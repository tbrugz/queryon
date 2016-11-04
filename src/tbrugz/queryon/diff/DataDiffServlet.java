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
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.naming.NamingException;
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
import tbrugz.queryon.util.DBUtil;
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
	
	//final boolean instant = true;
	long loopLimit = DEFAULT_LOOP_LIMIT;
	
	//XXXdone: add param columnsToIgnore - for each table: ignorecol:TABLE2=COL2&ignorecol:TABLE2=COL3
	//XXXdone: add alternateUk - for each table: ?altuk:TABLE2=COL2&altuk:TABLE2=COL3
	//XXXdone: add dml operations: INSERT,UPDATE,DELETE
	//XXX: add filters?
	
	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		List<String> partz = QueryOnSchema.parseQS(req);
		if(partz.size()<2 || partz.size()>3) {
			throw new BadRequestException("Malformed URL");
		}
		log.info("partz: "+partz);
		
		Properties prop = (Properties) req.getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
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

			Map<String,String[]> reqParams = req.getParameterMap();
			final Map<String, String[]> cols2ignore = new HashMap<String, String[]>();
			final Map<String, String[]> altUk = new HashMap<String, String[]>();
			for(Map.Entry<String,String[]> entry: reqParams.entrySet()) {
				String key = entry.getKey();
				String[] value = entry.getValue();
				
				//setUniParam("ignorecols:", key, value[0], );
				RequestSpec.setMultiParam("ignorecol:", key, value, cols2ignore);
				RequestSpec.setMultiParam("altuk:", key, value, altUk);
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
				List<String> ignoreCols = Arrays.asList( colarr );
				for(int i=cols.size()-1;i>=0;i--) {
					if(ignoreCols.contains( cols.get(i).getName() )) {
						cols.remove(i);
					}
				}
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
			}
			Table table = tSource;
			
			DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(connSource.getMetaData());
			DBMSFeatures featTarget = DBMSResources.instance().getSpecificFeatures(connTarget.getMetaData());
			String quote = feat.getIdentifierQuoteString();
			String quoteTarget = featTarget.getIdentifierQuoteString();
			
			String sqlSource = null;
			String sqlTarget = null;
			if(tableAltUk!=null) {
				sqlSource = DataDump.getQuery(table, columnsForSelect, null, Utils.join(keyCols, ", "), false, quote);
				sqlTarget = DataDump.getQuery(table, columnsForSelect, null, Utils.join(keyCols, ", "), false, quoteTarget);
			}
			else {
				sqlSource = DataDump.getQuery(table, columnsForSelect, null, null, true, quote);
				sqlTarget = DataDump.getQuery(table, columnsForSelect, null, null, true, quoteTarget);
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
				log.info(">> filename: "+filename+" mimetype: "+ds.getMimeType());
				firstObject = false;
			}
			runDiff(connSource, connTarget, sqlSource, sqlTarget, table, keyCols, modelISource, modelIdTarget, ds, dmlTypes, limit, resp.getWriter());
			
			}
		}
		catch(RuntimeException e) {
			throw new BadRequestException(e.getMessage(), e);
		}
		finally {
			ConnectionUtil.closeConnection(connSource);
			ConnectionUtil.closeConnection(connTarget);
			resp.getWriter().flush();
		}
	}

	void runDiff(Connection connSource, Connection connTarget, String sqlSource, String sqlTarget, NamedDBObject table, List<String> keyCols,
			String modelIdSource, String modelIdTarget, DiffSyntax ds, List<DataDiffType> ddTypes, Integer limit, Writer writer) throws SQLException, IOException {
		ResultSet rsSource = runQuery(connSource, sqlSource, modelIdSource, getQualifiedName(table));
		ResultSet rsTarget = runQuery(connTarget, sqlTarget, modelIdTarget, getQualifiedName(table));
		
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
		Constraint ctt = table.getPKConstraint();
		if(ctt!=null) {
			keyCols = ctt.getUniqueColumns();
		}
		if(keyCols==null) {
			throw new BadRequestException("table '"+table+"' has no PK. diff disabled");
		}
		return keyCols;
	}
	
	ResultSet runQuery(Connection conn, String sql, String modelId, String tableName) {
		try {
			PreparedStatement stmt = conn.prepareStatement(sql);
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
	
}
