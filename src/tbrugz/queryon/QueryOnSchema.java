package tbrugz.queryon;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

import tbrugz.queryon.exception.NotFoundException;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ModelUtils;
import tbrugz.sqldump.dbmodel.SchemaModel;

/*
 * XXX: add POST method?
 */
public class QueryOnSchema extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Log log = LogFactory.getLog(QueryOnSchema.class);

	public static final String MIME_SQL = "text/plain"; //"application/sql"; - browsers may "download"
	
	String lastDialect;
	
	public static List<String> parseQS(HttpServletRequest req) {
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
			resp.setContentType(AbstractHttpServlet.MIME_TEXT);
			resp.getWriter().write(e.getMessage());
		}
		catch(ServletException e) {
			//e.printStackTrace();
			throw e;
		} catch (ClassNotFoundException e) {
			throw new ServletException(e);
		} catch (SQLException e) {
			throw new ServletException(e);
		} catch (NamingException e) {
			e.printStackTrace();
			throw new ServletException(e);
		}
	}
	
	/*
	 * XXX: output syntax? don't think so...
	 */
	protected void doService(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, ClassNotFoundException, SQLException, NamingException {
		List<String> partz = parseQS(req);
		if(partz.size()<2) {
			throw new BadRequestException("Malformed URL");
		}
		log.info("partz: "+partz);
		
		/*
		DBObjectType type = null;
		String objType = partz.get(0).toUpperCase();
		String fullObjectName = partz.get(1);
		String schemaName = null;
		String objectName = fullObjectName;
		
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
		*/
		
		NamedTypedDBObject obj = NamedTypedDBObject.getObject(partz);
		
		String modelId = SchemaModelUtils.getModelId(req);
		Properties prop = (Properties) req.getSession().getServletContext().getAttribute(QueryOn.ATTR_PROP);
		
		Subject currentUser = ShiroUtils.getSubject(prop);
		ShiroUtils.checkPermission(currentUser, obj.getType()+":"+QOnPrivilegeType.SHOW, obj.getFullObjectName());
		
		SchemaModel model = SchemaModelUtils.getModel(req.getSession().getServletContext(), modelId);
		if(model==null) {
			throw new BadRequestException("Unknown model: "+modelId);
		}

		dumpObject(obj.getType(), prop, modelId, model, obj.getSchemaName(), obj.getName(), resp);
		
		/*
		String script = null;
		if(dbid instanceof Table) {
			Table t = (Table) dbid;
			script = t.getDefinition(true, true, false, false, false, null, / *List<FK>* / null);
		}
		else {
			script = dbid.getDefinition(true);
		}
		resp.getWriter().write(script);
		*/
	}
	
	public DBIdentifiable getObject(DBObjectType type, String schemaName, String objectName, SchemaModel model, Properties prop, String modelId) throws SQLException, ClassNotFoundException, NamingException {
		Collection<? extends DBIdentifiable> dbids = ModelUtils.getCollectionByType(model, type);
		//System.out.println(">>>>>>> "+dbids+" >>>>> "+(schemaName!=null?schemaName+".":"")+objectName);
		//DBObjectType type4Filter = type4filter(type);
		return DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(dbids, type, schemaName, objectName);
	}
	
	void dumpObject(DBObjectType type, Properties prop, String modelId, SchemaModel model, String schemaName, String objectName, HttpServletResponse resp) 
			throws ClassNotFoundException, SQLException, NamingException, IOException {
		/*
		Collection<? extends DBIdentifiable> dbids = ModelUtils.getCollectionByType(model, type);
		//System.out.println(">>>>>>> "+dbids+" >>>>> "+(schemaName!=null?schemaName+".":"")+objectName);
		DBObjectType type4Filter = type4filter(type);
		DBIdentifiable dbid = DBIdentifiable.getDBIdentifiableByTypeSchemaAndName(dbids, type4Filter, schemaName, objectName);
		*/
		DBObjectType type4Filter = type4filter(type);
		DBIdentifiable dbid = getObject(type4Filter, schemaName, objectName, model, prop, modelId);
		if(dbid==null) {
			throw new NotFoundException("Object "+(schemaName!=null?schemaName+".":"")+objectName+" of type "+type4Filter+" not found");
		}
		
		resp.setContentType(MIME_SQL);
		resp.getWriter().write(dbid.getDefinition(true));
		lastDialect = model.getSqlDialect();
	}
	
	DBObjectType type4filter(DBObjectType type) {
		switch (type) {
		case PACKAGE: return DBObjectType.PACKAGE_BODY;
		default:
			return type;
		}
	}
	
	public String getLastDialect() {
		return lastDialect;
	}
	
}
