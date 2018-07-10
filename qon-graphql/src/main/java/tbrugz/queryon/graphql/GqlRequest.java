package tbrugz.queryon.graphql;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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
		
		{
		List<Argument> args = env.getField().getArguments();
		for(Argument arg: args) {
			//log.info("arg:: name: "+arg.getName()+" / value: "+arg.getValue());
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
		}
	
		//log.info("getSelectionSet: "+env.getSelectionSet()+"\n- "+env.getSelectionSet().getArguments());
		Map<String, Map<String, Object>> fieldsArguments = env.getSelectionSet().getArguments();
		
		for(Map.Entry<String, Map<String, Object>> eset: fieldsArguments.entrySet()) {
			String field = eset.getKey();
			Map<String, Object> args = eset.getValue();
			for(Map.Entry<String, Object> argMap: args.entrySet()) {
				String filter = argMap.getKey();
				Object value = argMap.getValue();
				
				addValueToFilter(field, filter, value);
			}
			
		}
		
	}
	
	void addValueToFilter(String field, String filter, Object value) {
		Map<String, String> ufilter = getUniFilter(filter);
		if(ufilter!=null) {
			ufilter.put(field, String.valueOf(value));
			return;
		}

		Map<String, String[]> mfilter = getMultiFilter(filter);
		if(mfilter!=null) {
			//log.info("f: "+filter+" / "+value+" / "+value.getClass());
			List<Object> values = (List<Object>) value;
			String[] strs = new String[values.size()];
			for(int i=0;i<values.size();i++) {
				strs[i] = String.valueOf(values.get(i));
			}
			mfilter.put(field, strs);
			return;
		}
		
		Set<String> sfilter = getSetFilter(filter);
		if(sfilter!=null) {
			sfilter.add(field);
			return;
		}
		
		log.warn("unknown filter: "+filter);
	}
	
	Map<String, String> getUniFilter(String filter) {
		switch (filter) {
		case "feq":
			return filterEquals;
		case "fne":
			return filterNotEquals;
		case "fgt":
			return filterGreaterThan;
		case "fge":
			return filterGreaterOrEqual;
		case "flt":
			return filterLessThan;
		case "fle":
			return filterLessOrEqual;
		default:
			return null;
		}
	}

	Map<String, String[]> getMultiFilter(String filter) {
		switch (filter) {
		case "fin":
			return filterIn;
		case "fnin":
			return filterNotIn;
		case "flk":
			return filterLike;
		case "fnlk":
			return filterNotLike;
		default:
			return null;
		}
	}

	Set<String> getSetFilter(String filter) {
		switch (filter) {
		case "fnull":
			return filterNull;
		case "fnotnull":
			return filterNotNull;
		default:
			return null;
		}
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
