package tbrugz.queryon.graphql;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.QueryOn.ActionType;
import tbrugz.queryon.exception.NotFoundException;
import tbrugz.queryon.graphql.GqlSchemaFactory.QonAction;
import tbrugz.queryon.util.ShiroUtils;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;

public class QonDataFetcher<T> implements DataFetcher<T> {

	private static final Log log = LogFactory.getLog(QonDataFetcher.class);
	
	final SchemaModel sm;
	final Map<String, QonAction> actionMap;
	final GraphQlQonServlet servlet;
	final HttpServletRequest req;
	final HttpServletResponse resp;
	final Properties prop;
	
	public QonDataFetcher(SchemaModel sm, Map<String, QonAction> actionMap, GraphQlQonServlet servlet, HttpServletRequest req, HttpServletResponse resp) {
		this.sm = sm;
		this.actionMap = actionMap;
		this.servlet = servlet;
		this.req = req;
		this.resp = resp;
		
		this.prop = new Properties();
	}
	
	@Override
	public T get(DataFetchingEnvironment env) {
		log.info("env: args: "+env.getArguments()+" / source: "+env.getSource()+" / field: "+env.getField());
		log.info("env2: ctx: "+env.getContext()+" / exec-ctx: "+env.getExecutionContext()+" / exec-id: "+env.getExecutionId());
		log.info("env3: definition: "+env.getFieldDefinition()+"\n- fields: "+env.getFields()+"\n- field-type: "+env.getFieldType()+"\n- f-t-info: "+env.getFieldTypeInfo());
		log.info("env4: getSelectionSet: "+env.getSelectionSet());
		
		try {
			//GqlRequest reqspec = servlet.getRequestSpec(req); //servlet.getCurrentRequestSpec();
			GqlRequest reqspec = new GqlRequest(env, prop, req);
			//log.info("gcds(#1): "+reqspec.getCurrentDumpSyntax());
			
			QonAction action = actionMap.get(reqspec.object);
			if(action==null) {
				throw new NotFoundException("object not found in map: "+reqspec.object);
			}
			DBIdentifiable dbobj = getDBIdentifiable(action.dbType, action.objectName);
			if(dbobj==null) {
				throw new NotFoundException("object not found: "+reqspec.object);
			}

			//TODO mutation?
			//boolean mutation = false;
			String otype = QueryOn.getObjectType(dbobj);
			ActionType atype = action.atype;
			//ActionType atype = DBObjectType.EXECUTABLE.name().equals(otype)?ActionType.EXECUTE:
			//	(mutation?ActionType.EXECUTE:ActionType.SELECT);
			
			Subject currentUser = ShiroUtils.getSubject(servlet.getProperties(), req);
			ShiroUtils.checkPermission(currentUser, otype+":"+atype, reqspec.object);
			
			//log.info("gcds(#2): "+reqspec.getCurrentDumpSyntax());
			switch (atype) {
			case SELECT: {
				Relation rel = (Relation) dbobj;
				/*if(rel==null) {
					log.warn("strange... rel is null");
					rel = SchemaModelUtils.getRelation(sm, reqspec, true); //XXX: option to search views based on property?
				}
				if(! ShiroUtils.isPermitted(currentUser, doNotCheckGrantsPermission)) {
					checkGrantsAndRolesMatches(currentUser, PrivilegeType.SELECT, rel);
				}*/
				servlet.doSelect(sm, rel, reqspec, currentUser, resp, false);
				}
				break;
			case EXECUTE:
				//TODO: execute, insert, update, delete 
			default:
				log.warn("unknown action type: "+atype);
				return null;
			}
			
			GqlMapBufferSyntax mbs = reqspec.getCurrentDumpSyntax();
			//log.info("mbs: "+mbs);
			List<Map<String, Object>> list = mbs.getBuffer();
			log.info("list[#"+list.size()+"]: "+list);
			
			return (T) list;
			//return (T) reqspec.getCurrentDumpSyntax().getBuffer();
			
		}
		catch (BadRequestException e) {
			log.warn("BadRequestException: "+e);
			//throw e;
		}
		catch (ServletException | IOException | ClassNotFoundException | SQLException | NamingException e) {
			log.warn("Exception: "+e);
			//e.printStackTrace();
			//throw new RuntimeException(e);
		}
		
		return null;
	}
	
	<U extends DBIdentifiable> U getDBIdentifiable(DBObjectType type, String name) {
		U ret;
		if(type==DBObjectType.RELATION || type==DBObjectType.TABLE) {
			ret = DBIdentifiable.getDBIdentifiableByName(sm.getTables(), name);
			if(ret!=null) { return ret; }
		}
		if(type==DBObjectType.RELATION || type==DBObjectType.VIEW) {
			ret = DBIdentifiable.getDBIdentifiableByName(sm.getViews(), name);
			if(ret!=null) { return ret; }
		}
		if(type==DBObjectType.EXECUTABLE) {
			ret = DBIdentifiable.getDBIdentifiableByName(sm.getExecutables(), name);
			if(ret!=null) { return ret; }
		}
		return null;
	}

}
