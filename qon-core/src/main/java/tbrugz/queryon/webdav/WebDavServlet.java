package tbrugz.queryon.webdav;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.ResponseSpec;
import tbrugz.queryon.SQL;
import tbrugz.queryon.api.BaseApiServlet;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.exception.NotFoundException;
import tbrugz.queryon.resultset.ResultSetLimitOffsetDecorator;
import tbrugz.queryon.util.DBUtil;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.queryon.util.ShiroUtils;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.util.ConnectionUtil;

public class WebDavServlet extends BaseApiServlet {

	private static final long serialVersionUID = 1L;
	
	private static final Log log = LogFactory.getLog(WebDavServlet.class);
	
	public static final String METHOD_PROPFIND = "PROPFIND";
	
	public static final String HEADER_DEPTH = "Depth";
	
	public static final int STATUS_MULTI_STATUS = 207;
	
	public final static int DEFAULT_LIMIT = 1000;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		defaultLimit = DEFAULT_LIMIT;
		maxLimit = defaultLimit;
	}
	
	@Override
	protected WebDavRequest getRequestSpec(HttpServletRequest req) throws ServletException, IOException {
		//return new RequestSpec(dsutils, req, prop, /*prefixesToIgnore*/ 0, "xml", true, 0, null);
		return new WebDavRequest(dsutils, req, prop);
	}
	
	protected void doPropFind(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, ClassNotFoundException, IntrospectionException, SQLException, NamingException {
		String modelId = SchemaModelUtils.getModelId(req);
		SchemaModel model = SchemaModelUtils.getModel(req.getServletContext(), modelId);
		
		String pathInfo = getPathInfo(req);
		//log.info("doPropFind: pathInfo = "+pathInfo);
		
		if(pathInfo.isEmpty()) {
			log.info("doPropFind: list tables");
			List<Relation> rels = getRelationsWithPk(model);
			//List<WebDavResource> resl = getResources(rels, baseHref);
			List<WebDavResource> resl = getResourcesFromRelations(rels);
			writePaths(resl, resp);
			return;
		}
		WebDavRequest wdreq = getRequestSpec(req);
		List<Object> urlParts = wdreq.getParams();
		
		Relation r = SchemaModelUtils.getRelation(model, wdreq.getObject(), true);
		if(r==null) {
			log.warn("null object: "+wdreq.getObject());
			throw new NotFoundException("resource '"+pathInfo+"' does not exists");
		}
		Constraint pk = SchemaModelUtils.getPK(r);
		if(pk==null) {
			log.warn("null PK: "+wdreq.getObject());
			throw new BadRequestException("object '"+wdreq.getObject()+"' has no unique key");
		}
		
		// http://www.webdav.org/specs/rfc4918.html#HEADER_Depth
		int depth = getDepth(req);
		if(depth>1) {
			throw new BadRequestException("depth > 1 ["+depth+"] not implemented");
		}
		
		preprocessParameters(wdreq, r, pk);
		
		int positionalParametersNeeded = pk.getUniqueColumns().size();
		log.info("doPropFind: object = "+wdreq.getObject()+" ; params = "+wdreq.getParams()+" ; column = "+wdreq.getColumns()+" / positionalParametersNeeded = "+positionalParametersNeeded);
		
		if(urlParts.size() <= positionalParametersNeeded + 1) {
			List<WebDavResource> resl = null;
			Subject currentUser = ShiroUtils.getSubject(prop, req);
			Connection conn = null;
			try {
				conn = DBUtil.initDBConn(prop, modelId);
				
				if(urlParts.size() == 0) {
					// list contents (1st key level)
					resl = doListResources(r, pk, wdreq, currentUser, conn, model.getSqlDialect());
				}
				else if(urlParts.size() < positionalParametersNeeded) {
					// check if key exists...
					checkResourcesExists(r, pk, wdreq, currentUser, conn, model.getSqlDialect());
					// list contents
					resl = doListResources(r, pk, wdreq, currentUser, conn, model.getSqlDialect());
				}
				else if(urlParts.size() == positionalParametersNeeded) {
					// full key defined - all parameters defined
					//resl = getResourcesFromKeys(r.getColumnNames());
					checkUniqueResource(r, pk, wdreq, currentUser, conn, model.getSqlDialect());

					if(wdreq.getColumns().size()>0) {
						if(wdreq.getColumns().size()>1) {
							throw new InternalServerException("getColumns() > 1 ["+wdreq.getColumns()+"]");
						}
						resl = getResourceFromRelationColumn(r, wdreq.getColumns().get(0));
					}
					else {
						resl = getResourcesFromRelationColumns(r);
					}
					// XXX doList() - return columns with types, length?? - needs function to query char/varchar/text/blob column lengths
				}
				else {
					throw new IllegalStateException("urlParts.size() == "+urlParts.size()+" // positionalParametersNeeded + 1 == "+(positionalParametersNeeded + 1));
				}
				writePaths(resl, resp);
			}
			finally {
				ConnectionUtil.closeConnection(conn);
			}
		}
		else {
			throw new NotFoundException("resource '"+pathInfo+"' does not exists");
			//throw new BadRequestException("number of parameters ["+urlParts.size()+"] bigger than number of required parameters ["+positionalParametersNeeded+"]");
		}
	}
	
	@Override
	protected void doDelete(Relation relation, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp)
			throws ClassNotFoundException, SQLException, NamingException, IOException, ServletException {
		if(reqspec instanceof WebDavRequest) {
			WebDavRequest wdreq = (WebDavRequest) reqspec;
			
			Constraint pk = SchemaModelUtils.getPK(relation);
			preprocessParameters(reqspec, relation, pk);
			
			// if 'column' defined, set column to null. Otherwise, delete row 
			if(wdreq.getColumns().size()>0) {
				if(wdreq.getColumns().size()>1) {
					throw new InternalServerException("getColumns() > 1 ["+wdreq.getColumns()+"]");
				}
				boolean isPermitted = true; // permitted? since DELETE has no per-column permitions, yes
				doUpdate(relation, reqspec, currentUser, isPermitted, resp);
			}
			else {
				super.doDelete(relation, reqspec, currentUser, resp);
			}
		}
		else {
			throw new IllegalArgumentException(reqspec + " not instanceof WebDavRequest");
		}
	}
	
	String getPathInfo(HttpServletRequest req) {
		String pathInfo = req.getPathInfo();
		if(pathInfo==null) {
			return "";
		}
		if(pathInfo.startsWith("/")) {
			pathInfo = pathInfo.substring(1);
		}
		return pathInfo;
	}
	
	int getDepth(HttpServletRequest req) {
		String depth = req.getHeader(HEADER_DEPTH);
		if(depth==null) {
			return 0; // assuming 0
		}
		if("infinity".equalsIgnoreCase(depth)) {
			//return Integer.MAX_VALUE;
			throw new BadRequestException("infinity depth ["+depth+"] not implemented");
		}
		return Integer.parseInt(depth);
	}
	
	@Override
	protected void preprocessParameters(RequestSpec reqspec, Relation relation, Constraint pk) {
		if(reqspec instanceof WebDavRequest) {
			WebDavRequest wdreq = (WebDavRequest) reqspec;
			try {
				wdreq.setUniqueKey(pk);
			} catch (IOException e) {
				throw new InternalServerException("Error setting UK: "+e.getMessage(), e);
			}
		}
		else {
			throw new IllegalArgumentException(reqspec + " not instanceof WebDavRequest");
		}
	}
	
	List<String> getRelationNames(List<Relation> rels) {
		List<String> ret = new ArrayList<String>();
		for(Relation r: rels) {
			ret.add(r.getQualifiedName());
		}
		return ret; 
	}
	
	/*List<WebDavResource> getResources(List<Relation> rels, String baseHref) {
		List<WebDavResource> ret = new ArrayList<WebDavResource>();
		for(Relation r: rels) {
			ret.add(new WebDavResource(baseHref, r.getQualifiedName(), true));
		}
		return ret;
	}*/

	List<WebDavResource> getResourcesFromRelations(List<Relation> rels) {
		List<WebDavResource> ret = new ArrayList<WebDavResource>();
		for(Relation r: rels) {
			ret.add(new WebDavResource(r.getQualifiedName(), true));
		}
		return ret;
	}
	
	List<WebDavResource> getResourcesFromKeys(List<String> keys) {
		List<WebDavResource> ret = new ArrayList<WebDavResource>();
		for(String k: keys) {
			ret.add(new WebDavResource(k, true));
		}
		return ret;
	}

	List<WebDavResource> getResourcesFromRelationColumns(Relation r) {
		List<WebDavResource> ret = new ArrayList<WebDavResource>();
		for(int i=0 ; i < r.getColumnCount() ; i++) {
			String name = r.getColumnNames().get(i);
			String type = r.getColumnTypes().get(i);
			WebDavResource res = new WebDavResource(null, name, getContentType(type), false);
			//log.info(">> "+res+" ; "+type+" / "+getContentType(type));
			ret.add(res);
		}
		return ret;
	}

	List<WebDavResource> getResourceFromRelationColumn(Relation r, String column) {
		List<WebDavResource> ret = new ArrayList<WebDavResource>();
		int idx = r.getColumnNames().indexOf(column);
		if(idx>=0) {
			String type = r.getColumnTypes().get(idx);
			WebDavResource res = new WebDavResource(null, column, getContentType(type), false);
			//log.info(">> "+res+" ; "+type+" / "+getContentType(type));
			ret.add(res);
		}
		else {
			throw new NotFoundException("relation '"+r.getQualifiedName()+"' has no column '"+column+"'");
		}
		return ret;
	}

	protected void checkUniqueResource(Relation relation, Constraint pk, RequestSpec reqspec, Subject currentUser, Connection conn, String sqlDialect) {
		List<WebDavResource> resl = doListResources(relation, pk, reqspec, currentUser, conn, sqlDialect);
		if(resl.size()!=1) {
			throw new NotFoundException("list should contain 1 object but has "+resl.size());
		}
		//log.info("list should contain 1 object AND has "+resl.size());
	}

	protected void checkResourcesExists(Relation relation, Constraint pk, RequestSpec reqspec, Subject currentUser, Connection conn, String sqlDialect) {
		List<WebDavResource> resl = doListResources(relation, pk, reqspec, currentUser, conn, sqlDialect);
		if(resl.size()<1) {
			throw new NotFoundException("list should contain objects but has "+resl.size());
		}
		//log.info("list should contain objects AND has "+resl.size());
	}
	
	/*protected WebDavResource doGetResource(Relation relation, Constraint pk, RequestSpec reqspec, Subject currentUser, Connection conn, String sqlDialect) {
		List<WebDavResource> resl = doListResources(relation, pk, reqspec, currentUser, conn, sqlDialect);
		if(resl.size()!=1) {
			return null;
		}
		return resl.get(0);
	}*/
	
	protected List<WebDavResource> doListResources(Relation relation, Constraint pk, RequestSpec reqspec, Subject currentUser, Connection conn, String sqlDialect) {
		try {
			if(log.isDebugEnabled()) {
				ConnectionUtil.showDBInfo(conn.getMetaData());
			}
			
			LimitOffsetStrategy loStrategy = LimitOffsetStrategy.getDefaultStrategy(sqlDialect);
			boolean fullKeyDefined = fullKeyDefined(reqspec, pk);
			if(fullKeyDefined) {
				log.warn("doListResources with fullKeyDefined == true?");
			}
			
			//preprocessParameters(reqspec, relation, pk);
			SQL sql = getSelectQuery(relation, reqspec, pk, loStrategy, getUsername(currentUser), defaultLimit, maxLimit, null, isStrictMode());
			String finalSql = sql.getFinalSql();
			log.debug("sql: "+finalSql);
			
			PreparedStatement st = conn.prepareStatement(finalSql);
			sql.bindParameters(st);
			
			boolean applyLimitOffsetInResultSet = !sql.sqlLoEncapsulated;
			ResultSet rs = st.executeQuery();
			
			if(applyLimitOffsetInResultSet) {
				rs = new ResultSetLimitOffsetDecorator(rs, getLimit(), reqspec.getOffset());
			}
			
			List<String> keys = new ArrayList<String>();
			
			while(rs.next()) {
				keys.add(rs.getString(1));
			}
			return getResourcesFromKeys(keys);
		}
		catch(Exception e) {
			log.warn("Exception: "+e);
			throw new RuntimeException(e);
		}
	}
	
	int getLimit() {
		if(defaultLimit!=null) {
			return Math.min(defaultLimit, maxLimit);
		}
		return maxLimit;
	}
	
	void writePaths(List<WebDavResource> resl, HttpServletResponse resp) throws IOException {
		resp.setStatus(STATUS_MULTI_STATUS);
		resp.setContentType(ResponseSpec.MIME_TYPE_XML); // "application/xml"
		//resp.setStatus(sc, sm);
		Writer w = resp.getWriter();
		w.write("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" + 
				"<D:multistatus xmlns:D=\"DAV:\">\n");
		if(resl!=null) {
			for(WebDavResource r: resl) {
				w.write(r.serialize("D"));
			}
		}
		w.write("</D:multistatus>\n");
	}
	
	List<Relation> getRelationsWithPk(SchemaModel model) {
		List<Relation> rels = new ArrayList<Relation>();
		for(Relation r: model.getTables()) {
			Constraint pk = SchemaModelUtils.getPK(r);
			if(pk!=null) {
				rels.add(r);
			}
		}
		for(Relation r: model.getViews()) {
			Constraint pk = SchemaModelUtils.getPK(r);
			if(pk!=null) {
				rels.add(r);
			}
		}
		return rels;
	}
	
	/*@Override
	protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		log.warn("doOptions: "+req.getPathInfo());
		resp.setHeader("Allow", "DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT"); //TRACE
	}*/
	
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		//log.info("service: pathInfo = "+req.getPathInfo()+" ; method = "+req.getMethod());
		String method = req.getMethod();
		try {
			if (method.equals(METHOD_PROPFIND)) {
				doPropFind(req, resp);
			}
			else {
				super.service(req, resp);
			}
		}
		catch(InternalServerException e) {
			log.warn("InternalServerException: "+e, e);
			handleException(req, resp, e);
		}
		catch(BadRequestException e) {
			log.warn("BadRequestException: "+e); //,e
			handleException(req, resp, e);
		}
		catch (RuntimeException e) {
			log.warn("RuntimeException: "+e, e);
			throw e;
		}
		catch(NamingException e) {
			log.warn("NamingException: "+e);
			throw new ServletException(e);
		}
		catch (ClassNotFoundException e) {
			log.warn("ClassNotFoundException: "+e);
			throw new ServletException(e);
		}
		catch (IntrospectionException e) {
			log.warn("IntrospectionException: "+e);
			throw new ServletException(e);
		}
		catch (SQLException e) {
			log.warn("SQLException: "+e);
			throw new ServletException(e);
		}
	}
	
	@Override
	protected boolean isStrictMode() {
		return true;
	}
	
	String getContentType(String ctype) {
		if(ctype==null) {
			return ResponseSpec.MIME_TYPE_TEXT_PLAIN;
		}
		String upper = ctype.toUpperCase();
		
		/*boolean isInt = DBUtil.INT_COL_TYPES_LIST.contains(upper);
		if(isInt) {
			return ResponseSpec.MIME_TYPE_TEXT_PLAIN;
		}
		boolean isFloat = DBUtil.FLOAT_COL_TYPES_LIST.contains(upper);
		if(isFloat) {
			return ResponseSpec.MIME_TYPE_TEXT_PLAIN;
		}
		boolean isDate = DBUtil.DATE_COL_TYPES_LIST.contains(upper);
		if(isDate) {
			return ResponseSpec.MIME_TYPE_TEXT_PLAIN;
		}
		boolean isBoolean = DBUtil.BOOLEAN_COL_TYPES_LIST.contains(upper);
		if(isBoolean) {
			return "Edm.Boolean";
		}*/
		boolean isBlob = DBUtil.BLOB_COL_TYPES_LIST.contains(upper);
		if(isBlob) {
			return ResponseSpec.MIME_TYPE_OCTET_SREAM;
		}
		
		return ResponseSpec.MIME_TYPE_TEXT_PLAIN;
	}

}
