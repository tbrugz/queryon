package tbrugz.queryon.processor;

import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.action.QOnManage;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.sqldump.def.AbstractSQLProc;
import tbrugz.sqldump.util.Utils;

public class DiffProcessor extends AbstractSQLProc {

	static final Log log = LogFactory.getLog(DiffProcessor.class);
	
	static final String PROP_PREFIX = "queryon.DiffProcessor";
	
	static final String SUFFIX_APPLY = ".apply";

	boolean apply = false;

	@Override
	public void setProperties(Properties prop) {
		super.setProperties(prop);
		apply = Utils.getPropBool(prop, PROP_PREFIX+SUFFIX_APPLY, apply);
	}

	@Override
	public void process() {
		QOnManage qm = new QOnManage();
		try {
			log.info("Diffing model... [apply="+apply+"]");
			qm.diffModel(model, conn, apply, null);
			log.info("Model diffed");
		}
		catch(Exception e) {
			log.warn("Error diffing model: "+e.getMessage(), e);
			//log.debug("Error diffing model: "+e.getMessage(), e);
			throw new InternalServerException("Error diffing model: "+e.getMessage(), e);
		}
	}

}
