package tbrugz.queryon.api;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.util.DumpSyntaxUtils;

public class ODataRequest extends RequestSpec {
	
	static final Log log = LogFactory.getLog(ODataRequest.class);
	
	public static final String PARAM_SELECT = "$select";
	public static final String PARAM_SKIP = "$skip";
	public static final String PARAM_ORDERBY = "$orderby";
	public static final String PARAM_TOP = "$top";
	
	// collection parameters
	public static final String PARAM_COUNT = "$count";
	
	// instance parameters
	public static final String PARAM_VALUE = "$value";
	
	static final String ALIAS_VALUE = "value";
	static final String VALUEFIELD_COUNT = "count";
	
	protected Map<String, String> keyValues;
	protected String valueField;
	protected boolean isCountRequest;

	public ODataRequest(DumpSyntaxUtils dsutils, HttpServletRequest req, Properties prop, int prefixesToIgnore,
			String defaultOutputSyntax, boolean allowGetDumpSyntaxByAccept, int minUrlParts, String defaultObject)
			throws ServletException, IOException {
		super(dsutils, req, prop, prefixesToIgnore, defaultOutputSyntax, allowGetDumpSyntaxByAccept, minUrlParts, defaultObject);
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
		keyValues = null;
		if(parts.size()==0) { return null; }
		String objectTmp = parts.remove(0);
		
		if(objectTmp!=null) {
			int idx1 = objectTmp.indexOf("(");
			if(idx1>=0) {
				int idx2 = objectTmp.indexOf(")");
				String key = objectTmp.substring(idx1+1, idx2);
				objectTmp = objectTmp.substring(0, idx1);
				keyValues = parseKey(key);
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
		
		if(keyValues!=null) {
			if(keyValues.size()==1) {
				params.add(keyValues.get(uniqueKeyKey));
			}
			else {
				//FIXME: correct key order!
				for(Map.Entry<String, String> e: keyValues.entrySet()) {
					params.add(e.getValue());
				}
			}
			//params.addAll(keyValues);
		}
			
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
					field = key.substring(begin, idx);
				}
				if(idxComma>0) {
					value = key.substring(idx+1, idxComma);
				}
				else {
					value = key.substring(idx+1);
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
	protected String getValueField(HttpServletRequest req) {
		return valueField;
	}
	
	@Override
	protected boolean isCountRequest(HttpServletRequest req) {
		return isCountRequest;
	}
	
}
