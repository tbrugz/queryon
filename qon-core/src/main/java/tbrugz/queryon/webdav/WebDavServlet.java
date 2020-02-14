package tbrugz.queryon.webdav;

import java.beans.IntrospectionException;
import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.Constraint;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.util.ConnectionUtil;
import tbrugz.sqldump.util.Utils;

public class WebDavServlet extends BaseApiServlet {

	private static final long serialVersionUID = 1L;
	
	private static final Log log = LogFactory.getLog(WebDavServlet.class);
	
	public static final String METHOD_PROPFIND = "PROPFIND";
	
	public static final String HEADER_DEPTH = "Depth";
	
	public static final String DEPTH_INFINITY = "infinity";
	
	public static final int STATUS_MULTI_STATUS = 207;
	
	public final static int DEFAULT_LIMIT = 1000;
	
	boolean multiModel;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		multiModel = isMultiModel();
		
		defaultLimit = DEFAULT_LIMIT;
		maxLimit = defaultLimit;
	}
	
	@Override
	protected WebDavRequest getRequestSpec(HttpServletRequest req) throws ServletException, IOException {
		//return new RequestSpec(dsutils, req, prop, /*prefixesToIgnore*/ 0, "xml", true, 0, null);
		return new WebDavRequest(dsutils, req, prop, multiModel);
	}
	
	boolean isMultiModel() {
		List<String> mids = getDeclaredModels();
		if(mids==null) {
			return false;
		}
		return mids.size()>1;
	}
	
	@Override
	protected boolean isStatusObject(String name) {
		return "".equals(name);
	}
	
	protected void doPropFind(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, ClassNotFoundException, IntrospectionException, SQLException, NamingException {
		WebDavRequest wdreq = getRequestSpec(req);
		List<Object> urlParts = wdreq.getParams();
		
		//log.info("urlParts = "+urlParts+" / object = "+wdreq.getObject());
		//propFindShowRequest(req);
		
		if(multiModel && wdreq.getModelId()==null) {
			log.info("doPropFind: list modelIds");
			List<WebDavResource> resl = getResourcesFromKeys(SchemaModelUtils.getModelIds(req.getServletContext()));
			
			resl.add(0, getBaseResourceCollection());
			setBaseHrefToResourceList(resl, req.getRequestURI());
			writePaths(resl, resp);
			return;
		}

		//log.info("urlParts = "+urlParts+" / object = "+wdreq.getObject()+" / header[Authorization] = "+req.getHeader("Authorization"));
		SchemaModel model = SchemaModelUtils.getModel(req.getServletContext(), wdreq.getModelId());
		
		Subject currentUser = ShiroUtils.getSubject(prop, req);
		log.info("currentUser = "+currentUser.getPrincipal()+" ; isAuthenticated = "+currentUser.isAuthenticated());
		
		// http://www.webdav.org/specs/rfc4918.html#HEADER_Depth
		int depth = getDepth(req);
		if(depth>1) {
			throw new BadRequestException("depth > 1 ["+depth+"] not implemented");
		}
		
		if(wdreq.getObject().isEmpty()) {
			log.info("doPropFind: list tables");
			//XXX check for depth == 0
			List<Relation> rels = getRelationsWithPk(model);
			rels = filterRelationsByPermission(rels, currentUser, PrivilegeType.SELECT);
			//List<WebDavResource> resl = getResources(rels, baseHref);
			List<WebDavResource> resl = getResourcesFromRelations(rels);
			
			resl.add(0, getBaseResourceCollection());
			setBaseHrefToResourceList(resl, req.getRequestURI());
			writePaths(resl, resp);
			return;
		}
		
		Relation r = SchemaModelUtils.getRelation(model, wdreq.getObject(), true);
		if(r==null) {
			log.warn("null object: "+wdreq.getObject());
			throw new NotFoundException("resource '"+getPathInfo(req)+"' does not exists");
		}
		Constraint pk = SchemaModelUtils.getPK(r);
		if(pk==null) {
			log.warn("null PK: "+wdreq.getObject());
			throw new BadRequestException("object '"+wdreq.getObject()+"' has no unique key");
		}
		
		preprocessParameters(wdreq, r, pk);
		
		int positionalParametersNeeded = pk.getUniqueColumns().size();
		String baseHref = req.getRequestURI();
		log.info("doPropFind: object = "+wdreq.getObject()+" ; params = "+wdreq.getParams()+" ; column = "+wdreq.getColumn()+" / positionalParametersNeeded = "+positionalParametersNeeded);
		
		if(urlParts.size() <= positionalParametersNeeded + 1) {
			List<WebDavResource> resl = null;
			if(checkPermission(r, currentUser, PrivilegeType.SELECT)) {
			Connection conn = null;
			try {
				conn = DBUtil.initDBConn(prop, wdreq.getModelId());
				
				if(urlParts.size() == 0) {
					// list contents (1st key level)
					//if(checkPermission(r, pk, urlParts.size(), currentUser, PrivilegeType.SELECT)) {
					resl = doListResources(r, pk, wdreq, currentUser, conn, model.getSqlDialect());
					resl.add(0, getBaseResourceCollection());
					//}
				}
				else if(urlParts.size() < positionalParametersNeeded) {
					// check if key exists...
					checkResourcesExists(r, pk, wdreq, currentUser, conn, model.getSqlDialect());
					// list contents
					//if(checkPermission(r, pk, urlParts.size(), currentUser, PrivilegeType.SELECT)) {
					resl = doListResources(r, pk, wdreq, currentUser, conn, model.getSqlDialect());
					resl.add(0, getBaseResourceCollection());
					//}
				}
				else if(urlParts.size() == positionalParametersNeeded) {
					// full key defined - all parameters defined
					//resl = getResourcesFromKeys(r.getColumnNames());
					checkUniqueResource(r, pk, wdreq, currentUser, conn, model.getSqlDialect());

					if(wdreq.getColumn()!=null) {
						//resl = getResourceFromRelationColumn(r, wdreq.getColumns().get(0));
						resl = getResourcesFromRelationColumns(r, pk, wdreq.getColumn(), wdreq, conn);
						// baseHref: remove after last slash
						baseHref = baseHref.substring(0, baseHref.lastIndexOf("/")+1);
					}
					else {
						// XXX do *not* return columns with NULL value? or with size = 0...
						//resl = getResourcesFromRelationColumns(r);
						resl = getResourcesFromRelationColumns(r, pk, null, wdreq, conn);
						resl.add(0, getBaseResourceCollection());
					}
				}
				else {
					throw new IllegalStateException("urlParts.size() == "+urlParts.size()+" // positionalParametersNeeded + 1 == "+(positionalParametersNeeded + 1));
				}
			}
			finally {
				ConnectionUtil.closeConnection(conn);
			}
			}
			//log.info("getBaseHref= "+getBaseHref(req)+" ; getContextPath= "+req.getContextPath()+" ; getRequestURI="+req.getRequestURI()+" ; getServletPath="+req.getServletPath());
			
			setBaseHrefToResourceList(resl, baseHref);
			writePaths(resl, resp);
		}
		else {
			throw new NotFoundException("resource '"+getPathInfo(req)+"' does not exists");
		}
	}
	
	List<Relation> filterRelationsByPermission(List<Relation> rels, Subject subject, PrivilegeType privilege) {
		List<Relation> ret = new ArrayList<Relation>();
		for(Relation r: rels) {
			String permission = r.getRelationType()+":"+privilege+":"+r.getSchemaName()+":"+r.getName();
			if(subject.isPermitted(permission)) {
				//log.debug("permitted: "+permission);
				ret.add(r);
			}
			else {
				//log.debug("not permitted: "+permission);
			}
		}
		return ret;
	}

	boolean checkPermission(Relation r, Subject subject, PrivilegeType privilege) {
		String permission = r.getRelationType()+":"+privilege+":"+r.getSchemaName()+":"+r.getName();
		return subject.isPermitted(permission);
	}
	
	/*boolean checkPermission(Relation r, Constraint pk, int pkCol, Subject subject, PrivilegeType privilege) {
		String permission = r.getRelationType()+":"+privilege+":"+r.getSchemaName()+":"+r.getName()+":"+pk.getUniqueColumns().get(pkCol);
		return subject.isPermitted(permission);
	}*/
	
	@Override
	protected void doStatus(SchemaModel model, String statusTypeStr, RequestSpec reqspec, Subject currentUser,
			HttpServletResponse resp) throws IntrospectionException, SQLException, IOException, ServletException,
			ClassNotFoundException, NamingException {
		// do nothing on GET / HEAD for collecions... (?) at least do not throw 404 error
		// http://www.webdav.org/specs/rfc4918.html#rfc.section.9.4
	}

	@Override
	protected void doDelete(Relation relation, RequestSpec reqspec, Subject currentUser, HttpServletResponse resp)
			throws ClassNotFoundException, SQLException, NamingException, IOException, ServletException {
		if(reqspec instanceof WebDavRequest) {
			WebDavRequest wdreq = (WebDavRequest) reqspec;
			
			Constraint pk = SchemaModelUtils.getPK(relation);
			preprocessParameters(reqspec, relation, pk);
			
			// if 'column' defined, set column to null. Otherwise, delete row 
			if(wdreq.getColumn()!=null) {
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
		if(DEPTH_INFINITY.equalsIgnoreCase(depth)) {
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
	
	List<WebDavResource> getResourcesFromKeys(Collection<String> keys) {
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
	
	List<WebDavResource> getResourcesFromRelationColumns(Relation r, Constraint pk, String column, RequestSpec reqspec, Connection conn) throws SQLException, IOException {
		// selects column_isnull, column_length for all relation columns...
		List<WebDavResource> ret = new ArrayList<WebDavResource>();
		
		DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(conn.getMetaData());
		List<String> selectCols = new ArrayList<String>();
		List<String> lengthCols = new ArrayList<String>();
		List<String> isNullCols = new ArrayList<String>();
		Map<String, String> typeMap = new HashMap<String, String>();
		
		for(int i=0;i<r.getColumnCount();i++) {
			String cname = r.getColumnNames().get(i);
			if(column!=null && !cname.equals(column)) {
				continue;
			}
			String ctype = r.getColumnTypes().get(i);
			typeMap.put(cname, ctype);
			
			String lengthFunction = feat.sqlLengthFunctionByType(cname, ctype);
			if(lengthFunction!=null) {
				selectCols.add(lengthFunction+" as "+cname+"_LENGTH");
				lengthCols.add(cname);
			}
			else {
				String isNullFunction = feat.sqlIsNullFunction(cname);
				if(isNullFunction!=null) {
					selectCols.add(isNullFunction+" as "+cname+"_ISNULL");
					isNullCols.add(cname);
				}
			}
		}
		
		if(selectCols.size()==0) {
			log.debug("no known functions for length or isnull for relation "+r.getQualifiedName());
			//if(true) throw new RuntimeException("no known functions for length or isnull for relation "+r.getQualifiedName());
			if(column!=null) {
				return getResourceFromRelationColumn(r, column);
			}
			else {
				return getResourcesFromRelationColumns(r);
			}
		}
		
		String projection = Utils.join(selectCols, ", ");
		SQL sql = SQL.createSQL(r, reqspec, null);
		filterByKey(r, reqspec, pk, sql);
		sql.applyProjection(reqspec, projection);
		
		String finalSql = sql.getFinalSql();
		log.debug("getResourcesFromRelationColumns: sql:\n"+finalSql);
		
		PreparedStatement st = conn.prepareStatement(finalSql);
		sql.bindParameters(st);
		
		//boolean applyLimitOffsetInResultSet = !sql.sqlLoEncapsulated;
		ResultSet rs = st.executeQuery();
		
		/*if(applyLimitOffsetInResultSet) {
			rs = new ResultSetLimitOffsetDecorator(rs, getLimit(), reqspec.getOffset());
		}*/
		
		//List<String> keys = new ArrayList<String>();
		
		//DavPrivilege davPrivilege = DavPrivilege.WRITE_CONTENT;
		
		if(rs.next()) {
			for(int i=0;i<lengthCols.size();i++) {
				String col = lengthCols.get(i);
				int length = rs.getInt(col+"_LENGTH"); // if null, returns 0
				//WebDavResource res = new WebDavResource(null, col, getContentType(typeMap.get(col)), length, Collections.singletonList(davPrivilege), false);
				WebDavResource res = new WebDavResource(null, col, getContentType(typeMap.get(col)), length, false);
				ret.add(res);
			}
			for(int i=0;i<isNullCols.size();i++) {
				String col = isNullCols.get(i);
				Object isNullObj = rs.getObject(col+"_ISNULL");
				boolean isNull = getBooleanFromObject(isNullObj);
				//WebDavResource res = new WebDavResource(null, col, getContentType(typeMap.get(col)), isNull?0:1, Collections.singletonList(davPrivilege), false);
				WebDavResource res = new WebDavResource(null, col, getContentType(typeMap.get(col)), isNull?0:1, false);
				ret.add(res);
			}
			if(rs.next()) {
				throw new InternalServerException("more than 1 row?");
			}
		}
		else {
			throw new InternalServerException("no rows?");
		}
		return ret;
	}
	
	boolean getBooleanFromObject(Object o) {
		if(o instanceof Boolean) {
			return ((Boolean) o).booleanValue();
		}
		if(o instanceof Number) {
			return ((Number) o).intValue() > 0;
		}
		if(o instanceof String) {
			String s = ((String) o);
			return s.equalsIgnoreCase("t") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("y") || s.equalsIgnoreCase("yes");
		}
		return false;
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
			/*boolean fullKeyDefined = fullKeyDefined(reqspec, pk);
			if(fullKeyDefined) {
				log.warn("doListResources with fullKeyDefined == true?");
			}*/
			
			//preprocessParameters(reqspec, relation, pk);
			SQL sql = getSelectQuery(relation, reqspec, pk, loStrategy, getUsername(currentUser), defaultLimit, maxLimit, null, isStrictMode());
			String finalSql = sql.getFinalSql();
			log.debug("doListResources: sql:\n"+finalSql);
			
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
			//XXX if not all rows dumped (limited?), throw Exception...
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

	WebDavResource getBaseResourceCollection() {
		return getBaseResourceCollection("");
	}
	
	private WebDavResource getBaseResourceCollection(String pathInfo) {
		/*if(pathInfo==null || pathInfo.isEmpty()) {
			pathInfo = ".";
		}*/
		if(pathInfo==null) {
			pathInfo = "";
		}
		return new WebDavResource(pathInfo, true);
	}
	
	void setBaseHrefToResourceList(List<WebDavResource> resl, String baseHref) {
		for(WebDavResource res: resl) {
			res.setBaseHref(baseHref);
		}
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
				String content = r.serialize("D");
				w.write(content);
				//System.out.print(content);
			}
			log.debug("wrote "+resl.size()+" resources");
		}
		else {
			log.debug("wrote _null_ resources");
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
	
	@Override
	protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// http://www.webdav.org/specs/rfc4918.html#http.headers.for.distributed.authoring
		// http://www.webdav.org/specs/rfc4918.html#dav.compliance.classes
		log.info("doOptions: "+req.getPathInfo());
		resp.addHeader("Allow", "OPTIONS, GET, HEAD, PATCH, POST, PUT, DELETE"); // TRACE ?
		resp.addHeader("Allow", METHOD_PROPFIND);
		//resp.addHeader("Allow", "MKCOL, PROPFIND, PROPPATCH, LOCK, UNLOCK, REPORT, ACL");
		resp.setHeader("DAV", "1"); // basic compliance
		//resp.setHeader("DAV", "1, access-control"); // basic compliance + access control (rfc3744)
		//resp.setHeader("DAV", "1, 2, access-control"); // basic compliance + access control (rfc3744)
		//resp.setHeader("DAV", "1, 2, 3"); // full compliance (locks included)
	}
	
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		//log.info("service: pathInfo = "+req.getPathInfo()+" ; method = "+req.getMethod());
		String method = req.getMethod();
		try {
			if (method.equals(METHOD_PROPFIND)) {
				log.info(">> pathInfo: "+req.getPathInfo()+" ; method: "+req.getMethod());
				doPropFind(req, resp);
			}
			else {
				log.debug(">> pathInfo: "+req.getPathInfo()+" ; method: "+req.getMethod());
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

	/*
	void propFindShowRequest(HttpServletRequest req) throws IOException {
		String body = IOUtil.readFromReader(req.getReader()).trim();
		log.info("depth = "+req.getHeader(HEADER_DEPTH)+" ; body:\n"+body);
	}
	*/
}
