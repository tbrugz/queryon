package tbrugz.queryon.api;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

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
	
	protected String keyValue = null;

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
		if(parts.size()==0) { return null; }
		String objectTmp = parts.remove(0);
		if(objectTmp==null) { return null; }
		
		int idx1 = objectTmp.indexOf("(");
		if(idx1>=0) {
			int idx2 = objectTmp.indexOf(")");
			String key = objectTmp.substring(idx1+1, idx2);
			if(key.charAt(0)=='\'' && key.charAt(key.length()-1)=='\'') {
				key = key.substring(1, key.length()-1);
			}
			objectTmp = objectTmp.substring(0, idx1);
			//XXX: allow compoundKey
			keyValue = key;
			log.info("object: ["+objectTmp+"] key: ["+keyValue+"]");
		}
		return objectTmp;
	}
	
	@Override
	protected void processParams(List<String> parts) {
		if(keyValue!=null) {
			params.add(keyValue);
		}
	}
	
}
