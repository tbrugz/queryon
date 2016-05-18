package tbrugz.queryon.diff;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.NamedTypedDBObject;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.QueryOnSchema;
import tbrugz.queryon.QueryOn.ActionType;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.queryon.util.ShiroUtils;
import tbrugz.sqldiff.datadiff.DiffSyntax;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.Utils;

public class Diff2QServlet extends DataDiffServlet {

	private static final long serialVersionUID = 1L;
	static final Log log = LogFactory.getLog(Diff2QServlet.class);

	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		List<String> partz = QueryOnSchema.parseQS(req);
		if(partz.size()<2) {
			throw new BadRequestException("Malformed URL");
		}
		log.info("partz: "+partz);
		
		NamedTypedDBObject obj = NamedTypedDBObject.getObject(partz);
		Properties prop = (Properties) req.getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		Subject currentUser = ShiroUtils.getSubject(prop);
		ShiroUtils.checkPermission(currentUser, ActionType.SELECT_ANY.name(), obj.getFullObjectName());
		
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
			
			String sqlParam = req.getParameter("sql");
			//XXX req.getParameterValues("keycols"); ??
			String keyColsParam = req.getParameter("keycols");
			List<String> keyCols = Utils.getStringList(keyColsParam, ",");
			
			String sql = sqlParam;

			DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(connSource.getMetaData());
			DiffSyntax ds = getSyntax(obj, feat, prop, req);
			
			resp.setContentType(ds.getMimeType());
			runDiff(connSource, connTarget, sql, obj, keyCols, modelIdSource, modelIdTarget, ds, resp.getWriter());
		}
		finally {
			ConnectionUtil.closeConnection(connSource);
			ConnectionUtil.closeConnection(connTarget);
			resp.getWriter().flush();
		}
	}

}
