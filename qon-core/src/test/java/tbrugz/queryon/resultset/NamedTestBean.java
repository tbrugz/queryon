package tbrugz.queryon.resultset;

import java.util.Arrays;
import java.util.List;

public class NamedTestBean {

	int id;
	String name;
	
	static String[] uniqueCols = {"id"};
	static String[] allCols = {"name"};
	
	public NamedTestBean(int id, String name) {
		this.id = id;
		this.name = name;
	}
	
	public static List<String> getUniqueCols() {
		return Arrays.asList(uniqueCols);
	}

	public static List<String> getAllCols() {
		return Arrays.asList(allCols);
	}
	
	public int getId() {
		return id;
	}
	
	public String getName() {
		return name;
	}
	
}
