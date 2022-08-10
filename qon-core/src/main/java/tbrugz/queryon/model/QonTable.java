package tbrugz.queryon.model;

import java.util.List;

import tbrugz.sqldump.dbmodel.Table;

public class QonTable extends Table implements QonRelation {

	private static final long serialVersionUID = 1L;

	List<String> defaultColumnNames;
	String sqlFilter;
	
	@Override
	public List<String> getDefaultColumnNames() {
		return defaultColumnNames;
	}
	
	@Override
	public void setDefaultColumnNames(List<String> defaultColumnNames) {
		this.defaultColumnNames = defaultColumnNames;
	}

	public String getSqlFilter() {
		return sqlFilter;
	}
	
	public void setSqlFilter(String sqlFilter) {
		this.sqlFilter = sqlFilter;
	}

}
