package tbrugz.queryon.diff;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.NamedTypedDBObject;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.QueryOnSchema;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.QueryOn.ActionType;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.queryon.util.ShiroUtils;
import tbrugz.queryon.util.WebUtils;
import tbrugz.sqldiff.datadiff.DiffSyntax;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.NamedDBObject;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.Utils;

public class Diff2QServlet extends DataDiffServlet {

	private static final long serialVersionUID = 1L;
	static final Log log = LogFactory.getLog(Diff2QServlet.class);

	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		List<String> partz = QueryOnSchema.parseQS(req);
		if(partz.size()<2 || partz.size()>3) {
			throw new BadRequestException("Malformed URL");
		}
		log.info("partz: "+partz);
		
		NamedTypedDBObject obj = NamedTypedDBObject.getObject(partz);
		Properties prop = (Properties) req.getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		Subject currentUser = ShiroUtils.getSubject(prop, req);
		ShiroUtils.checkPermission(currentUser, ActionType.SELECT_ANY.name(), obj.getFullObjectName());
		
		setupProperties(prop);
		
		String modelIdSource = SchemaModelUtils.getModelId(req, DiffServlet.PARAM_MODEL_SOURCE, false);
		String modelIdTarget = SchemaModelUtils.getModelId(req, DiffServlet.PARAM_MODEL_TARGET, false);
		if(modelIdSource.equals(modelIdTarget)) {
			log.warn("equal models being compared [id="+modelIdSource+"], no diffs can be generated");
		}
		
		Connection connSource = null;
		Connection connTarget = null;
		
		try {
			connSource = DBUtil.initDBConn(prop, modelIdSource);
			connTarget = DBUtil.initDBConn(prop, modelIdTarget);
			
			//XXX: add many sqls
			
			String sqlParam = req.getParameter("sql");
			String keyColsParam = req.getParameter("keycols");
			List<String> keyCols = Utils.getStringList(keyColsParam, ",");
			
			String dmlOps = req.getParameter(PARAM_DATADIFFTYPES);
			List<DataDiffType> dmlTypes = getDataDiffTypes(dmlOps);
			Integer limit = WebUtils.getIntegerParameter(req, RequestSpec.PARAM_LIMIT);
			
			String sql = sqlParam;

			DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(connSource.getMetaData());
			DiffSyntax ds = getSyntax(obj, (partz.size()>2 ? partz.get(2):null));
			RequestSpec.setSyntaxProps(ds, req, feat, prop);
			
			resp.setContentType(ds.getMimeType());
			runDiff(connSource, connTarget, sql, obj, keyCols, modelIdSource, modelIdTarget, ds, dmlTypes, limit, resp.getWriter());
		}
		catch (ClassNotFoundException e) {
			throw new InternalServerException(e.getMessage(), e);
		}
		catch (SQLException e) {
			throw new InternalServerException(e.getMessage(), e);
		}
		catch (NamingException e) {
			throw new InternalServerException(e.getMessage(), e);
		}
		finally {
			ConnectionUtil.closeConnection(connSource);
			ConnectionUtil.closeConnection(connTarget);
			resp.getWriter().flush();
		}
	}
	
	void runDiff(Connection connSource, Connection connTarget, String sql, NamedDBObject table, List<String> keyCols,
			String modelIdSource, String modelIdTarget, DiffSyntax ds, List<DataDiffType> ddTypes, Integer limit, Writer writer) throws SQLException, IOException {
		runDiff(connSource, connTarget, sql, sql, null, table, keyCols, modelIdSource, modelIdTarget, ds, ddTypes, limit, writer);
	}

}
