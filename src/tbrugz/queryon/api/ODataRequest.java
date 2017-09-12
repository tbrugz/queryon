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
	
	public static final String PARAM_TOP = "$top";
	public static final String PARAM_SKIP = "$skip";
	public static final String PARAM_ORDERBY = "$orderby";

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
	
}
