package tbrugz.queryon.resultset;

import java.util.Arrays;
import java.util.List;

public class TestBean {
	int id;
	String description;
	String category;
	
	static String[] uniqueCols = {"id"};
	static String[] allCols = {"description", "category"};
	
	public TestBean(int id, String desc, String cat) {
		this.id = id;
		this.description = desc;
		this.category = cat;
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
	public void setId(int id) {
		this.id = id;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getCategory() {
		return category;
	}
	public void setCategory(String category) {
		this.category = category;
	}
	
}
