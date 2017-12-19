package tbrugz.queryon.processor;

import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.SQL;
import tbrugz.queryon.util.DBObjectUtils;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.def.AbstractSQLProc;

public class ModelValidator extends AbstractSQLProc {

	static final Log log = LogFactory.getLog(ModelValidator.class);
	
	@Override
	public void process() {
		boolean update = true;
		
		try {
			int count = 0;
			long init = System.currentTimeMillis();
			
			for(Table rel: model.getTables()) {
				DBObjectUtils.validateTable(rel, conn, update);
				count++;
			}
			for(View rel: model.getViews()) {
				if(rel instanceof Query) {
					Query q = (Query) rel;
					SQL sql = SQL.createSQL(q, null, null);
					DBObjectUtils.validateQuery(q, sql.getFinalSql(), conn, update);
					count++;
				}
				else {
					DBObjectUtils.validateTable(rel, conn, update);
					count++;
				}
			}
			//TODO: validate executables
			
			log.info(count+" objects validated [elapsed="+(System.currentTimeMillis()-init)+"ms]");
		}
		catch(SQLException e) {
			log.warn(e);
		}
	}

}
