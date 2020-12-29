package tbrugz.queryon.api;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.Gson;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.api.ODataFilterParser.Filter;
import tbrugz.queryon.syntaxes.ODataJsonSyntax;
import tbrugz.queryon.util.DumpSyntaxUtils;

public class ODataRequest extends RequestSpec {
	
	static final Log log = LogFactory.getLog(ODataRequest.class);
	
	// XXX $expand
	public static final String PARAM_FILTER = "$filter";
	public static final String PARAM_ORDERBY = "$orderby";
	public static final String PARAM_SELECT = "$select";
	public static final String PARAM_SKIP = "$skip";
	public static final String PARAM_TOP = "$top";
	
	// collection parameters
	public static final String PARAM_COUNT = "$count";
	
	// instance parameters
	public static final String PARAM_VALUE = "$value";
	
	static final String ALIAS_VALUE = "value";
	static final String VALUEFIELD_COUNT = "count";
	
	protected String valueField;
	protected boolean isCountRequest;
	Map<String, String> xtraParametersMap;
	
	public static final String DEFAULT_OBJECT = null;

	/*public ODataRequest(DumpSyntaxUtils dsutils, HttpServletRequest req, Properties prop, int prefixesToIgnore,
			String defaultOutputSyntax, boolean allowGetDumpSyntaxByAccept, int minUrlParts, String defaultObject)
			throws ServletException, IOException {
		super(dsutils, req, prop, prefixesToIgnore, defaultOutputSyntax, allowGetDumpSyntaxByAccept, minUrlParts, defaultObject);
	}*/

	public ODataRequest(HttpServletRequest req, Properties prop)
			throws ServletException, IOException {
		super(req, prop, 0, ODataJsonSyntax.ODATA_ID, false, 0, DEFAULT_OBJECT);
	}
	
	@Override
	protected int getFinalOffset(HttpServletRequest req) {
		String offsetStr = req.getParameter(PARAM_SKIP);
		return offsetStr!=null? Integer.parseInt(offsetStr) : 0;
	}
	
	@Override
	protected Integer getFinalLimit(HttpServletRequest req) {
		String limitStr = req.getParameter(PARAM_TOP);
		return limitStr!=null ? Integer.parseInt(limitStr) : null;
	}

	@Override
	protected void processOrder(HttpServletRequest req) {
		String order = req.getParameter(PARAM_ORDERBY);
		if(order!=null) {
			List<String> orderColz = Arrays.asList(order.split(","));
			for(String ocol: orderColz) {
				ocol = ocol.trim();
				String[] parts = ocol.split("[ ]+");
				if(parts.length<1 || parts.length>2) {
					log.warn(PARAM_ORDERBY+": parts.length should be 1 or 2 :: "+Arrays.asList(parts));
					continue;
				}
				orderCols.add(parts[0]);
				if(parts.length==2) {
					if("desc".equalsIgnoreCase(parts[1])) {
						orderAscDesc.add(ORDER_DESC);
					}
					else {
						orderAscDesc.add(ORDER_ASC);
					}
				}
				else {
					orderAscDesc.add(ORDER_ASC);
				}
			}
		}
	}
	
	@Override
	protected String getFields(HttpServletRequest req) {
		return req.getParameter(PARAM_SELECT);
	}

	@Override
	protected String getObject(List<String> parts, int prefixesToIgnore) {
		keyValues.clear();
		if(parts.size()==0) { return null; }
		String objectTmp = parts.remove(0);
		
		if(objectTmp!=null) {
			int idx1 = objectTmp.indexOf("(");
			if(idx1>=0) {
				int idx2 = objectTmp.indexOf(")");
				String key = objectTmp.substring(idx1+1, idx2);
				objectTmp = objectTmp.substring(0, idx1);
				/*Map<String, String> keyMap = parseKey(key);
				if(keyMap!=null) {
					keyValues.putAll(keyMap);
				}*/
				keyValues.putAll(parseKey(key));
				//log.info("object: ["+objectTmp+"] key: "+keyValues);
			}
		}
		return objectTmp;
	}
	
	static final String uniqueKeyKey = "unique";
	
	@Override
	protected void processParams(List<String> parts) {
		valueField = null;
		isCountRequest = false;
		xtraParametersMap = new HashMap<String, String>();
		
		/*if(keyValues!=null) {
			if(keyValues.size()==1) {
				params.add(keyValues.get(uniqueKeyKey));
			}
			else {
				//FIXedME: correct key order: will be corrected by ODataServlet.preprocessParameters()
				for(Map.Entry<String, String> e: keyValues.entrySet()) {
					params.add(e.getValue());
				}
			}
			//params.addAll(keyValues);
		}*/
		if(keyValues!=null) {
			Map<Integer, Object> postionalParamsMap = new TreeMap<Integer, Object>();
			for(Map.Entry<String, String> entry: keyValues.entrySet()) {
				Matcher m = positionalParamPattern.matcher(entry.getKey());
				if(m.matches()) {
					int pos = Integer.parseInt( m.group(1) );
					String value = entry.getValue();
					if(value==null) {
						log.warn("null (or empty) 'value': "+entry.getKey()+" / "+value);
					}
					else {
						postionalParamsMap.put(pos, value);
					}
				}
				else {
					xtraParametersMap.put(entry.getKey(), entry.getValue());
				}
			}
			{
				int pCount = 1;
				for(Map.Entry<Integer, Object> e: postionalParamsMap.entrySet()) {
					int i = e.getKey();
					params.add(e.getValue());
					pCount++;
					if(i!=pCount) {
						log.debug("parameter #"+i+" present but previous parameter isn't [pCount="+pCount+"]");
					}
				}
			}
		}
		//log.info("processParams: parts: "+parts+" / params: "+params+" / keyvalues: "+keyValues);
			
		if(parts.size()>0) {
			//log.info("parts: "+parts);
			String col = parts.remove(0).trim();
			if(PARAM_COUNT.equals(col)) {
				isCountRequest = true;
				valueField = VALUEFIELD_COUNT;
			}
			else {
				columns.add(col);
				aliases.add(ALIAS_VALUE);
				if(parts.size()>0) {
					String par = parts.remove(0);
					if(PARAM_VALUE.equals(par)) {
						valueField = col;
					}
					else {
						log.warn("unknown parameter: "+par+" [remaining parts: "+parts+"]");
					}
				}
			}
		}
	}
	
	static final Pattern integerPattern = Pattern.compile("\\d+");
	
	Map<String, String> parseKey(String key) {
		//log.info("key:: "+key+" int-ptrn: "+integerPattern);
		Map<String, String> ret = new LinkedHashMap<String, String>();
		if(key.charAt(0)=='\'' && key.charAt(key.length()-1)=='\'') {
			key = key.substring(1, key.length()-1);
			ret.put(uniqueKeyKey, key);
		}
		else if(integerPattern.matcher(key).matches()) {
			ret.put(uniqueKeyKey, key);
		}
		else {
			int begin = 0;
			int idxComma = 0;
			do {
				int idx = key.indexOf("=", begin);
				idxComma = key.indexOf(",", begin);
				//int idx2 = key.indexOf(",");
				String field = null;
				String value = null;
				//log.info("key: "+key+" ; idx="+idx+" ; idxComma="+idxComma);
				if(idx>0) {
					field = key.substring(begin, idx); //.trim()?
				}
				if(idxComma>0) {
					value = key.substring(idx+1, idxComma);
				}
				else {
					value = key.substring(idx+1);
				}
				
				//value = value.trim() ?
				if(value.charAt(0)=='\'' && value.charAt(value.length()-1)=='\'') {
					value = value.substring(1, value.length()-1);
				}
				begin = idxComma+1;
				ret.put(field, value);
			}
			while(idxComma>0);
		}
		//log.info("key: "+key+" ; ret: "+ret);
		return ret;
	}
	
	@Override
	protected void processFilters(Set<String> allowedFilters) {
		String filter = request.getParameter(PARAM_FILTER);
		if(filter!=null) {
			try {
				List<Filter> filters = ODataFilterParser.parse(filter);
				log.info("filters: "+filters);
				for(Filter f: filters) {
					if("eq".equals(f.operator)) {
						setODataFilterUniParam("feq", f.var, f.literal, filterEquals, allowedFilters);
					}
					else if("ne".equals(f.operator)) {
						setODataFilterUniParam("fne", f.var, f.literal, filterNotEquals, allowedFilters);
					}
					else if("gt".equals(f.operator)) {
						setODataFilterUniParam("fgt", f.var, f.literal, filterGreaterThan, allowedFilters);
					}
					else if("ge".equals(f.operator)) {
						setODataFilterUniParam("fge", f.var, f.literal, filterGreaterOrEqual, allowedFilters);
					}
					else if("lt".equals(f.operator)) {
						setODataFilterUniParam("flt", f.var, f.literal, filterLessThan, allowedFilters);
					}
					else if("le".equals(f.operator)) {
						setODataFilterUniParam("fle", f.var, f.literal, filterLessOrEqual, allowedFilters);
					}
					else {
						throw new BadRequestException("unknown filter operator '"+f.operator+"'");
					}
				}
			}
			catch(RuntimeException e) {
				throw new BadRequestException(e.getMessage(), e);
			}
		}
	}
	
	@Override
	protected void processXtra() {
		try {
			Gson gson = new Gson();
			
			//java 8
			//Type type = new TypeToken<Map<String, Object>>(){}.getType();
			//Map<String, Object> map = gson.fromJson(request.getReader(), type);
			
			//Map<String, Object> map = new HashMap<String,Object>();
			//map = gson.fromJson(request.getReader(), map.getClass());
			@SuppressWarnings("unchecked")
			Map<String, Object> map = gson.fromJson(request.getReader(), Map.class);
			
			if(map!=null) {
				for(Map.Entry<String, Object> e: map.entrySet()) {
					String value = getValueForUpdate( e.getValue() );
					log.debug("odata body: "+e.getKey()+" / "+e.getValue()+" / "+value+" ["+e.getValue().getClass()+"]");
					updateValues.put(e.getKey(), value);
					
					//XXX if matches 'p[1-9][0-9]*', add to params? action invocation
					// http://docs.oasis-open.org/odata/odata-json-format/v4.0/odata-json-format-v4.0.html#_Toc368563119
				}
			}
		}
		catch(IOException e) {
			throw new BadRequestException(e.getMessage());
		}
	}
	
	/*
	String getValueForUpdate(Object o) {
		if(o instanceof Double) {
			Double d = (Double) o;
			if(d.doubleValue() % 1 == 0) { return String.valueOf(d.longValue()); }
		}
		return String.valueOf(o);
	}
	*/
	
	public <T> void setODataFilterUniParam(String prefix, String key, T value, Map<String, T> uniFilter, Set<String> allowedFilters) {
		if(allowedFilters!=null && !allowedFilters.contains(prefix)) {
			throw new BadRequestException("filter '"+prefix+"' not allowed");
		}
		uniFilter.put(key, value);
	}
	
	@Override
	protected String getDefaultOutputSyntax(Properties prop, String defaultSyntax) {
		return ODataJsonSyntax.ODATA_ID; //defaultSyntax
	}
	
	@Override
	protected String getValueField(HttpServletRequest req) {
		return valueField;
	}
	
	@Override
	protected boolean isCountRequest(HttpServletRequest req) {
		return isCountRequest;
	}
	
	@Override
	public Integer getUpdateSucessStatus() {
		return HttpServletResponse.SC_NO_CONTENT;
	}

	@Override
	public Integer getDeleteSucessStatus() {
		return HttpServletResponse.SC_NO_CONTENT;
	}

	@Override
	public Map<String, String> getParameterMapUniqueValues() {
		return xtraParametersMap;
	}
	
}
