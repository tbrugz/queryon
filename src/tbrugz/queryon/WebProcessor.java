package tbrugz.queryon;

import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.subject.Subject;

import tbrugz.sqldump.dbmodel.DBIdentifiable;

public interface WebProcessor {

	void setDBIdentifiable(DBIdentifiable dbid);

	//void setRelation(Relation relation);
	//void setModel(SchemaModel model);
	
	void setSubject(Subject currentUser);

	void process(RequestSpec reqspec, HttpServletResponse resp);

}
