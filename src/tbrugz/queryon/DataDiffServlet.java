package tbrugz.queryon;

import java.io.IOException;
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

import tbrugz.sqldiff.datadiff.DiffSyntax;
import tbrugz.sqldiff.datadiff.HTMLDiff;
import tbrugz.sqldiff.datadiff.ResultSetDiff;
import tbrugz.sqldump.datadump.DataDump;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.Utils;

public class DataDiffServlet extends AbstractHttpServlet {

	private static final long serialVersionUID = 1L;
	static final Log log = LogFactory.getLog(DataDiffServlet.class);

	final boolean instant = true;
	long loopLimit = 1000;
	
	@Override
	void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		List<String> partz = QueryOnSchema.parseQS(req);
		if(partz.size()<2) {
			throw new BadRequestException("Malformed URL");
		}
		log.info("partz: "+partz);
		
		NamedTypedDBObject obj = NamedTypedDBObject.getObject(partz);
		Properties prop = (Properties) req.getSession().getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		// shiro authorization - XXX use auth other than SELECT ?
		Subject currentUser = ShiroUtils.getSubject(prop);
		ShiroUtils.checkPermission(currentUser, obj.getType()+":SELECT", obj.getFullObjectName());
		
		String modelIdFrom = SchemaModelUtils.getModelId(req, "modelFrom");
		String modelIdTo = SchemaModelUtils.getModelId(req, "modelTo");
		if(modelIdFrom.equals(modelIdTo)) {
			log.warn("equal models being compared [id="+modelIdFrom+"], no diffs can be generated");
		}
		
		String metadataId = SchemaModelUtils.getModelId(req, "metadata");
		log.info("metadataId: "+metadataId+" / "+req.getParameter("metadata"));
		//XXX: 'common'(metadata) boolean parameter: get common columns from both models 
		//String metadataId = req.getParameter("metadata");
		//if(metadataId==null) { metadataId = modelIdFrom; }
		
		SchemaModel model = SchemaModelUtils.getModel(req.getSession().getServletContext(), metadataId);
		if(model==null) {
			throw new BadRequestException("Unknown model: "+metadataId);
		}
		
		QueryOnSchema qos = getQOS();
		Table table = getTable(qos, obj, prop, model, metadataId);
		
		String columnsForSelect = getColumnsForSelect(table);

		List<String> keyCols = getKeyCols(table);
		
		String sql = DataDump.getQuery(table, columnsForSelect, null, null, true);

		Connection connFrom = DBUtil.initDBConn(prop, modelIdFrom);
		Connection connTo = DBUtil.initDBConn(prop, modelIdTo);
		
		try {
			ResultSet rsFrom = runQuery(connFrom, sql, modelIdFrom, table.getQualifiedName());
			ResultSet rsTo = runQuery(connTo, sql, modelIdTo, table.getQualifiedName());
			
			ResultSetDiff rsdiff = new ResultSetDiff();
			rsdiff.setLimit(loopLimit);
	
			log.debug("diff for table '"+table+"'...");
			DiffSyntax ds = getSyntax(prop);
			rsdiff.diff(rsFrom, rsTo, table.getName(), keyCols, ds, resp.getWriter());
			log.info("table '"+table+"' data diff: "+rsdiff.getStats());
			
			rsFrom.close(); rsTo.close();
		}
		finally {
			ConnectionUtil.closeConnection(connFrom);
			ConnectionUtil.closeConnection(connTo);
		}
	}
	
	QueryOnSchema getQOS() {
		if(instant) {
			return new QueryOnSchemaInstant(); // XXXxx use factory? new QueryOnSchema() / QueryOnSchemaInstant() ...
		}
		return new QueryOnSchema();
	}
	
	Table getTable(QueryOnSchema qos, NamedTypedDBObject obj, Properties prop, SchemaModel model, String modelId) throws ClassNotFoundException, SQLException, NamingException {
		DBIdentifiable dbid = qos.getObject(obj.getType(), obj.getSchemaName(), obj.getName(), model, prop, modelId);
		if(dbid instanceof Table) {
			return (Table) dbid;
		}
		throw new BadRequestException("Object '"+obj+"' is not a table");
	}
	
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
			PreparedStatement stmtFrom = conn.prepareStatement(sql);
			return stmtFrom.executeQuery();
		}
		catch(SQLException e) {
			throw new BadRequestException("error in sql exec [model="+modelId+" ; '"+tableName+"']: "+sql);
		}
	}
	
	static String getColumnsForSelect(Table t) {
		return Utils.join(t.getColumnNames(), ", ");
	}
	
	static DiffSyntax getSyntax(Properties prop) throws SQLException {
		//List<DiffSyntax> dss = new ArrayList<DiffSyntax>();
		
		DiffSyntax ds = new HTMLDiff();
		ds.procProperties(prop);
		//dss.add(ds);
		
		return ds;
	}
	
}
