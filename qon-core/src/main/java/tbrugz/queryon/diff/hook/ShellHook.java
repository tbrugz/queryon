package tbrugz.queryon.diff.hook;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import java.util.regex.Pattern;

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
	
	static final Pattern NAME_REPALCER = Pattern.compile("[^a-zA-Z0-9_]");
	
	static String normalizeName(String s) {
		return NAME_REPALCER.matcher(s).replaceAll("");
	}
	
	static String normalizeMessage(String message) {
		return message.replaceAll("\"", "");
	}
	
	String getScriptString(ApplyHook.ApplyMessage am) {
		return cmd
				.replace("[message]", normalizeMessage(am.message) )
				.replace("[username]", normalizeName(am.username) )
				.replace("[object-type]", normalizeName(am.objectType) )
				.replace("[object-schema]", normalizeName(am.objectSchema) )
				.replace("[object-name]", normalizeName(am.objectName) )
				.replace("[model-base]", am.modelBase)
				.replace("[model-apply]", am.modelApply)
				;
	}
	
	@Override
	public String run(ApplyHook.ApplyMessage am) {
		try {
			String script = getScriptString(am);
			log.info("running: "+script);
			Process p = Runtime.getRuntime().exec(script);
			OutputStream os = p.getOutputStream();
			//XXX pipe diff content into os ?
			os.close();
			int exitValue = p.waitFor(); //XXX add timeout?
			String ret = StringUtils.readInputStream(p.getInputStream(), 8192);
			if(exitValue>0) {
				throw new InternalServerException("Error applying diff to '"+am.modelApply+"'"+
						"\n<br/><pre>status="+exitValue+"\n"+DataDumpUtils.xmlEscapeText(ret)+"</pre>");
			}
			log.info("returning: "+exitValue);
			return ret;
		} catch (IOException e) {
			log.warn("IOException: "+e);
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			log.warn("InterruptedException: "+e);
			//throw e;
			throw new RuntimeException(e);
		}
	}
	
}
