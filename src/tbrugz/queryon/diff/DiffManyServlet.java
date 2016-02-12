package tbrugz.queryon.diff;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.AbstractHttpServlet;
import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.DBUtil;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.QueryOnSchema;
import tbrugz.queryon.SchemaModelUtils;
import tbrugz.queryon.ShiroUtils;
import tbrugz.queryon.QueryOn.ActionType;
import tbrugz.sqldiff.SQLDiff;
import tbrugz.sqldump.util.CategorizedOut;
import tbrugz.sqldump.util.ParametrizedProperties;

public class DiffManyServlet extends AbstractHttpServlet {

	private static final long serialVersionUID = 1L;
	static final Log log = LogFactory.getLog(DiffManyServlet.class);

	@Override
	public void doProcess(HttpServletRequest req, HttpServletResponse resp) throws ClassNotFoundException, SQLException, NamingException, IOException, JAXBException, XMLStreamException, InterruptedException, ExecutionException {
		
		List<String> partz = QueryOnSchema.parseQS(req);
		if(partz.size()<1) {
			throw new BadRequestException("Malformed URL");
		}
		log.info("partz: "+partz);
		
		String schemas = partz.get(0); // comma separated schema names
		String syntax = partz.get(1); // valid:: json, xml, patch
		
		Properties prop = (Properties) req.getSession().getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		Subject currentUser = ShiroUtils.getSubject(prop);
		ShiroUtils.checkPermission(currentUser, ActionType.SELECT_ANY.name(), ActionType.SELECT_ANY.name());
		
		String modelIdFrom = SchemaModelUtils.getModelId(req, DiffServlet.PARAM_MODEL_FROM);
		String modelIdTo = SchemaModelUtils.getModelId(req, DiffServlet.PARAM_MODEL_TO);
		if(modelIdFrom.equals(modelIdTo)) {
			log.warn("equal models being compared [id="+modelIdFrom+"], no diffs can be generated");
		}
		
		try {
			//XXX params: schemas, objectTypes - define properties to sqldump
			
			ParametrizedProperties pp = new ParametrizedProperties();
			pp.putAll(prop);
			pp.put("sqldiff.target", modelIdFrom);
			pp.put("sqldiff.source", modelIdTo);
			pp.put("sqldiff."+modelIdFrom+".connpropprefix", DBUtil.getDBConnPrefix(prop, modelIdFrom));
			pp.put("sqldiff."+modelIdTo+".connpropprefix", DBUtil.getDBConnPrefix(prop, modelIdTo));
			//pp.put(DBUtil.getDBConnPrefix(prop, modelIdFrom)+".diffgrabclass","JDBCSchemaGrabber");
			//pp.put(DBUtil.getDBConnPrefix(prop, modelIdTo)+".diffgrabclass","JDBCSchemaGrabber");
			pp.put(DBUtil.getDBConnPrefix(prop, modelIdFrom)+".grabclass","JDBCSchemaGrabber");
			pp.put(DBUtil.getDBConnPrefix(prop, modelIdTo)+".grabclass","JDBCSchemaGrabber");
			pp.put("sqldiff.dodatadiff","false");
			pp.put("sqldump.schemagrab.schemas", schemas); //XXX param: schemas, validate?
			//XXX: param: objecttypes (sqldump grab properties) - 'sqldiff.typestodiff' also?
			
			SQLDiff sqldiff = new SQLDiff();
			sqldiff.setProperties(pp);
			
			sqldiff.procProterties();
			// using 'syntax' param
			if("xml".equals(syntax)) {
				sqldiff.setXmlWriter(resp.getWriter());
			}
			else if("json".equals(syntax)) {
				sqldiff.setJsonWriter(resp.getWriter());
			}
			else if("patch".equals(syntax)) {
				sqldiff.setPatchWriter(resp.getWriter());
			}
			else if("sql".equals(syntax)) {
				// "sql": every DDL in plain text...
				CategorizedOut cout = new CategorizedOut(resp.getWriter(), null);
				sqldiff.setCategorizedOut(cout);
			}
			else {
				throw new BadRequestException("unknown syntax: "+syntax);
			}
			
			int lastDiffCount = sqldiff.doIt();
			log.info("diff count: "+lastDiffCount);
		}
		finally {
		}
	}
	
}
