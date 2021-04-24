package tbrugz.queryon.diff;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.QueryOnSchema;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.queryon.util.DBUtil;
import tbrugz.sqldiff.model.ChangeType;
import tbrugz.sqldiff.model.Diff;
import tbrugz.sqldump.util.ConnectionUtil;

public class DiffUtilQon {

	static final Log log = LogFactory.getLog(DiffUtilQon.class);
	
	public static void applyDiffs(List<Diff> diffs, Properties prop, String modelId, boolean addComments, HttpServletResponse resp) throws IOException {
		Connection conn = null;
		try {
			conn = DBUtil.initDBConn(prop, modelId);
			applyDiffs(diffs, conn, modelId, addComments, resp);
		}
		catch(SQLException e) {
			throw new BadRequestException("Error: ["+e+"]");
		}
		catch(ClassNotFoundException e) {
			throw new InternalServerException("Error: ["+e+"]");
		}
		catch(NamingException e) {
			throw new InternalServerException("Error: ["+e+"]");
		}
		finally {
			ConnectionUtil.closeConnection(conn);
		}
	}
	
	public static void applyDiffs(List<Diff> diffs, Connection conn, String modelId, boolean addComments, HttpServletResponse resp) throws IOException {
		String sql = null;
		try {
			int executeCount = 0;
			StringBuilder sb = new StringBuilder();
			for(Diff d: diffs) {
				List<String> sqls = d.getDiffList();
				for(String s: sqls) {
					if(s==null || s.equals("")) { continue; }
					sql = s;
					
					Statement st = conn.createStatement();
					boolean retIsRs = st.execute(sql);
					int count = st.getUpdateCount();
					log.debug("Diff executed: "+sql);
					if(resp!=null) {
						sb.append(sql);
						if(addComments) {
							sb.append(" /* model="+modelId+" ; ret=[isRS="+retIsRs+",count="+count+"] */");
						}
						sb.append((d.getObjectType().isExecutableType() && d.getChangeType().equals(ChangeType.ADD))? "\n" : ";\n");
					}
					executeCount++;
				}
			}
			//XXX: commit after postHooks have run?
			DBUtil.doCommit(conn);
			log.info(executeCount+" diffs applyed");

			if(resp!=null) {
				resp.setContentType(QueryOnSchema.MIME_SQL);
				if(executeCount==0) {
					resp.getWriter().write("-- no diffs applyed");
				}
				resp.getWriter().write(sb.toString());
			}
		}
		catch(SQLException e) {
			throw new BadRequestException("Error: ["+e+"] ; sql =\n"+sql, e);
		}
		finally {
			//ConnectionUtil.closeConnection(conn);
		}
	}
	
}
