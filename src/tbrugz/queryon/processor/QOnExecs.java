package tbrugz.queryon.processor;

import java.io.IOException;
import java.io.Writer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.sqldump.dbmodel.DBObjectType;
import tbrugz.sqldump.dbmodel.ExecutableObject;
import tbrugz.sqldump.dbmodel.ExecutableParameter;
import tbrugz.sqldump.def.AbstractSQLProc;
import tbrugz.sqldump.util.Utils;

public class QOnExecs extends AbstractSQLProc {

	static final Log log = LogFactory.getLog(QOnExecs.class);
	
	static final String PROP_PREFIX = "queryon.qon-execs";
	
	static final String SUFFIX_TABLE = ".table";
	//static final String SUFFIX_TABLE_NAMES = ".names";

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
		}
	}

	int readFromDatabase() throws SQLException {
		String qonExecsTable = prop.getProperty(PROP_PREFIX+SUFFIX_TABLE, DEFAULT_EXECS_TABLE);
		String sql = "select schema_name, name, remarks, roles_filter, exec_type"
				+ ", package_name, parameter_count, parameter_names, parameter_types, parameter_inouts"
				+ ", body"
				+ " from "+qonExecsTable
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
				count += addExecutable(schema, name, remarks, rolesFilter, exec_type,
						packageName, parameterCount, parameterNames, parameterTypes, parameterInouts);
			}
			catch(SQLException e) {
				log.warn("error reading table '"+name+"': "+e);
			}
		}
		
		log.info("QOnExecs processed [added/replaced "+count+" executables]");
		return count;
	}
	
	int addExecutable(String schema, String execName, String remarks, List<String> rolesFilter, String execType,
			String packageName, int parameterCount, List<String> parameterNames, List<String> parameterTypes, List<String> parameterInouts) throws SQLException {
		ExecutableObject e = new ExecutableObject();
		e.setSchemaName(schema);
		e.setName(execName);
		e.setPackageName(packageName);
		e.setRemarks(remarks);
		e.setType(DBObjectType.valueOf(execType));
		List<ExecutableParameter> eps = new ArrayList<ExecutableParameter>();
		for(int i=0;i<parameterCount;i++) {
			ExecutableParameter ep = new ExecutableParameter();
			if(parameterNames.size()>i) {
				if(parameterNames!=null && parameterNames.size()>i) {
					ep.setName(parameterNames.get(i));
				}
				if(parameterTypes!=null && parameterTypes.size()>i) {
					ep.setDataType(parameterTypes.get(i));
				}
				if(parameterInouts!=null && parameterInouts.size()>i) {
					ep.setInout(ExecutableParameter.INOUT.valueOf(parameterInouts.get(i)));
				}
			}
			//log.info("ExecParam["+i+"]: "+ep);
			eps.add(ep);
		}
		e.setParams(eps);
		if(e.getType()==DBObjectType.FUNCTION) {
			ExecutableParameter ep = new ExecutableParameter();
			e.setReturnParam(ep);
		}
		//e.setBody(body);
		
		//TODO: validate executable! PreparedStatement, ResultSetMetadata ?
		
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
	
}
