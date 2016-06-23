package tbrugz.queryon;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.datadump.DumpSyntaxInt;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.util.ParametrizedProperties;
import tbrugz.sqldump.util.StringUtils;
import tbrugz.sqldump.util.Utils;

/**
 * see: /web/doc/api.md
 */
/*
 * TODOne: Recspec: init new dumpsyntax if parameters are present
 * 
 * XXXdone: add ResponseSpec? special-headers-coinvention??
 *  x X-ResultSet-Limit <n>
 *  x X-Execute-ReturnCount <n>
 *  x X-Warning-Execute TooManyReturnParams ReturnCount=<n> -> X-Execute-ReturnCount
 *  x X-Warning-UnknownColumn <message>
 * http://stackoverflow.com/questions/3561381/custom-http-headers-naming-conventions
 * 
 * XXXxx do a select on the inserted/updated row (req.getParameter("doselect"))? maybe use st.getGeneratedKeys() instead...
 */
public class RequestSpec {
	static final Log log = LogFactory.getLog(RequestSpec.class);
	
	/*public class Filter {
		final String id;
		final boolean multiple;
		final String prefixAccept;
		final Map<String, String[]> map;
		
		public Filter(String id, boolean multiple, Map map) {
			this.id = id;
			this.multiple = multiple;
			this.prefixAccept = id+":";
			this.map = map;
		}
		
		boolean accept(String s) {
			return s.startsWith(prefixAccept);
		}
	}*/
	
	public static final String SYNTAXES_INFO_RESOURCE = "/tbrugz/queryon/syntaxes/syntaxinfo.properties";
	
	static final String MULTIPART = "multipart/form-data";
	
	final HttpServletRequest request; //XXX: private & add getAttribute/setAttribute??

	final String httpMethod;
	public final String modelId;
	public final String object;
	final int offset, limit;
	final String loStrategy;
	final String contentType;
	
	// data manipulation (DML) properties
	final Integer minUpdates, maxUpdates;
	
	final List<String> columns = new ArrayList<String>();
	final List<String> params = new ArrayList<String>();
	final String outputTypeStr;
	final DumpSyntaxInt outputSyntax;
	final boolean distinct;
	
	// 'eq', 'ne', 'gt', 'lt', 'ge', 'le'? see: http://en.wikipedia.org/wiki/Relational_operator
	// 'in', 'nin - not in', 'null', 'nnull - not null', 'like', 'not like', 'between' - see: http://en.wikipedia.org/wiki/SQL#Operators
	
	// unique value filters
	final Map<String, String> filterEquals = new HashMap<String, String>();
	final Map<String, String> filterNotEquals = new HashMap<String, String>();
	final Map<String, String> filterGreaterThan = new HashMap<String, String>();
	final Map<String, String> filterGreaterOrEqual = new HashMap<String, String>();
	final Map<String, String> filterLessThan = new HashMap<String, String>();
	final Map<String, String> filterLessOrEqual = new HashMap<String, String>();
	
	// multiple value filters
	final Map<String, String[]> filterIn = new HashMap<String, String[]>();
	final Map<String, String[]> filterNotIn = new HashMap<String, String[]>(); // not in (fnin)
	final Map<String, String[]> filterLike = new HashMap<String, String[]>();
	final Map<String, String[]> filterNotLike = new HashMap<String, String[]>();
	
	//XXX: add filters: is null (fnull), is not null (fnn/fnnull), between (btwn)?
	
	final Map<String, String> updateValues = new HashMap<String, String>();
	final Map<String, Part> updatePartValues = new HashMap<String, Part>();

	final List<String> orderCols = new ArrayList<String>();
	final List<String> orderAscDesc = new ArrayList<String>();
	
	final boolean showDebugInfo = false;

	// syntax properties resource
	static final Properties syntaxProperties = new Properties();
	static {
		try {
			syntaxProperties.load(RequestSpec.class.getResourceAsStream(SYNTAXES_INFO_RESOURCE));
		} catch (Exception e) {
			log.warn("Error loading resource '"+SYNTAXES_INFO_RESOURCE+"'", e);
			e.printStackTrace();
		}
	}
	
	// blob/download fields
	final String uniValueCol;
	final String uniValueMimetype;
	final String uniValueMimetypeCol;
	final String uniValueFilename;
	final String uniValueFilenameCol;
	
	final static int DEFAULT_LIMIT = 100;
	final static int DEFAULT_MAX_UPDATES = 1;
	final static int DEFAULT_MIN_UPDATES = 1;
	
	// file extensions to be ignored by RequestSpec
	final static String[] standardFileExt = { "blob" };
	
	public RequestSpec(DumpSyntaxUtils dsutils, HttpServletRequest req, Properties prop) throws ServletException, IOException {
		this(dsutils, req, prop, 0);
	}
	
	public RequestSpec(DumpSyntaxUtils dsutils, HttpServletRequest req, Properties prop, int prefixesToIgnore) throws ServletException, IOException {
		this.request = req;
		
		contentType = req.getContentType();
		
		/* 
		 * http://stackoverflow.com/a/37125568/616413 - using-put-method-in-html-form
		 * http://programmers.stackexchange.com/questions/114156/why-are-there-are-no-put-and-delete-methods-on-html-forms
		 * http://laraveldaily.com/theres-no-putpatchdelete-method-or-how-to-build-a-laravel-form-manually/
		 * http://symfony.com/doc/current/cookbook/routing/method_parameters.html
		 */
		String method = req.getParameter("_method"); //XXXdone: _method
		//XXX: may method be changed? property?
		if(method!=null) {
			httpMethod = method;
		}
		else {
			httpMethod = req.getMethod();
		}
		
		this.modelId = SchemaModelUtils.getModelId(req);
		//TODO test if model with this id exists
		
		String varUrl = req.getPathInfo();
		if(varUrl==null) { throw new BadRequestException("URL (path-info) must not be null"); }
		
		String[] URIparts = varUrl.split("/");
		List<String> URIpartz = new ArrayList<String>( Arrays.asList(URIparts) );
		log.debug("urlparts: "+URIpartz);
		if(URIpartz.size()<2) { throw new BadRequestException("URL must have at least 1 part"); }

		/*
		String lastURIPart = URIpartz.remove(URIpartz.size()-1);
		int lastDotIndex = lastURIPart.lastIndexOf('.');
		if(lastDotIndex > -1) {
			String outputTypeStrTmp = lastURIPart.substring(lastDotIndex+1);
			// test for known syntax
			if(dsutils.getDumpSyntax(outputTypeStrTmp, prop) != null) {
				outputTypeStr = outputTypeStrTmp;
				lastURIPart = lastURIPart.substring(0, lastDotIndex);
			}
			else {
				outputTypeStr = null;
				List<String> ignoreFileExt = Arrays.asList(standardFileExt);
				if(ignoreFileExt.contains(outputTypeStrTmp)) {
					lastURIPart = lastURIPart.substring(0, lastDotIndex);
				}
			}
		}
		else {
			outputTypeStr = null;
		}
		*/
		String lastURIPart = URIpartz.remove(URIpartz.size()-1);
		outputTypeStr = getSyntax(lastURIPart, dsutils, prop);
		
		if(outputTypeStr!=null) {
			lastURIPart = lastURIPart.substring(0, lastURIPart.length()-1-outputTypeStr.length());
		}

		URIpartz.add( lastURIPart );
		//log.info("output-type: "+outputTypeStr+"; new urlparts: "+URIpartz);
		
		String objectTmp = URIpartz.remove(0);
		if(objectTmp == null || objectTmp.equals("")) {
			//first part may be empty
			objectTmp = URIpartz.remove(0);
		}
		for(int i=0;i<prefixesToIgnore;i++) {
			objectTmp = URIpartz.remove(0);
		}
		object = objectTmp;
		log.info("object: "+object+"; output-type: "+outputTypeStr+"; xtra URIpartz: "+URIpartz);
		
		for(int i=0;i<URIpartz.size();i++) {
			params.add(URIpartz.get(i));
		}
		
		DumpSyntaxInt outputSyntaxTmp = null;
		// http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
		// accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
		String acceptHeader = req.getHeader("Accept");
		log.debug("accept: "+acceptHeader);
		
		if(outputTypeStr != null) {
			outputSyntaxTmp = dsutils.getDumpSyntax(outputTypeStr, prop);
			if(outputSyntaxTmp==null) {
				// will never happen?
				throw new BadRequestException("Unknown output syntax: "+outputTypeStr);
			}
		}
		else {
			outputSyntaxTmp = dsutils.getDumpSyntaxByAccept(acceptHeader, prop);
			if(outputSyntaxTmp==null) {
				outputSyntaxTmp = dsutils.getDumpSyntax(QueryOn.DEFAULT_OUTPUT_SYNTAX, prop);
			}
			else {
				log.debug("syntax defined by accept! syntax: "+outputSyntaxTmp.getSyntaxId()+" // "+outputSyntaxTmp.getMimeType()+" ; accept: "+acceptHeader);
			}
		}
		outputSyntax = outputSyntaxTmp;
		
		SchemaModel sm = SchemaModelUtils.getModel(req.getServletContext(), this.modelId);
		DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(sm.getSqlDialect());
		setSyntaxProps(outputSyntax, req, feat, prop);
		
		//---------------------
		
		String offsetStr = req.getParameter("offset");
		if(offsetStr!=null) { offset = Integer.parseInt(offsetStr); }
		else { offset = 0; }

		Long maxLimit = Utils.getPropLong(prop, QueryOn.PROP_MAX_LIMIT); //XXX: add DEFAULT_LIMIT_MAX ?
		String limitStr = req.getParameter("limit");
		if(limitStr!=null) {
			int propLimit = Integer.parseInt(limitStr);
			if(maxLimit!=null && propLimit>maxLimit) {
				limit = maxLimit.intValue();
			}
			else {
				limit = propLimit;
			}
		}
		else {
			Long defaultLimit = Utils.getPropLong(prop, QueryOn.PROP_DEFAULT_LIMIT);
			if(defaultLimit!=null) {
				limit = defaultLimit.intValue();;
			}
			else if(maxLimit!=null) {
				limit = maxLimit.intValue();;
			}
			else {
				//limit = (int)(long) Utils.getPropLong(prop, QueryOn.PROP_DEFAULT_LIMIT, 1000l);
				limit = DEFAULT_LIMIT;
			}
		}
		
		// max & min updates
		String updateMaxStr = req.getParameter("updatemax");
		String updateMinStr = req.getParameter("updatemin");
		maxUpdates = (updateMaxStr!=null)?Integer.parseInt(updateMaxStr):DEFAULT_MAX_UPDATES;
		minUpdates = (updateMinStr!=null)?Integer.parseInt(updateMinStr):DEFAULT_MIN_UPDATES;
		
		String fields = req.getParameter("fields");
		if(fields!=null) {
			columns.addAll(Arrays.asList(fields.split(",")));
		}
		
		distinct = req.getParameter("distinct")!=null;
		
		String order = req.getParameter("order");
		if(order!=null) {
			List<String> orderColz = Arrays.asList(order.split(","));
			for(String ocol: orderColz) {
				ocol = ocol.trim();
				if(ocol.startsWith("-")) {
					ocol = ocol.substring(1);
					orderAscDesc.add("DESC");
				}
				else {
					orderAscDesc.add("ASC");
				}
				orderCols.add(ocol);
			}
		}
		
		loStrategy = req.getParameter("lostrategy");

		for(int i=1;;i++) {
			String value = req.getParameter("p"+i);
			if(value==null) break;
			params.add(value);
		}
		
		uniValueCol = req.getParameter("valuefield");
		uniValueMimetype = req.getParameter("mimetype");
		uniValueMimetypeCol = req.getParameter("mimefield");
		uniValueFilename = req.getParameter("filename");
		uniValueFilenameCol = req.getParameter("filenamefield");
		
		//Enumeration<String> en = (Enumeration<String>) req.getParameterNames();
		//while(en.hasMoreElements()) {
		//	String key = en.nextElement();
		//	String[] value = req.getParameterValues(key);
		
		// http://stackoverflow.com/questions/2422468/how-to-upload-files-to-server-using-jsp-servlet
		if(isContentTypeMultiPart()) {
			int i=0;
			for(Part p: req.getParts()) {
				String name = p.getName();
				String fileName = getSubmittedFileName(p);
				//log.info("part["+i+"]: " + name + (fileName!=null?" / filename="+fileName:"") );
				if(p.getContentType()!=null) {
					boolean added = setUniParam("v:", name, p, updatePartValues);
					log.info("part["+i+";added="+added+"]: " + name + "; content-type="+p.getContentType() + " ;  size=" + p.getSize() + " ; " + (fileName!=null?" / filename="+fileName:"") );
				}
				i++;
			}
			log.debug("multipart-content: length="+i);
		}
		
		String bodyParamName = req.getParameter("bodyparamname");
		if(bodyParamName!=null) {
			try {
				String value = getRequestBody(req);
				updateValues.put(bodyParamName, value);
			} catch (IOException e) {
				log.warn("error decoding http message body [bodyparamname="+bodyParamName+"]: "+e);
			}
		}
		
		Map<String,String[]> reqParams = req.getParameterMap();
		for(Map.Entry<String,String[]> entry: reqParams.entrySet()) {
			String key = entry.getKey();
			String[] value = entry.getValue();
			
			try {
				setUniParam("feq:", key, value, filterEquals);
				setUniParam("fne:", key, value, filterNotEquals);
				setUniParam("fgt:", key, value, filterGreaterThan);
				setUniParam("fge:", key, value, filterGreaterOrEqual);
				setUniParam("flt:", key, value, filterLessThan);
				setUniParam("fle:", key, value, filterLessOrEqual);
				
				setMultiParam("fin:", key, value, filterIn);
				setMultiParam("fnin:", key, value, filterNotIn);
				setMultiParam("flk:", key, value, filterLike);
				setMultiParam("fnlk:", key, value, filterNotLike);
				
				setUniParam("v:", key, value, updateValues);
			}
			catch(RuntimeException e) {
				//log.warn("encoding error [e: "+entry+"]: "+e);
				log.warn("setParam exception [k:"+key+"; v:"+Arrays.toString(value)+"]: "+e);
			}
		}
			
		/*
		for(String param: params.keySet()) {
			if(param.startsWith("feq:")) {
				String col = param.substring(4);
				String value = params.get(param)[0];
				filterEquals.put(col, value);
			}
			else if(param.startsWith("fne:")) {
				String col = param.substring(4);
				String value = params.get(param)[0];
				filterNotEquals.put(col, value);
			}
			
			else if(param.startsWith("fgt:")) {
				String col = param.substring(4);
				String value = params.get(param)[0];
				filterGreaterThan.put(col, value);
			}
			else if(param.startsWith("fge:")) {
				String col = param.substring(4);
				String value = params.get(param)[0];
				filterGreaterOrEqual.put(col, value);
			}
			else if(param.startsWith("flt:")) {
				String col = param.substring(4);
				String value = params.get(param)[0];
				filterLessThan.put(col, value);
			}
			else if(param.startsWith("fle:")) {
				String col = param.substring(4);
				String value = params.get(param)[0];
				filterLessOrEqual.put(col, value);
			}
			
			else if(param.startsWith("fin:")) {
				String col = param.substring(4);
				String[] values = params.get(param);
				filterIn.put(col, values);
			}
			else if(param.startsWith("fnin:")) {
				String col = param.substring(5);
				String[] values = params.get(param);
				filterNotIn.put(col, values);
			}
			else if(param.startsWith("flk:")) {
				String col = param.substring(4);
				String[] values = params.get(param);
				filterLike.put(col, values);
			}
			else if(param.startsWith("fnlk:")) {
				String col = param.substring(5);
				String[] values = params.get(param);
				filterNotLike.put(col, values);
			}
			
			else if(param.startsWith("v:")) {
				String col = param.substring(2);
				String value = params.get(param)[0];
				updateValues.put(col, value);
			}
			/*else {
				for(Filter f: filters) {
					if(f.accept(param)) {
						
					}
				}
			}* /
			else {
				//log.info("param "+param+" : "+params.get(param)[0]);
			}

			//XXX: warn unknown parameters
		}
		*/
		
		if(showDebugInfo) {
			showDebugInfo(reqParams);
		}
	}
	
	boolean setUniParam(String prefix, String key, String[] values, Map<String, String> uniFilter) {
		//String key = entry.getKey();
		//String[] value = entry.getValue();
		if(key.startsWith(prefix)) {
			String col = key.substring(prefix.length());
			//String value1 = values[0];
			//String value2 = URLDecoder.decode(value1, "UTF-8");
			//String value2 = value1;
			uniFilter.put(col, values[0]);
			//log.info("setUniParam: old="+value+" ; new="+value2);
			return true;
		}
		return false;
	}

	<T> boolean setUniParam(String prefix, String key, T value, Map<String, T> uniFilter) {
		if(key.startsWith(prefix)) {
			String col = key.substring(prefix.length());
			uniFilter.put(col, value);
			return true;
		}
		return false;
	}
	
	boolean setMultiParam(String prefix, String key, String[] values, Map<String, String[]> multiFilter) {
		if(key.startsWith(prefix)) {
			String col = key.substring(prefix.length());
			//String[] value1 = value;
			//for(int i=0;i<values.length;i++) {
				//String value2 = URLDecoder.decode(value[i], "UTF-8");
				//String value2 = values[i];
				//log.info("setMultiParam: old="+value[i]+" ; new="+value2); 
				//values[i] = value2;
			//}
			multiFilter.put(col, values);
			return true;
		}
		return false;
	}
	
	public static void setSyntaxProps(DumpSyntaxInt ds, HttpServletRequest req, DBMSFeatures feat, Properties initProps) {
		if(ds.needsDBMSFeatures()) { ds.setFeatures(feat); }
		
		List<String> pkeys = Utils.getStringListFromProp(syntaxProperties, ds.getSyntaxId()+".allowed-parameters", ",");
		//<syntax>.parameter@callback.prop=sqldump.datadump.<syntax>.zzz
		//<syntax>.parameter@callback.regex=[a-zA-Z_][a-zA-Z_0-9]*
		if(pkeys==null) { return; }
		
		Properties newProps = new Properties();
		for(String key: pkeys) {
			String val = req.getParameter(key);
			if(val!=null) {
				String regexKey = ds.getSyntaxId()+".parameter@"+key+".regex";
				String propKey = ds.getSyntaxId()+".parameter@"+key+".prop";
				String regex = syntaxProperties.getProperty(regexKey);
				String prop = syntaxProperties.getProperty(propKey);
				if(regex==null || prop==null) {
					log.warn("["+ds.getSyntaxId()+"] syntax properties '"+regexKey+"' & '"+propKey+"' must be set in '"+SYNTAXES_INFO_RESOURCE+"'");
				}
				else {
					if(Pattern.matches(regex, val)) {
						newProps.put(prop, val);
					}
					else {
						log.warn("["+ds.getSyntaxId()+"] parameter '"+key+"' does not match pattern '"+regex+"': "+val);
					}
				}
			}
		}

		if(newProps.size()==0) {
			ds.procProperties(initProps);
			return;
		}
		
		Properties modifiedProps = new ParametrizedProperties();
		modifiedProps.putAll(initProps);
		//if(newProps.size()>0) {
			log.debug("["+ds.getSyntaxId()+"] parameter props: "+newProps);
			modifiedProps.putAll(newProps);
		/*}
		else {
			//log.info("["+ds.getSyntaxId()+"] no parameter props");
			return;
		}*/
		ds.procProperties(modifiedProps);
	}
	
	/*
	 * reconstructs URL pased on RequestSpec
	 * 
	 * will not use: output syntax, method?, updateValues
	 * XXX may use: columns, offset, limit, filters, order??
	 */
	public String getCanonicalUrl(Properties prop) {
		String base = prop.getProperty(QueryOn.PROP_BASE_URL, "");
		if(!base.endsWith("/")) { base += "/"; }
		String url = base + object + "/";
		//url = url.replaceAll("\\/+", "\\/");
		String params = Utils.join(this.params, "/");
		return url+params;
	}
	
	public List<String> getParams() {
		return params;
	}
	
	public Map<String, String> getUpdateValues() {
		return updateValues;
	}
	
	String getSyntax(String lastUriPart, DumpSyntaxUtils dsutils, Properties prop) {
		String outputTypeStr = null;
		
		int lastDotIndex = lastUriPart.lastIndexOf('.');
		String outputTypeStrTmp = null;
		DumpSyntaxInt ds = null;
		if(lastDotIndex > -1) {
			int last2DotIndex = lastUriPart.lastIndexOf('.', lastDotIndex-1);
			outputTypeStrTmp = lastUriPart.substring(lastDotIndex+1);
			log.debug("lastUriPart: "+lastUriPart+" ; outputTypeStrTmp: "+outputTypeStrTmp+"; lastDotIndex="+lastDotIndex+"; last2DotIndex="+last2DotIndex);
			
			// test for known syntax
			if(last2DotIndex > -1) {
				String output2TypeStrTmp = lastUriPart.substring(last2DotIndex+1);
				//log.debug("output2TypeStrTmp: "+output2TypeStrTmp);
				ds = dsutils.getDumpSyntax(output2TypeStrTmp, prop);
				if(ds != null) {
					outputTypeStr = output2TypeStrTmp;
				}
			}
			
			if(ds==null) {
				ds = dsutils.getDumpSyntax(outputTypeStrTmp, prop);
				if(ds != null) {
					outputTypeStr = outputTypeStrTmp;
				}
			}
		}
		else {
			List<String> ignoreFileExt = Arrays.asList(standardFileExt);
			if(ignoreFileExt.contains(outputTypeStrTmp)) {
				outputTypeStr = outputTypeStrTmp;
			}
		}
		return outputTypeStr;
	}

	static String getRequestBody(HttpServletRequest req) throws IOException {
		InputStream is = req.getInputStream();
		return StringUtils.readInputStream(is, 8192);
	}
	
	boolean isContentTypeMultiPart() {
		return contentType!=null && contentType.startsWith(MULTIPART);
	}
	
	// http://stackoverflow.com/a/2424824/616413
	static String getSubmittedFileName(Part part) {
		for (String cd : part.getHeader("content-disposition").split(";")) {
			if (cd.trim().startsWith("filename")) {
				String fileName = cd.substring(cd.indexOf('=') + 1).trim().replace("\"", "");
				return fileName.substring(fileName.lastIndexOf('/') + 1).substring(fileName.lastIndexOf('\\') + 1);
			}
		}
		return null;
	}
	
	void showDebugInfo(Map<String,String[]> reqParams) {
		//log.info("debug: object: "+object+" / partz: "+URIpartz);
		log.info("debug: limit/offset: "+limit+"/"+offset);
		//final String loStrategy;
		log.info("debug: contentType: "+contentType);
		log.info("debug: minUpdates/maxUpdates: "+minUpdates+"/"+maxUpdates);
		log.info("debug: params: "+params);
		for(String par: reqParams.keySet()) {
			log.info("debug: reqParams["+par+"]: "+Arrays.toString(reqParams.get(par)));
		}
		log.info("debug: updateValues: "+updateValues);
		log.info("debug: updatePartValues: "+updatePartValues);
	}
	
	@Override
	public String toString() {
		return "RequestSpec[object="+object+";params="+params+";model="+modelId+"]";
	}
	
}
