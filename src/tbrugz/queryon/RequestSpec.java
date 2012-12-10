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
	static final Log log = LogFactory.getLog(QueryOn.class);

	final String httpMethod;
	final String object;
	final int offset, limit;
	
	final List<String> columns = new ArrayList<String>();
	final List<String> params = new ArrayList<String>();
	final String outputTypeStr;
	final DumpSyntax outputSyntax;
	
	final Map<String, String> filterEquals = new HashMap<String, String>();
	final Map<String, String[]> filterIn = new HashMap<String, String[]>();
	final Map<String, String> updateValues = new HashMap<String, String>();

	final List<String> orderCols = new ArrayList<String>();
	final List<String> orderAscDesc = new ArrayList<String>();
	
	public RequestSpec(DumpSyntaxUtils dsutils, HttpServletRequest req, Properties prop) throws ServletException {
		String method = req.getParameter("method");
		//XXX: may method be changed? property?
		if(method!=null) {
			httpMethod = method;
		}
		else {
			httpMethod = req.getMethod();
		}
		
		String varUrl = req.getPathInfo();
		
		String[] URIparts = varUrl.split("/");
		List<String> URIpartz = new ArrayList<String>( Arrays.asList(URIparts) );
		log.info("urlparts: "+URIpartz);
		if(URIpartz.size()<2) { throw new BadRequestException("URL must have at least 1 part"); }

		String lastURIPart = URIpartz.remove(URIpartz.size()-1);
		int lastDotIndex = lastURIPart.lastIndexOf('.');
		if(lastDotIndex > -1) {
			outputTypeStr = lastURIPart.substring(lastDotIndex+1);
			lastURIPart = lastURIPart.substring(0, lastDotIndex);
		}
		else {
			outputTypeStr = null;
		}
		URIpartz.add( lastURIPart );
		log.info("output-type: "+outputTypeStr+"; new urlparts: "+URIpartz);
		
		String objectTmp = URIpartz.remove(0);
		if(objectTmp == null || objectTmp.equals("")) {
			//first part may be empty
			objectTmp = URIpartz.remove(0);
		}
		object = objectTmp;
		
		for(int i=0;i<URIpartz.size();i++) {
			params.add(URIpartz.get(i));
		}
		
		DumpSyntax outputSyntaxTmp = null;
		// http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
		// accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
		String acceptHeader = req.getHeader("Accept");
		log.info("accept: "+acceptHeader);
		
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
		
		String order = req.getParameter("order");
		if(order!=null) {
			List<String> orderColz = Arrays.asList(order.split(","));
			for(String ocol: orderColz) {
				String[] cparts = ocol.split(":");
				orderCols.add(cparts[0]);
				if(cparts.length>1) {
					String ascDesc = cparts[1];
					if("A".equalsIgnoreCase(ascDesc)) {
						orderAscDesc.add("ASC");
					}
					else if("D".equalsIgnoreCase(ascDesc)) {
						orderAscDesc.add("DESC");
					}
					else {
						orderAscDesc.add("");
					}
				}
				else {
					orderAscDesc.add("");
				}
			}
		}

		for(int i=1;;i++) {
			String value = req.getParameter("p"+i);
			if(value==null) break;
			params.add(value);
		}
		
		Map<String,String[]> params = req.getParameterMap();
		for(String param: params.keySet()) {
			if(param.startsWith("fe:")) {
				String col = param.substring(3);
				String value = params.get(param)[0];
				filterEquals.put(col, value);
			}
			else if(param.startsWith("fin:")) {
				String col = param.substring(4);
				String[] values = params.get(param);
				filterIn.put(col, values);
			}
			else if(param.startsWith("v:")) {
				String col = param.substring(2);
				String value = params.get(param)[0];
				updateValues.put(col, value);
			}

			//XXX: warn unknown parameters
		}
	}
} 
