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
import tbrugz.queryon.DBUtil;
import tbrugz.queryon.NamedTypedDBObject;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.QueryOnSchema;
import tbrugz.queryon.SchemaModelUtils;
import tbrugz.queryon.ShiroUtils;
import tbrugz.queryon.QueryOn.ActionType;
import tbrugz.sqldiff.datadiff.DiffSyntax;
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
		Properties prop = (Properties) req.getSession().getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		Subject currentUser = ShiroUtils.getSubject(prop);
		ShiroUtils.checkPermission(currentUser, ActionType.SELECT_ANY.name(), obj.getFullObjectName());
		
		String modelIdFrom = SchemaModelUtils.getModelId(req, "modelFrom");
		String modelIdTo = SchemaModelUtils.getModelId(req, "modelTo");
		if(modelIdFrom.equals(modelIdTo)) {
			log.warn("equal models being compared [id="+modelIdFrom+"], no diffs can be generated");
		}
		
		Connection connFrom = DBUtil.initDBConn(prop, modelIdFrom);
		Connection connTo = DBUtil.initDBConn(prop, modelIdTo);
		
		try {
			String sqlParam = req.getParameter("sql");
			//XXX req.getParameterValues("keycols"); ??
			String keyColsParam = req.getParameter("keycols");
			List<String> keyCols = Utils.getStringList(keyColsParam, ",");
			
			String sql = sqlParam;
			DiffSyntax ds = getSyntax(obj, prop);
			
			runDiff(connFrom, connTo, sql, obj, keyCols, modelIdFrom, modelIdTo, ds, resp.getWriter());
		}
		finally {
			ConnectionUtil.closeConnection(connFrom);
			ConnectionUtil.closeConnection(connTo);
		}
	}

}
