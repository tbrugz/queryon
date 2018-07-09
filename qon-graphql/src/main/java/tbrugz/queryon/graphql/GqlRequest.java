package tbrugz.queryon.graphql;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import graphql.schema.DataFetchingEnvironment;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.util.DumpSyntaxUtils;

public class GqlRequest extends RequestSpec {

	//private static final Log log = LogFactory.getLog(GqlRequest.class);
	
	//static final Properties prop = new Properties();
	
	//GqlMapBufferSyntax dumpSyntax = null;
	
	/*public GqlRequestSpec(DumpSyntaxUtils dsutils, Properties prop, HttpServletRequest req)
			throws ServletException, IOException {
		super(dsutils, req, prop, 0, null, true, 0, null);
	}*/

	public GqlRequest(DataFetchingEnvironment env, Properties prop, HttpServletRequest req)
			throws ServletException, IOException {
		super(null, req, prop, 0, null, true, 0, null);
		//log.info("GqlRequest(): "+this.hashCode()+"/"+dumpSyntax);
		
		object = env.getField().getName();
	
		//TODO: filters, ...
	}
	
	/*@Override
	protected String getDefaultOutputSyntax(Properties prop, String defaultSyntax) {
		return GqlMapBufferSyntax.GRAPHQL_ID;
	}
	
	@Override
	protected String getObject(List<String> parts, int prefixesToIgnore) {
		return super.getObject(parts, prefixesToIgnore);
	}*/
	
	@Override
	protected GqlMapBufferSyntax getOutputSyntax(HttpServletRequest req, DumpSyntaxUtils dsutils,
			boolean allowGetDumpSyntaxByAccept, String defaultOutputSyntax) {
		GqlMapBufferSyntax dumpSyntax = new GqlMapBufferSyntax();
		//log.info("getOutputSyntax...      "+this.hashCode()+"/"+dumpSyntax);
		return dumpSyntax;
	}
	
	protected GqlMapBufferSyntax getCurrentDumpSyntax() {
		//log.info("getCurrentDumpSyntax... "+this.hashCode()+"/"+outputSyntax);
		return (GqlMapBufferSyntax) outputSyntax;
	}
	
}
