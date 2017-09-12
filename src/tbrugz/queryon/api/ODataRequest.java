package tbrugz.queryon.api;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.util.DumpSyntaxUtils;

public class ODataRequest extends RequestSpec {
	
	public static final String PARAM_TOP = "$top";
	public static final String PARAM_SKIP = "$skip";

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

}
