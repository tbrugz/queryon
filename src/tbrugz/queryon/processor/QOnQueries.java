package tbrugz.queryon.processor;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.UpdatePlugin;
import tbrugz.queryon.util.DBObjectUtils;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.util.Utils;

public class QOnQueries extends QOnQueriesProcessor implements UpdatePlugin {

	static final Log log = LogFactory.getLog(QOnQueries.class);
	
	/*static final String PROP_PREFIX = "queryon.qon-queries";
	
	static final String SUFFIX_ACTION = ".action";
	static final String SUFFIX_TABLE = ".table";
	static final String SUFFIX_QUERY_NAMES = ".querynames";
	static final String SUFFIX_LIMIT_INSERT_EXACT = ".limit.insert.exact";
	static final String SUFFIX_LIMIT_UPDATE_EXACT = ".limit.update.exact";
	static final String SUFFIX_METADATA_ALLOW_QUERY_EXEC = ".metadata.allow-query-exec";
	
	static final String DEFAULT_QUERIES_TABLE = "qon_queries";*/

	static final String PIPE_SPLIT = "\\|";
	
	String getQonQueriesTable() {
		return prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_QUERIES_TABLE);
	}
	
	boolean isQonQueriesRelation(Relation relation) {
		String tablename = getQonQueriesTable();
		if( (! tablename.equalsIgnoreCase(relation.getName()))
			&& (! tablename.equalsIgnoreCase(relation.getSchemaName()+"."+relation.getName())) ) {
			return false;
		}
		return true;
	}
	
	void addGrants(Relation r, String owner, PrivilegeType pt, List<String> roles) {
		if(roles==null) { return; }
		List<Grant> grants = new ArrayList<Grant>();
		
		for(String s: roles) {
			grants.add(new Grant(owner, pt, s));
		}
		
		if(grants.size()>0) {
			r.setGrants(grants);
		}
	}
	
	Query newQuery(String schema, String name, String sql, String remarks, List<String> rolesSelect) {
		Query q = new Query();
		q.setSchemaName(schema);
		q.setName(name);
		q.setQuery(sql);
		q.setRemarks(remarks);
		//XXX t.setParameterCount(parameterCount);
	
		addGrants(q, name, PrivilegeType.SELECT, rolesSelect);
		return q;
	}
	
	Query createQonQuery(RequestSpec reqspec) {
		Map<String, String> v = reqspec.getUpdateValues();
	
		Query q = newQuery(v.get("SCHEMA_NAME"), v.get("NAME"), v.get("SQL"), v.get("REMARKS"),
				Utils.getStringList(v.get("ROLES"), PIPE_SPLIT));
		try {
			DBObjectUtils.validateQuery(q, conn, true);
		}
		catch(SQLException e) {
			log.warn("Error validating query:"+e, e);
		}
	
		if(model.getViews().contains(q)) {
			model.getViews().remove(q);
		}
		boolean added = model.getViews().add(q);
		if(added) {
			return q;
		}
		return null;
	}
	
	Query getQOnQueryFromModel(RequestSpec reqspec) {
		return (Query) DBIdentifiable.getDBIdentifiableByTypeAndName(model.getViews(), DBObjectType.VIEW, reqspec.getParams().get(0));
	}
	
	@Override
	public void onInit(ServletContext context) {
		// TODO: do not depend on QOnQueriesProcessor / SQLQueries
		process(context);
	}
	
	@Override
	public void onInsert(Relation relation, RequestSpec reqspec) {
		if(!isQonQueriesRelation(relation)) { return; }
	
		Query q = createQonQuery(reqspec);
		boolean added = q!=null;
		log.info("onInsert: added "+relation+"? "+added);
	}
	
	@Override
	public void onUpdate(Relation relation, RequestSpec reqspec) {
		if(!isQonQueriesRelation(relation)) { return; }
	
		Query q = getQOnQueryFromModel(reqspec);
	
		boolean removed = false;
		if(q==null) {
			log.warn("onUpdate: qon_query not found on model: "+reqspec.getParams()+" ; "+relation); //+" ; uv: "+uv);
		}
		else {
			removed = model.getViews().remove(q);
		}
	
		boolean added = false;
		Query qnew = null;
		try {
			qnew = createQonQuery(reqspec);
			added = qnew!=null;
		}
		catch(BadRequestException e) {
			if(q!=null) {
				// "model rollback"
				model.getViews().add(q);
			}
			throw e;
		}
		log.info("onUpdate: removed "+q+"? "+removed+" ; added "+qnew+"? "+added+" [qonRelation="+relation+"]");
	}
	
	@Override
	public void onDelete(Relation relation, RequestSpec reqspec) {
		if(!isQonQueriesRelation(relation)) { return; }
	
		Query q = getQOnQueryFromModel(reqspec);
		if(q==null) {
			//log.warn("onDelete: qon_table not found on model: "+reqspec.getParams()+" ; "+qonRelation);
			return;
		}
		boolean removed = model.getViews().remove(q);
		log.info("onDelete: removed "+q+"? "+removed);
	}
	
	@Override
	protected int addQueryFromDB(String schemaName, String queryName, PreparedStatement stmt, String sql, String remarks,
			String rolesFilterStr, ServletContext context) {
		Query q = newQuery(schemaName, queryName, sql, remarks,
				Utils.getStringList(rolesFilterStr, PIPE_SPLIT));
		try {
			DBObjectUtils.validateQuery(q, conn, true);
		}
		catch(SQLException e) {
			log.warn("Error validating query:"+e, e);
		}
	
		if(model.getViews().contains(q)) {
			model.getViews().remove(q);
		}
		boolean added = model.getViews().add(q);
		if(added) {
			return 1;
		}
		return 0;
	}

}

