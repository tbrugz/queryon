package tbrugz.queryon.graphql;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

public class LogDataFetcher<T> implements DataFetcher<T> {

	private static final Log log = LogFactory.getLog(LogDataFetcher.class);
	
	@Override
	public T get(DataFetchingEnvironment env) {
		log.info("env: args: "+env.getArguments()+" / source: "+env.getSource()+" / field: "+env.getField());
		log.info("env2: ctx: "+env.getContext()+" / exec-ctx: "+env.getExecutionContext()+" / exec-id: "+env.getExecutionId());
		log.info("env3: definition: "+env.getFieldDefinition()+"\n- fields: "+env.getFields()+"\n- field-type: "+env.getFieldType()+"\n- f-t-info: "+env.getFieldTypeInfo());
		log.info("env4: getSelectionSet: "+env.getSelectionSet());
		log.info("env5: field.getSelectionSet: "+env.getField().getSelectionSet());
		
		return null;
	}

}
