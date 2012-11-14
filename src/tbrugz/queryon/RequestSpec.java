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

	String object = null;
	String action = null;
	int offset, length;
	List<String> columns = new ArrayList<String>();
	List<String> params = new ArrayList<String>();
	String outputTypeStr = QueryOn.DEFAULT_OUTPUT_SYNTAX;
	DumpSyntax outputSyntax = null;
	
	public RequestSpec(QueryOn qon, HttpServletRequest req, Properties prop) throws ServletException {
		String varUrl = req.getPathInfo();
		int lastDotIndex = varUrl.lastIndexOf('.');
		if(lastDotIndex>-1) {
			outputTypeStr = varUrl.substring(lastDotIndex+1);
			varUrl = varUrl.substring(0, lastDotIndex);
		}
		
		String[] URIparts = varUrl.split("/");
		List<String> URIpartz = new ArrayList<String>( Arrays.asList(URIparts) );
		log.info("urlparts: "+URIpartz);
		if(URIpartz.size()<3) { throw new ServletException("URL must have at least 2 parts"); }

		object = URIpartz.remove(0);
		if(object == null || object.equals("")) {
			//first part may be empty
			object = URIpartz.remove(0);
		}
		action = URIpartz.remove(0);
		action = action.toUpperCase();
		for(int i=0;i<URIpartz.size();i++) {
			params.add(URIpartz.get(i));
		}
		
		outputSyntax = qon.getDumpSyntax(outputTypeStr, prop);
		if(outputSyntax == null) {
			throw new ServletException("Unknown output syntax: "+outputTypeStr);
		}
		//---------------------
		
		String offsetStr = req.getParameter("offset");
		if(offsetStr!=null) offset = Integer.parseInt(offsetStr);

		String lengthStr = req.getParameter("length");
		if(lengthStr!=null) length = Integer.parseInt(lengthStr);
		
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
