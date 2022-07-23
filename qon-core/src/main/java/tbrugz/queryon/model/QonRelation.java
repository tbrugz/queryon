package tbrugz.queryon.model;

import java.util.List;

import tbrugz.sqldump.dbmodel.Relation;

public interface QonRelation extends Relation {

	public List<String> getDefaultColumnNames();

	public void setDefaultColumnNames(List<String> defaultColumnNames);

}
