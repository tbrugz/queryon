package tbrugz.queryon.model;

import java.util.List;

import tbrugz.sqldump.dbmodel.Query;

public class QonQuery extends Query implements QonRelation {

	private static final long serialVersionUID = 1L;
	
	List<String> defaultColumnNames;
	
	@Override
	public List<String> getDefaultColumnNames() {
		return defaultColumnNames;
	}
	
	public void setDefaultColumnNames(List<String> defaultColumnNames) {
		this.defaultColumnNames = defaultColumnNames;
	}

}
