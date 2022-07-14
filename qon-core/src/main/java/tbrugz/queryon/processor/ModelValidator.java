package tbrugz.queryon.processor;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.SQL;
import tbrugz.queryon.util.DBObjectUtils;
import tbrugz.sqldump.dbmodel.Query;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.dbmodel.View;
import tbrugz.sqldump.def.AbstractSQLProc;
import tbrugz.sqldump.util.Utils;

public class ModelValidator extends AbstractSQLProc {

	static final Log log = LogFactory.getLog(ModelValidator.class);
	
	static final String PROP_PREFIX = "queryon.ModelValidator";

	static final String SUFFIX_REMOVE_INVALID = ".remove-invalid-objects";
	
	boolean removeInvalid = false;
	final boolean update = true; //XXX: update may change data type & lose nullable info
	//XXX: setInvalid...
	
	@Override
	public void setProperties(Properties prop) {
		super.setProperties(prop);
		removeInvalid = Utils.getPropBool(prop, PROP_PREFIX+SUFFIX_REMOVE_INVALID, removeInvalid);
	}
	
	@Override
	public void process() {
		
		int count = 0;
		int countErr = 0;
		long init = System.currentTimeMillis();
		
		// tables
		Iterator<Table> it1 = model.getTables().iterator();
		while(it1.hasNext()) {
			Table rel = it1.next();
			try {
				DBObjectUtils.validateTable(rel, conn, update);
			}
			catch(SQLException e) {
				if(removeInvalid) { it1.remove(); }
				log.warn("Error validating table '"+rel.getFinalQualifiedName()+"': "+e);
				log.debug("Error validating table '"+rel.getFinalQualifiedName()+"': "+e.getMessage(), e);
				countErr++;
			}
			
			count++;
		}
		
		// views & queries
		Iterator<View> it2 = model.getViews().iterator();
		while(it2.hasNext()) {
			View rel = it2.next();
			if(rel instanceof Query) {
				try {
					Query q = (Query) rel;
					SQL sql = SQL.createSQL(q, null, null);
					String sqlWithNamedParams = sql.getInitialSql();
					DBObjectUtils.validateQuery(q, sqlWithNamedParams, conn, update);
				}
				catch(RuntimeException e) {
					if(removeInvalid) { it2.remove(); }
					log.warn("Error with query '"+rel.getFinalQualifiedName()+"': "+e);
					log.debug("Error with query '"+rel.getFinalQualifiedName()+"': "+e.getMessage(), e);
					countErr++;
				}
				catch(SQLException e) {
					if(removeInvalid) { it2.remove(); }
					log.warn("Error with query '"+rel.getFinalQualifiedName()+"': "+e);
					log.debug("Error with query '"+rel.getFinalQualifiedName()+"': "+e.getMessage(), e);
					countErr++;
				}
				count++;
			}
			else {
				try {
					DBObjectUtils.validateTable(rel, conn, update);
				}
				catch(RuntimeException e) {
					if(removeInvalid) { it2.remove(); }
					log.warn("Error with table '"+rel.getFinalQualifiedName()+"': "+e);
					log.debug("Error with table '"+rel.getFinalQualifiedName()+"': "+e.getMessage(), e);
					countErr++;
				}
				catch(SQLException e) {
					if(removeInvalid) { it2.remove(); }
					log.warn("Error with table '"+rel.getFinalQualifiedName()+"': "+e);
					log.debug("Error with table '"+rel.getFinalQualifiedName()+"': "+e.getMessage(), e);
					countErr++;
				}
				count++;
			}
		}
		
		// executables
		/*
		Iterator<ExecutableObject> it3 = model.getExecutables().iterator();
		while(it3.hasNext()) {
			ExecutableObject eo = it3.next();
			try {
				DBObjectUtils.validateExecutable(eo, conn, false);
			}
			catch(SQLException e) {
				if(removeInvalid) { it2.remove(); }
				log.warn(e);
				countErr++;
			}
			count++;
		}
		*/
		
		log.info((count-countErr)+" of "+count+" objects sucessfully validated [elapsed="+(System.currentTimeMillis()-init)+"ms]");
	}

}
