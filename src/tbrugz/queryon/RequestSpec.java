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

//XXX: order by? 3a,1d,2d?
public class RequestSpec {
	static final Log log = LogFactory.getLog(QueryOn.class);

	final String httpMethod;
	final String object;
	final String action;
	final int offset, limit;
	List<String> columns = new ArrayList<String>();
	List<String> params = new ArrayList<String>();
	String outputTypeStr = QueryOn.DEFAULT_OUTPUT_SYNTAX;
	DumpSyntax outputSyntax = null;
	
	public RequestSpec(QueryOn qon, HttpServletRequest req, Properties prop) throws ServletException {
		httpMethod = req.getMethod();
		
		String varUrl = req.getPathInfo();
		
		String[] URIparts = varUrl.split("/");
		List<String> URIpartz = new ArrayList<String>( Arrays.asList(URIparts) );
		log.info("urlparts: "+URIpartz);
		if(URIpartz.size()<3) { throw new ServletException("URL must have at least 2 parts"); }

		String lastURIPart = URIpartz.remove(URIpartz.size()-1);
		int lastDotIndex = lastURIPart.lastIndexOf('.'); //FIXME: do it after '/' split - '.' may split SCHEMA and OBJNAME
		if(lastDotIndex > -1) {
			outputTypeStr = lastURIPart.substring(lastDotIndex+1);
			lastURIPart = lastURIPart.substring(0, lastDotIndex);
		}
		URIpartz.add( lastURIPart );
		log.info("output-type: "+outputTypeStr+"; new urlparts: "+URIpartz);
		
		String objectTmp = URIpartz.remove(0);
		if(objectTmp == null || objectTmp.equals("")) {
			//first part may be empty
			objectTmp = URIpartz.remove(0);
		}
		object = objectTmp;
		String actionTmp = URIpartz.remove(0);
		action = actionTmp.toUpperCase();
		
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
		else { limit = -1; }
		
		for(int i=1;;i++) {
			String value = req.getParameter("c"+i);
			if(value==null) break;
			columns.add(value);
		}

		for(int i=1;;i++) {
			String value = req.getParameter("p"+i);
			if(value==null) break;
			params.add(value);
		}
	}
} 
