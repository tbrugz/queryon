package tbrugz.queryon.graphql;

import java.util.Arrays;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

public class DF1<T> implements DataFetcher<T> {

	String[] sarr = new String[]{"a", "b", "c", "d"};
	
	@Override
	public T get(DataFetchingEnvironment environment) {
		return (T) Arrays.asList(sarr);
	}
	
}
