package tbrugz.queryon.processor;

import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.UpdatePlugin;
import tbrugz.sqldump.def.AbstractSQLProc;

public abstract class AbstractUpdatePlugin extends AbstractSQLProc implements UpdatePlugin {

	static final Log log = LogFactory.getLog(AbstractUpdatePlugin.class);
	
	String modelId;
	
	@Override
	public void setModelId(String modelId) {
		this.modelId = modelId;
	}
	
	protected String getProperty(String prefix, String suffix, String defaultValue) {
		/*
		String ret = prop.getProperty(prefix+"@"+modelId+suffix);
		if(ret!=null) { return ret; }
		return prop.getProperty(prefix+suffix, defaultValue);
		*/
		String ret = getProperty(prop, modelId, prefix, suffix, defaultValue);
		//log.info("getProperty(): "+ret+" ;; modelId = "+modelId+" / prefix = "+prefix+" / suffix = "+suffix);
		return ret;
	}

	public static String getProperty(Properties prop, String modelId, String prefix, String suffix, String defaultValue) {
		String ret = prop.getProperty(prefix+"@"+modelId+suffix);
		if(ret!=null) { return ret; }
		return prop.getProperty(prefix+suffix, defaultValue);
	}
	
}
