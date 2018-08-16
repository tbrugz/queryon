package tbrugz.queryon.graphql;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import graphql.language.Argument;
import graphql.language.BooleanValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.DataFetchingEnvironment;
import tbrugz.queryon.QueryOn.ActionType;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.graphql.GqlSchemaFactory.QonAction;
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
	
	final QonAction action;
	int updateCount;
	String executeOutput;
	final Map<String, String> relationColMap;
	final Map<String, String> xtraParametersMap = new HashMap<>();

	public GqlRequest(DataFetchingEnvironment env, Map<String, QonAction> actionMap, Map<String, Map<String, String>> colMap, Properties prop, HttpServletRequest req)
			throws ServletException, IOException {
		super(null, req, prop, 0, null, true, 0, null);
		//log.info("GqlRequest(): "+this.hashCode()+"/"+dumpSyntax);
		
		object = env.getField().getName();
		action = actionMap.get(object);
		relationColMap = colMap.get(action.objectName);
		//log.info("relationColMap: "+relationColMap+" // "+action.objectName+" // "+object);

		//log.debug("field-names: "+env.getSelectionSet().get().keySet());
		Set<String> fieldNames = env.getSelectionSet().get().keySet();
		//columns.addAll(fieldNames); //"fields" param (PARAM_FIELDS)
		// non-normalized columns will not be retrieved...
		/*for(String s: fieldNames) {
			if(GqlSchemaFactory.isNormalizedName(s)) {
				columns.add(s);
			}
		}*/
		if(relationColMap!=null) {
			for(String f: fieldNames) {
				String unnormalizedCol = relationColMap.get(f);
				if(unnormalizedCol==null) {
					log.debug("null unnormalized column [normalized="+f+"]");
					columns.add(f);
					aliases.add(null);
					continue;
				}
				
				columns.add(unnormalizedCol);
				if(unnormalizedCol.equals(f)) {
					aliases.add(null);
				}
				else {
					aliases.add(f);
				}
			}
		}
		
		{
		List<Argument> args = env.getField().getArguments();
		Map<String, String> positionalValues = new TreeMap<>();
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
				if( (action.atype==ActionType.UPDATE || action.atype==ActionType.DELETE) && arg.getName().startsWith(GqlSchemaFactory.FILTER_KEY_PREPEND) ) {
					keyValues.put(arg.getName().substring(GqlSchemaFactory.FILTER_KEY_PREPEND.length()), getStringValue(arg.getValue()));
				}
				else if(action.atype==ActionType.INSERT || action.atype==ActionType.UPDATE) {
					updateValues.put(arg.getName(), getStringValue(arg.getValue()));
				}
				else if( (action.atype==ActionType.SELECT || action.atype==ActionType.EXECUTE) && positionalParamPattern.matcher(arg.getName()).matches()) {
					//log.info("Exec/arg:: name: "+arg.getName()+" / value: "+arg.getValue());
					positionalValues.put(arg.getName(), getStringValue(arg.getValue()));
				}
				else {
					log.debug("unknown argument:: name: "+arg.getName()+" / value: "+arg.getValue());
					xtraParametersMap.put(arg.getName(), getStringValue(arg.getValue()));
				}
				break;
			}
		}
		for(Map.Entry<String, String> e: positionalValues.entrySet()) {
			params.add(e.getValue());
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
		
		//log.debug("keyValues: "+keyValues);
	}
	
	String getStringValue(Value<?> value) {
		if(value instanceof StringValue) {
			return ((StringValue) value).getValue();
		}
		if(value instanceof IntValue) {
			return ((IntValue) value).getValue().toString();
		}
		if(value instanceof FloatValue) {
			return ((FloatValue) value).getValue().toString();
		}
		if(value instanceof BooleanValue) {
			return String.valueOf(((BooleanValue) value).isValue());
		}
		log.warn("getStringValue: unknown type: "+value);
		return null;
	}
	
	void addValueToFilter(String field, String filter, Object value) {
		Map<String, String> ufilter = getUniFilter(filter);
		if(ufilter!=null) {
			ufilter.put(field, String.valueOf(value));
			return;
		}

		Map<String, String[]> mfilter = getMultiFilter(filter);
		if(mfilter!=null) {
			//log.info("f[MultiFilter]: "+filter+" / "+value+" / "+value.getClass());
			@SuppressWarnings("unchecked")
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
			//log.info("f[SetFilter]: "+filter+" / "+value+" / "+value.getClass());
			if(value instanceof Boolean && (Boolean) value) {
				sfilter.add(field);
			}
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

	@Override
	public Map<String, String> getParameterMapUniqueValues() {
		return xtraParametersMap;
	}
	
}
