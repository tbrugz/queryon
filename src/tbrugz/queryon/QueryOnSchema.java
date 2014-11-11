package tbrugz.queryon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ModelUtils;
import tbrugz.sqldump.dbmodel.SchemaModel;

/*
 * XXX: add POST method?
 */
public class QueryOnSchema extends HttpServlet {

	private static final long serialVersionUID = 1L;
	static final Log log = LogFactory.getLog(QueryOnSchema.class);
	
	List<String> parseQS(HttpServletRequest req) {
		String varUrl = req.getPathInfo();
		if(varUrl==null) { throw new BadRequestException("URL (path-info) must not be null"); }
		
		String[] URIparts = varUrl.split("/");
		List<String> URIpartz = new ArrayList<String>( Arrays.asList(URIparts) );
		//log.debug("urlparts: "+URIpartz);
		if(URIpartz.size()<3) { throw new BadRequestException("URL must have at least 2 parts"); }

		String objectTmp = URIpartz.get(0);
		if(objectTmp == null || objectTmp.equals("")) {
			URIpartz.remove(0);
		}
		
		return URIpartz;
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			doService(req, resp);
		}
		catch(BadRequestException e) {
			resp.setStatus(e.getCode());
			resp.getWriter().write(e.getMessage());
		}
		catch(ServletException e) {
			//e.printStackTrace();
			throw e;
		}
	}
	
	/*
	 * XXX: output syntax? don't think so...
	 */
	protected void doService(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		List<String> partz = parseQS(req);
		if(partz.size()<2) {
			throw new BadRequestException("Malformed URL");
		}
		log.info("partz: "+partz);
		
		DBObjectType type = null;
		String objType = partz.get(0).toUpperCase();
		String fullObjectName = partz.get(1);
		String schemaName = null;
		String objectName = fullObjectName;
		String modelId = req.getParameter("model");
		
		if(objectName.contains(".")) {
			String[] onPartz = objectName.split("\\.");
			if(onPartz.length!=2) {
				throw new BadRequestException("Malformed object name: "+objectName);
			}
			schemaName = onPartz[0];
			objectName = onPartz[1];
		}
		try {
			type = DBObjectType.valueOf(objType);
		}
		catch(IllegalArgumentException e) {
			throw new BadRequestException("Unknown object type: "+objType);
		}
		
		Properties prop = (Properties) req.getSession().getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		Subject currentUser = ShiroUtils.getSubject(prop);
		ShiroUtils.checkPermission(currentUser, objType+":SHOW", fullObjectName);
		
		SchemaModel model = SchemaModelUtils.getModel(req.getSession().getServletContext(), modelId);

		Collection<? extends DBIdentifiable> dbids = ModelUtils.getCollectionByType(model, type);
		//System.out.println(">>>>>>> "+dbids+" >>>>> "+(schemaName!=null?schemaName+".":"")+objectName);
		DBIdentifiable dbid = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(dbids, type, schemaName, objectName);
		if(dbid==null) {
			throw new BadRequestException("Object "+(schemaName!=null?schemaName+".":"")+objectName+" of type "+type+" not found", 404);
		}
		
		resp.getWriter().write(dbid.getDefinition(true));
	}
	
}
