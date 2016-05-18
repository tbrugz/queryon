package tbrugz.queryon.diff;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
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
import tbrugz.queryon.SchemaModelUtils;
import tbrugz.queryon.ShiroUtils;
import tbrugz.queryon.util.DBUtil;
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

public class DataDiffServlet extends AbstractHttpServlet {

	private static final long serialVersionUID = 1L;
	static final Log log = LogFactory.getLog(DataDiffServlet.class);

	//final boolean instant = true;
	long loopLimit = 1000;
	
	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		List<String> partz = QueryOnSchema.parseQS(req);
		if(partz.size()<2) {
			throw new BadRequestException("Malformed URL");
		}
		log.info("partz: "+partz);
		
		NamedTypedDBObject obj = NamedTypedDBObject.getObject(partz);
		Properties prop = (Properties) req.getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		// shiro authorization - XXX use auth other than SELECT ?
		Subject currentUser = ShiroUtils.getSubject(prop);
		ShiroUtils.checkPermission(currentUser, obj.getType()+":"+PrivilegeType.SELECT, obj.getFullObjectName());
		
		String modelISource = SchemaModelUtils.getModelId(req, DiffServlet.PARAM_MODEL_SOURCE, false);
		String modelIdTarget = SchemaModelUtils.getModelId(req, DiffServlet.PARAM_MODEL_TARGET, false);
		if(modelISource.equals(modelIdTarget)) {
			log.warn("equal models being compared [id="+modelISource+"], no diffs can be generated");
		}
		
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
			
			Table tSource = (Table) qos.getObject(DBObjectType.TABLE, obj.getSchemaName(), obj.getName(), connSource);
			Table tTarget = (Table) qos.getObject(DBObjectType.TABLE, obj.getSchemaName(), obj.getName(), connTarget);
			
			if(tSource==null) {
				throw new BadRequestException("relation "+obj+" not found ["+DiffServlet.PARAM_MODEL_SOURCE+"]");
			}
			if(tTarget==null) {
				throw new BadRequestException("relation "+obj+" not found ["+DiffServlet.PARAM_MODEL_TARGET+"]");
			}

			List<Column> cols = DataDiff.getCommonColumns(tSource, tTarget);
			String columnsForSelect = DataDiff.getColumnsForSelect(cols);
			List<String> keyColsSource = getKeyCols(tSource);
			List<String> keyColsTarget = getKeyCols(tTarget);
			log.debug("keyCols: s="+keyColsSource+" t="+keyColsTarget+" / equals?="+keyColsSource.equals(keyColsTarget));
			
			List<String> keyCols = keyColsSource;
			Table table = tSource;
			
			DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(connSource.getMetaData());
			String quote = feat.getIdentifierQuoteString();
			
			//XXX test if keycols are the same in both models ?
			String sql = DataDump.getQuery(table, columnsForSelect, null, null, true, quote);
			DiffSyntax ds = getSyntax(obj, feat, prop, req);
			
			resp.setContentType(ds.getMimeType());
			runDiff(connSource, connTarget, sql, table, keyCols, modelISource, modelIdTarget, ds, resp.getWriter());
		}
		finally {
			ConnectionUtil.closeConnection(connSource);
			ConnectionUtil.closeConnection(connTarget);
			resp.getWriter().flush();
		}
	}
	
	void runDiff(Connection connSource, Connection connTarget, String sql, NamedDBObject table, List<String> keyCols, String modelIdSource, String modelIdTarget, DiffSyntax ds, Writer writer) throws SQLException, IOException {
		ResultSet rsSource = runQuery(connSource, sql, modelIdSource, getQualifiedName(table));
		ResultSet rsTarget = runQuery(connTarget, sql, modelIdTarget, getQualifiedName(table));
		
		// testing column types equality
		ResultSetColumnMetaData sRSColmd = new ResultSetColumnMetaData(rsSource.getMetaData());
		ResultSetColumnMetaData tRSColmd = new ResultSetColumnMetaData(rsTarget.getMetaData()); 
		if(!sRSColmd.equals(tRSColmd)) {
			log.warn("["+table+"] metadata from ResultSets differ");
			log.debug("["+table+"] diff:\nsource: "+sRSColmd+" ;\ntarget: "+tRSColmd);
		}
		
		ResultSetDiff rsdiff = new ResultSetDiff();
		rsdiff.setLimit(loopLimit);
		
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
	static DiffSyntax getSyntax(NamedTypedDBObject obj, DBMSFeatures feat, Properties prop, HttpServletRequest req) throws SQLException {
		DiffSyntax ds = null;
		if("sql".equals(obj.getMimeType())) {
			ds = new SQLDataDiffSyntax();
		}
		else if("html".equals(obj.getMimeType()) || obj.getMimeType()==null){
			ds = new HTMLDiff();
		}
		else {
			throw new BadRequestException("unknown data type: "+obj.getMimeType());
		}
		RequestSpec.setSyntaxProps(ds, req, feat, prop);
		//ds.procProperties(prop);
		//if(ds.needsDBMSFeatures()) { ds.setFeatures(feat); }
		
		return ds;
	}
	
	static String getQualifiedName(NamedDBObject obj) {
		return (obj.getSchemaName()!=null?obj.getSchemaName()+".":"")+obj.getName();
	}
	
}
