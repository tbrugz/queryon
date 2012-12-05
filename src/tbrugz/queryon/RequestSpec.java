package tbrugz.queryon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
	
	public RequestSpec(QueryOn qon, HttpServletRequest req, Properties prop) throws ServletException {
		httpMethod = req.getMethod();
		
		String varUrl = req.getPathInfo();
		
		String[] URIparts = varUrl.split("/");
		List<String> URIpartz = new ArrayList<String>( Arrays.asList(URIparts) );
		log.info("urlparts: "+URIpartz);
		if(URIpartz.size()<2) { throw new ServletException("URL must have at least 1 part"); }

		String lastURIPart = URIpartz.remove(URIpartz.size()-1);
		int lastDotIndex = lastURIPart.lastIndexOf('.'); //FIXME: do it after '/' split - '.' may split SCHEMA and OBJNAME
		if(lastDotIndex > -1) {
			outputTypeStr = lastURIPart.substring(lastDotIndex+1);
			lastURIPart = lastURIPart.substring(0, lastDotIndex);
		}
		else {
			outputTypeStr = QueryOn.DEFAULT_OUTPUT_SYNTAX;
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
		
		outputSyntax = qon.getDumpSyntax(outputTypeStr, prop);
		if(outputSyntax == null) {
			throw new ServletException("Unknown output syntax: "+outputTypeStr);
		}
		//---------------------
		
		String offsetStr = req.getParameter("offset");
		if(offsetStr!=null) { offset = Integer.parseInt(offsetStr); }
		else { offset = 0; }

		String lengthStr = req.getParameter("limit");
		if(lengthStr!=null) { limit = Integer.parseInt(lengthStr); }
		else {
			limit = (int)(long) Utils.getPropLong(prop, QueryOn.PROP_DEFAULT_LIMIT, 1000l);
		}
		
		String fields = req.getParameter("fields");
		if(fields!=null) {
			columns.addAll(Arrays.asList(fields.split(",")));
		}

		for(int i=1;;i++) {
			String value = req.getParameter("p"+i);
			if(value==null) break;
			params.add(value);
		}
	}
} 
