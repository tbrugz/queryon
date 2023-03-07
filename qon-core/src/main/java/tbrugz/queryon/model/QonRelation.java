package tbrugz.queryon.model;

import java.util.List;

import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.ValidatableDBObject;

public interface QonRelation extends Relation, ValidatableDBObject {

	public List<String> getDefaultColumnNames();

	public void setDefaultColumnNames(List<String> defaultColumnNames);

}
