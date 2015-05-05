package tbrugz.queryon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.sqldump.datadump.DumpSyntax;
import tbrugz.sqldump.util.Utils;

//XXX: order by? 3a,1d,2d?
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
	
	final HttpServletRequest request;

	final String httpMethod;
	final String modelId;
	final String object;
	final int offset, limit;
	final String loStrategy;
	
	final List<String> columns = new ArrayList<String>();
	final List<String> params = new ArrayList<String>();
	final String outputTypeStr;
	final DumpSyntax outputSyntax;
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

	final List<String> orderCols = new ArrayList<String>();
	final List<String> orderAscDesc = new ArrayList<String>();
	
	public RequestSpec(DumpSyntaxUtils dsutils, HttpServletRequest req, Properties prop) throws ServletException {
		this.request = req;
		String method = req.getParameter("method");
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
			}
		}
		else {
			outputTypeStr = null;
		}
		URIpartz.add( lastURIPart );
		//log.info("output-type: "+outputTypeStr+"; new urlparts: "+URIpartz);
		
		String objectTmp = URIpartz.remove(0);
		if(objectTmp == null || objectTmp.equals("")) {
			//first part may be empty
			objectTmp = URIpartz.remove(0);
		}
		object = objectTmp;
		log.info("object: "+object+"; output-type: "+outputTypeStr+"; xtra URIpartz: "+URIpartz);
		
		for(int i=0;i<URIpartz.size();i++) {
			params.add(URIpartz.get(i));
		}
		
		DumpSyntax outputSyntaxTmp = null;
		// http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
		// accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
		String acceptHeader = req.getHeader("Accept");
		log.debug("accept: "+acceptHeader);
		
		if(outputTypeStr != null) {
			outputSyntaxTmp = dsutils.getDumpSyntax(outputTypeStr, prop);
			if(outputSyntaxTmp==null) {
				throw new BadRequestException("Unknown output syntax: "+outputTypeStr);
			}
		}
		else {
			outputSyntaxTmp = dsutils.getDumpSyntaxByAccept(acceptHeader, prop);
			if(outputSyntaxTmp==null) {
				outputSyntaxTmp = dsutils.getDumpSyntax(QueryOn.DEFAULT_OUTPUT_SYNTAX, prop);
			}
			else {
				log.info("syntax defined by accept! syntax: "+outputSyntaxTmp.getSyntaxId()+" // "+outputSyntaxTmp.getMimeType()+" ; accept: "+acceptHeader);
			}
		}
		outputSyntax = outputSyntaxTmp;
		
		//---------------------
		
		String offsetStr = req.getParameter("offset");
		if(offsetStr!=null) { offset = Integer.parseInt(offsetStr); }
		else { offset = 0; }

		Long maxLimit = Utils.getPropLong(prop, QueryOn.PROP_MAX_LIMIT);
		String limitStr = req.getParameter("limit");
		if(limitStr!=null) { 
			int propLimit = Integer.parseInt(limitStr);
			if(maxLimit!=null && propLimit>maxLimit) {
				limit = (int)(long) maxLimit;
			}
			else {
				limit = propLimit;
			}
		}
		else {
			Long defaultLimit = Utils.getPropLong(prop, QueryOn.PROP_DEFAULT_LIMIT);
			if(defaultLimit!=null) {
				limit = (int)(long) defaultLimit;
			}
			else if(maxLimit!=null) {
				limit = (int)(long) maxLimit;
			}
			else {
				//limit = (int)(long) Utils.getPropLong(prop, QueryOn.PROP_DEFAULT_LIMIT, 1000l);
				limit = 0;
			}
		}
		
		String fields = req.getParameter("fields");
		if(fields!=null) {
			columns.addAll(Arrays.asList(fields.split(",")));
		}
		
		distinct = req.getParameter("distinct")!=null;
		
		String order = req.getParameter("order");
		if(order!=null) {
			List<String> orderColz = Arrays.asList(order.split(","));
			for(String ocol: orderColz) {
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
		
		@SuppressWarnings("unchecked")
		Map<String,String[]> params = req.getParameterMap();
		
		//List<Filter> filters = new ArrayList<Filter>();
		//Filter fnin = new Filter("fnin", true, filterNotIn);
		//filters.add(fnin);
		
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
			}*/

			//XXX: warn unknown parameters
		}
	}
	
	
	/*
	 * reconstructs URL pased on RequestSpec
	 * 
	 * will not use: output syntax, method?, updateValues
	 * XXX may use: columns, offset, limit, filters, order??
	 */
	public String getCanonicalUrl(Properties prop) {
		String url = prop.getProperty(QueryOn.PROP_BASE_URL)+object+"/";
		//url = url.replaceAll("\\/+", "\\/");
		String params = Utils.join(this.params, "/");
		return url+params;
	}
	
} 
