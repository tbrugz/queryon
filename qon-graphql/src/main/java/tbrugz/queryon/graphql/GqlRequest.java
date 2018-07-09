package tbrugz.queryon.graphql;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import graphql.language.Argument;
import graphql.language.BooleanValue;
import graphql.language.IntValue;
import graphql.schema.DataFetchingEnvironment;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.util.DumpSyntaxUtils;

public class GqlRequest extends RequestSpec {

	private static final Log log = LogFactory.getLog(GqlRequest.class);
	
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
		List<Argument> args = env.getField().getArguments();
		for(Argument arg: args) {
			log.info("arg:: name: "+arg.getName()+" / value: "+arg.getValue());
			switch (arg.getName()) {
			case RequestSpec.PARAM_LIMIT:
				limit = ((IntValue)arg.getValue()).getValue().intValue();
				break;
			case RequestSpec.PARAM_OFFSET:
				offset = ((IntValue)arg.getValue()).getValue().intValue();
				break;
			case RequestSpec.PARAM_DISTINCT:
				distinct = ((BooleanValue)arg.getValue()).isValue();
				break;
			default:
				break;
			}
		}
	
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
