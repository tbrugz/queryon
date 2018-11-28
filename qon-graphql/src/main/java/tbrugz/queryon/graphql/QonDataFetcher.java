package tbrugz.queryon.graphql;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
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
import tbrugz.queryon.exception.ForbiddenException;
import tbrugz.queryon.exception.NotFoundException;
import tbrugz.queryon.graphql.GqlSchemaFactory.QonAction;
import tbrugz.queryon.util.ShiroUtils;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.dbmodel.SchemaModel;

public class QonDataFetcher<T> implements DataFetcher<T> {

	private static final Log log = LogFactory.getLog(QonDataFetcher.class);
	
	final SchemaModel sm;
	final Map<String, QonAction> actionMap;
	final Map<String, Map<String, String>> colMap;
	final GraphQlQonServlet servlet;
	final HttpServletRequest req;
	final HttpServletResponse resp;
	final Properties prop;
	
	public QonDataFetcher(SchemaModel sm, Map<String, QonAction> actionMap, Map<String, Map<String, String>> colMap, GraphQlQonServlet servlet, HttpServletRequest req, HttpServletResponse resp) {
		this.sm = sm;
		this.actionMap = actionMap;
		this.colMap = colMap;
		this.servlet = servlet;
		this.req = req;
		this.resp = resp;
		
		this.prop = new Properties();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public T get(DataFetchingEnvironment env) {
		/*
		log.info("env: args: "+env.getArguments()+" / source: "+env.getSource()+" / field: "+env.getField());
		log.info("env2: ctx: "+env.getContext()+" / exec-ctx: "+env.getExecutionContext()+" / exec-id: "+env.getExecutionId());
		log.info("env3: definition: "+env.getFieldDefinition()+"\n- fields: "+env.getFields()+"\n- field-type: "+env.getFieldType()+"\n- f-t-info: "+env.getFieldTypeInfo());
		log.info("env4: getSelectionSet: "+env.getSelectionSet());
		*/
		
		try {
			//GqlRequest reqspec = servlet.getRequestSpec(req); //servlet.getCurrentRequestSpec();
			GqlRequest reqspec = new GqlRequest(env, actionMap, colMap, prop, req);
			//log.info("gcds(#1): "+reqspec.getCurrentDumpSyntax());
			
			QonAction action = reqspec.action;
			if(action==null) {
				throw new NotFoundException("object not found (in actionMap): "+reqspec.getObject());
			}
			DBIdentifiable dbobj = getDBIdentifiable(action.dbType, action.objectName);
			if(dbobj==null) {
				throw new NotFoundException("object not found: "+reqspec.getObject()+" [objectName="+action.objectName+"]");
			}

			//TODO mutation?
			//boolean mutation = false;
			String otype = QueryOn.getObjectType(dbobj);
			ActionType atype = action.atype;
			//ActionType atype = DBObjectType.EXECUTABLE.name().equals(otype)?ActionType.EXECUTE:
			//	(mutation?ActionType.EXECUTE:ActionType.SELECT);
			
			Subject currentUser = ShiroUtils.getSubject(servlet.getProperties(), req);
			ShiroUtils.checkPermission(currentUser, otype+":"+atype, action.objectName);
			
			log.info("get:: object: "+reqspec.getObject()+" ; objType/aType: "+otype+"/"+atype+" ; exec-id: "+env.getExecutionId());
			//log.info("currentUser: "+currentUser.getPrincipal());
			//log.info("gcds(#2): "+reqspec.getCurrentDumpSyntax());
			
			//XXX: per-column permission on INSERT / UPDATE 
			switch (atype) {
			case SELECT: {
				Relation rel = (Relation) dbobj;
				servlet.doSelect(sm, rel, reqspec, currentUser, resp, false);
				}
				break;
			case INSERT: {
				servlet.doInsert((Relation) dbobj, reqspec, currentUser, true, resp);
				return (T) getUpdateCountMap(reqspec.updateCount);
				}
			case UPDATE: {
				servlet.doUpdate((Relation) dbobj, reqspec, currentUser, true, resp);
				return (T) getUpdateCountMap(reqspec.updateCount);
				}
			case DELETE: {
				servlet.doDelete((Relation) dbobj, reqspec, currentUser, resp);
				return (T) getUpdateCountMap(reqspec.updateCount);
				}
			case EXECUTE: {
				servlet.doExecute((ExecutableObject) dbobj, reqspec, currentUser, resp);
				return (T) getExecuteReturnMap(reqspec.executeOutput);
				}
			default:
				log.warn("unknown action type: "+atype);
				throw new BadRequestException("unknown action type: "+atype);
				//return null;
			}
			
			GqlMapBufferSyntax mbs = reqspec.getCurrentDumpSyntax();
			//log.info("mbs: "+mbs);
			List<Map<String, Object>> list = mbs.getBuffer();
			//log.info("list[#"+list.size()+"]: "+list);
			log.debug("list[#"+list.size()+"]");
			
			return (T) list;
			//return (T) reqspec.getCurrentDumpSyntax().getBuffer();
			
		}
		catch (ForbiddenException e) {
			log.warn("ForbiddenException: "+e);
			throw e;
		}
		catch (BadRequestException e) {
			log.warn("BadRequestException: "+e);
			throw e;
		}
		catch (ServletException | IOException | ClassNotFoundException | SQLException | NamingException e) {
			log.warn("Exception: "+e);
			//e.printStackTrace();
			throw new RuntimeException(e);
		}
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
		if(type.isExecutableType()) {
			ret = DBIdentifiable.getDBIdentifiableByName(sm.getExecutables(), name);
			if(ret!=null) { return ret; }
		}
		return null;
	}
	
	Map<String, Integer> getUpdateCountMap(int count) {
		Map<String, Integer> updateCount = new HashMap<>();
		updateCount.put(GqlSchemaFactory.FIELD_UPDATE_COUNT, count);
		return updateCount;
	}

	Map<String, String> getExecuteReturnMap(String returnValue) {
		Map<String, String> map = new HashMap<>();
		map.put(GqlSchemaFactory.FIELD_RETURN_VALUE, returnValue);
		return map;
	}
	
}
