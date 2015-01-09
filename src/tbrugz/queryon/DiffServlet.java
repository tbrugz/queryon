package tbrugz.queryon;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.sqldiff.model.Diff;
import tbrugz.sqldiff.model.TableDiff;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;

/*
 * TODO: apply diff option - add authorization like <type>:APPLYDIFF:<model>:<schema>:<name>
 */
public class DiffServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	static final Log log = LogFactory.getLog(DiffServlet.class);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		//throw new BadRequestException("Only POST allowed", HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			doProcess(req, resp);
		} catch(BadRequestException e) {
			log.warn("BadRequestException: "+e.getMessage());
			resp.setStatus(e.getCode());
			resp.getWriter().write(e.getMessage());
		} /*catch (ServletException e) {
			//e.printStackTrace();
			throw e;
		} */ catch (Exception e) {
			//e.printStackTrace();
			throw new ServletException(e);
		}
	}

	void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException {
		List<String> partz = QueryOnSchema.parseQS(req);
		if(partz.size()<2) {
			throw new BadRequestException("Malformed URL");
		}
		log.info("partz: "+partz);
		
		NamedTypedDBObject obj = NamedTypedDBObject.getObject(partz);
		
		Properties prop = (Properties) req.getSession().getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		// TODOne: diff authorization
		// XXX add <type>:DIFF authorization instead of <type>:SHOW ?
		Subject currentUser = ShiroUtils.getSubject(prop);
		ShiroUtils.checkPermission(currentUser, obj.getType()+":SHOW", obj.getFullObjectName());
		
		String modelIdFrom = SchemaModelUtils.getModelId(req, "modelFrom");
		String modelIdTo = SchemaModelUtils.getModelId(req, "modelTo");
		if(modelIdFrom.equals(modelIdTo)) {
			log.warn("equal models being compared [id="+modelIdFrom+"], no diffs can be generated");
		}
		
		SchemaModel modelFrom = SchemaModelUtils.getModel(req.getSession().getServletContext(), modelIdFrom);
		if(modelFrom==null) {
			throw new BadRequestException("Unknown model (from): "+modelIdFrom);
		}
		SchemaModel modelTo = SchemaModelUtils.getModel(req.getSession().getServletContext(), modelIdTo);
		if(modelTo==null) {
			throw new BadRequestException("Unknown model (to): "+modelIdTo);
		}
		
		QueryOnSchema qos = new QueryOnSchemaInstant(); // XXX use factory? new QueryOnSchema() / QueryOnSchemaInstant() ...
		
		DBIdentifiable dbidFrom = qos.getObject(obj.getType(), obj.getSchemaName(), obj.getName(), modelFrom, prop, modelIdFrom);
		DBIdentifiable dbidTo = qos.getObject(obj.getType(), obj.getSchemaName(), obj.getName(), modelTo, prop, modelIdTo);
		
		if(dbidFrom==null) {
			throw new BadRequestException("Object "+obj+" not found on model "+modelIdFrom);
		}
		if(dbidTo==null) {
			throw new BadRequestException("Object "+obj+" not found on model "+modelIdTo);
		}
		
		if(dbidFrom instanceof Table && dbidTo instanceof Table) {
			Table origTable = (Table) dbidFrom;
			Table newTable = (Table) dbidTo;
			List<Diff> diffs = TableDiff.tableDiffs(origTable, newTable);
			if(diffs.size()==0) {
				log.info("no diffs found");
				//XXX: return 404?
			}

			//SchemaDiff.logInfo(diffs);

			dumpDiffs(diffs, resp);
		}
		else {
			throw new BadRequestException("Object "+obj+" is not a table");
		}
		
	}

	void dumpDiffs(List<Diff> diffs, HttpServletResponse resp) throws IOException {
		String append = ";\n";
		for(Diff d: diffs) {
			resp.getWriter().write(d.getDiff()+append);
		}
	}
	
}
