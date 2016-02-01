package tbrugz.queryon.processor;

import java.io.IOException;
import java.io.Writer;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.UpdatePlugin;
import tbrugz.sqldump.JDBCSchemaGrabber;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.DBIdentifiable;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.ExecutableParameter;
import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.dbmodel.Relation;
import tbrugz.sqldump.def.AbstractSQLProc;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.util.Utils;

/*
 * XXX: do not allow qon_execs to update schema_name or name... or reload all executables on save...
 */
public class QOnExecs extends AbstractSQLProc implements UpdatePlugin {

	static final Log log = LogFactory.getLog(QOnExecs.class);
	
	static final String PROP_PREFIX = "queryon.qon-execs";
	
	static final String SUFFIX_TABLE = ".table";
	static final String SUFFIX_EXECS_NAMES = ".names";

	static final String PIPE_SPLIT = "\\|";
	
	public static final String DEFAULT_EXECS_TABLE = "QON_EXECS";

	Writer writer;

	@Override
	public void process() {
		try {
			int count = readFromDatabase();
			if(writer!=null) {
				writer.write(String.valueOf(count));
			}
		} catch (SQLException e) {
			throw new BadRequestException("SQL exception: "+e, e);
		} catch (IOException e) {
			throw new BadRequestException("IO exception: "+e, e);
		} catch (Exception e) {
			throw new BadRequestException("Exception: "+e, e);
		}
	}

	int readFromDatabase() throws SQLException {
		String qonExecsTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_EXECS_TABLE);
		List<String> names = Utils.getStringListFromProp(prop, PROP_PREFIX+SUFFIX_EXECS_NAMES, ",");
		String sql = "select schema_name, name, remarks, roles_filter, exec_type"
				+ ", package_name, parameter_count, parameter_names, parameter_types, parameter_inouts"
				+ ", body"
				+ " from "+qonExecsTable
				+ " where (disabled = 0 or disabled is null)"
				+(names!=null?" and name in ("+Utils.join(names, ",", QOnTables.sqlStringValuesDecorator)+")":""); //XXX: possible sql injection?
				;
		
		/*
	schema_name varchar(100),
	name varchar(100) not null,
	remarks varchar(400),
	roles_filter varchar(1000),
	exec_type varchar(100),
	package_name varchar(100),
	body clob,
	parameter_count integer,
	parameter_names varchar(1000),
	parameter_types varchar(1000),
	parameter_inouts varchar(1000),
	constraint qon_execs_pk primary key (name) 
		 */
		
		ResultSet rs = null;
		try {
			PreparedStatement st = conn.prepareStatement(sql);
			rs = st.executeQuery();
		}
		catch(SQLException e) {
			throw new SQLException("Error fetching execs, sql: "+sql, e);
		}

		final DBMSResources res = DBMSResources.instance();
		final DBMSFeatures feat = res.getSpecificFeatures(conn.getMetaData());
		final DatabaseMetaData dbmd = feat.getMetadataDecorator(conn.getMetaData());
		JDBCSchemaGrabber jgrab = new JDBCSchemaGrabber();
		List<ExecutableObject> execs = new ArrayList<ExecutableObject>();
		Set<String> schemasGrabbed = new HashSet<String>();
		
		int count = 0;
		while(rs.next()) {
			String schema = rs.getString(1);
			String name = rs.getString(2);
			String remarks = rs.getString(3);
			String roles_filter_str = rs.getString(4);
			String exec_type = rs.getString(5);
			String packageName = rs.getString(6);
			int parameterCount = rs.getInt(7);
			String parameter_names_str = rs.getString(8);
			String parameter_types_str = rs.getString(9);
			String parameter_inouts_str = rs.getString(10);
			//String body = rs.getString(11);
			
			List<String> rolesFilter = Utils.getStringList(roles_filter_str, PIPE_SPLIT);
			List<String> parameterNames = Utils.getStringList(parameter_names_str, PIPE_SPLIT);
			List<String> parameterTypes = Utils.getStringList(parameter_types_str, PIPE_SPLIT);
			List<String> parameterInouts = Utils.getStringList(parameter_inouts_str, PIPE_SPLIT);
			
			try {
				if(!schemasGrabbed.contains(schema)) {
					execs.addAll(jgrab.doGrabFunctions(dbmd, schema, false));
					execs.addAll(jgrab.doGrabProcedures(dbmd, schema, false));
					//log.info("execs: "+execs);
					schemasGrabbed.add(schema);
				}

				if(containsExecutableWithName(execs, schema, name)) {
					count += addExecutable(schema, name, remarks, rolesFilter, exec_type,
							packageName, parameterCount, parameterNames, parameterTypes, parameterInouts);
				}
				else {
					log.warn("executable '"+(schema!=null?schema+".":"")+(packageName!=null?packageName+".":"")+name+"' not found");
					//XXX: throw exception?
				}
			}
			catch(SQLException e) {
				log.warn("error reading table '"+name+"': "+e);
				throw e;
			}
		}
		
		log.info("QOnExecs processed [added/replaced "+count+" executables]");
		return count;
	}
	
	int addExecutable(String schema, String execName, String remarks, List<String> rolesFilter, String execType,
			String packageName, int parameterCount, List<String> parameterNames, List<String> parameterTypes, List<String> parameterInouts) {
		ExecutableObject e = new ExecutableObject();
		e.setSchemaName(schema);
		e.setName(execName);
		e.setPackageName(packageName);
		e.setRemarks(remarks);
		if(execType==null) {
			throw new BadRequestException("Executable 'type' is mandatory");
		}
		//log.info("inouts: "+parameterInouts);
		e.setType(DBObjectType.valueOf(execType)); // execType.toUpperCase()
		List<ExecutableParameter> eps = new ArrayList<ExecutableParameter>();
		for(int i=0;i<parameterCount;i++) {
			ExecutableParameter ep = new ExecutableParameter();
			if(parameterNames!=null && parameterNames.size()>i) {
				ep.setName(parameterNames.get(i));
			}
			if(parameterTypes!=null && parameterTypes.size()>i) {
				ep.setDataType(parameterTypes.get(i));
			}
			if(parameterInouts!=null && parameterInouts.size()>i && !"".equals(parameterInouts.get(i)) ) {
				ep.setInout(ExecutableParameter.INOUT.valueOf(parameterInouts.get(i)));
			}
			//log.info("ExecParam["+i+"]: "+ep);
			eps.add(ep);
		}
		e.setParams(eps);
		if(e.getType()==DBObjectType.FUNCTION) {
			ExecutableParameter ep = new ExecutableParameter();
			e.setReturnParam(ep);
		}
		if(rolesFilter!=null) {
			for(String g: rolesFilter) {
				Grant gr = new Grant(schema, PrivilegeType.EXECUTE, g);
				e.getGrants().add(gr);
			}
		}
		
		//e.setBody(body);
		
		//TODOne: validate executable! PreparedStatement, ResultSetMetadata ? see QueryOnInstant.grabExecutables()...
		
		if(model.getExecutables().contains(e)) {
			model.getExecutables().remove(e);
		}
		
		//log.info("Exec: "+e+" ; pc="+parameterCount);
		return model.getExecutables().add(e)?1:0;
	}
	
	@Override
	public boolean acceptsOutputWriter() {
		return true;
	}
	
	@Override
	public void setOutputWriter(Writer writer) {
		this.writer = writer;
	}
	
	boolean containsExecutableWithName(List<ExecutableObject> execs, String schema, String name) {
		for(ExecutableObject exec: execs) {
			if( ( (exec.getSchemaName()==null && schema==null) || exec.getSchemaName().equalsIgnoreCase(schema))
				&& exec.getName().equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onInit() {
		process();
	}

	@Override
	public void onInsert(Relation relation, RequestSpec reqspec) {
		if(!isQonExecsRelation(relation)) { return; }
		//XXX: validate new executable?
		boolean added = createQonExec(reqspec);
		log.info("onInsert: added "+relation+"? "+added);
	}

	@Override
	public void onUpdate(Relation relation, RequestSpec reqspec) {
		ExecutableObject eo = getQOnExecutableFromModel(relation, reqspec);
		if(eo==null) {
			return;
		}
		//XXX: validate updated executable?
		boolean removed = model.getExecutables().remove(eo);
		boolean added = createQonExec(reqspec);
		log.info("onUpdate: removed "+eo+"? "+removed+" ; added "+relation+"? "+added);
	}

	@Override
	public void onDelete(Relation relation, RequestSpec reqspec) {
		ExecutableObject eo = getQOnExecutableFromModel(relation, reqspec);
		if(eo==null) {
			return;
		}
		boolean removed = model.getExecutables().remove(eo);
		log.info("onDelete: removed "+eo+"? "+removed);
	}
	
	boolean createQonExec(RequestSpec reqspec) {
		Map<String,String> v = reqspec.getUpdateValues();
		
		try {
			return addExecutable(v.get("SCHEMA_NAME"), v.get("NAME"), v.get("REMARKS"), Utils.getStringList(v.get("ROLES_FILTER"), PIPE_SPLIT), v.get("EXEC_TYPE"),
					v.get("PACKAGE_NAME"), Integer.valueOf(v.get("PARAMETER_COUNT")), Utils.getStringList(v.get("PARAMETER_NAMES"), PIPE_SPLIT), Utils.getStringList(v.get("PARAMETER_TYPES"), PIPE_SPLIT), Utils.getStringList(v.get("PARAMETER_INOUTS"), PIPE_SPLIT))
					>0;
		}
		catch(IllegalArgumentException e) {
			throw new BadRequestException("Error adding Executable: "+e.getMessage(), e);
		}
	}
	
	boolean isQonExecsRelation(Relation relation) {
		String qonExecsTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_EXECS_TABLE);
		//log.info("isQonExecsRelation: qonExecsTable: "+qonExecsTable+" relation.getName(): "+relation.getName()+" ; relation.getSchemaName(): "+relation.getSchemaName()); 
		if( (! qonExecsTable.equalsIgnoreCase(relation.getName()))
			&& (! qonExecsTable.equalsIgnoreCase(relation.getSchemaName()+"."+relation.getName())) ) {
			//log.info("isQonExecsRelation: no qon_execs:: qonExecsTable: "+qonExecsTable+" relation.getName(): "+relation.getName()+" ; relation.getSchemaName(): "+relation.getSchemaName()); 
			return false;
		}
		return true;
	}
	
	ExecutableObject getQOnExecutableFromModel(Relation relation, RequestSpec reqspec) {
		if(!isQonExecsRelation(relation)) { return null; }
		
		ExecutableObject eo = (ExecutableObject) DBIdentifiable.getDBIdentifiableByName(model.getExecutables(), reqspec.getParams().get(0));
		
		if(eo==null) {
			log.warn("getQOnExecutableFromModel: eo: "+eo+" ; reqspec.getParams(): "+reqspec.getParams());
			return null;
		}
		
		return eo;
	}
	
}
