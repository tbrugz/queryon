package tbrugz.queryon.diff.hook;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.diff.ApplyHook;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.util.StringUtils;

public class ShellHook implements ApplyHook {
	
	static final Log log = LogFactory.getLog(ShellHook.class);

	public static final String DEFAULT_ID = "sh";
	
	public static final String PREFIX = "queryon.diff.apply.hook.";

	public static final String SUFFIX_CMD = ".cmd";
	//public static final String PROP_CMD = PREFIX + SUFFIX_CMD;
	
	//Properties prop;
	String cmd;
	String id = DEFAULT_ID;
	//XXX add List<Integer> okExitStatus ? not just '0' 
	
	@Override
	public void setProperties(Properties prop) {
		//this.prop = prop;
		cmd = prop.getProperty(getPropPrefix()+SUFFIX_CMD);
	}
	
	@Override
	public void setId(String id) {
		this.id = id;
	}
	
	@Override
	public String getPropPrefix() {
		return PREFIX+id;
	}
	
	@Override
	public String run(ApplyHook.ApplyMessage am) {
		String script = cmd
				.replace("[message]", am.message.replace("\"", ""))
				.replace("[username]", am.username)
				.replace("[object-type]", am.objectType)
				.replace("[object-schema]", am.objectSchema)
				.replace("[object-name]", am.objectName)
				.replace("[model-base]", am.modelBase)
				.replace("[model-apply]", am.modelApply)
				;
		try {
			log.info("running: "+script);
			Process p = Runtime.getRuntime().exec(script);
			OutputStream os = p.getOutputStream();
			//XXX pipe diff content into os ?
			os.close();
			int exitValue = p.waitFor();
			String ret = StringUtils.readInputStream(p.getInputStream(), 8192);
			if(exitValue>0) {
				throw new InternalServerException("Error applying diff to '"+am.modelApply+"'"+
						"\n<br/><pre>status="+exitValue+"\n"+DataDumpUtils.xmlEscapeText(ret)+"</pre>");
			}
			log.info("returning: "+exitValue);
			return ret;
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
}
