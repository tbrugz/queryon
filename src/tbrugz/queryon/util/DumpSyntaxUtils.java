package tbrugz.queryon.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.sqldump.datadump.DumpSyntax;
import tbrugz.sqldump.datadump.DumpSyntaxRegistry;
import tbrugz.sqldump.util.Utils;

public class DumpSyntaxUtils {
	static Log log = LogFactory.getLog(DumpSyntaxUtils.class);
	
	final Map<String, DumpSyntax> syntaxesByFormat = new HashMap<String, DumpSyntax>();
	final Map<String, DumpSyntax> syntaxesByFileExtension = new HashMap<String, DumpSyntax>();
	final Map<String, DumpSyntax> syntaxesByMimeType = new HashMap<String, DumpSyntax>();

	public DumpSyntaxUtils(Properties prop) {
		for(Class<? extends DumpSyntax> dsc: DumpSyntaxRegistry.getSyntaxes()) {
			DumpSyntax ds = (DumpSyntax) Utils.getClassInstance(dsc);
			if(ds!=null) {
				ds.procProperties(prop);
				//ds.needsDBMSFeatures(); ds.setFeatures(null); //no needed... features will be set by RequestSpec
				//XXX: what if syntax was already putted?
				syntaxesByFormat.put(ds.getSyntaxId(), ds);
				syntaxesByFileExtension.put(ds.getDefaultFileExtension(), ds);
				syntaxesByMimeType.put(ds.getMimeType(), ds);
				log.debug("syntax '"+ds.getClass().getSimpleName()+"': id="+ds.getSyntaxId()+" ; ext="+ds.getDefaultFileExtension()+" ; mime="+ds.getMimeType());
			}
		}
	}
	
	public DumpSyntax getDumpSyntax(String format) {
		//XXX file format (id) preferred to file extension. which way is better?
		DumpSyntax dsx = syntaxesByFormat.get(format);
		if(dsx!=null) { return dsx; }
		return syntaxesByFileExtension.get(format);
	}

	/*
	 * TODO: parse qvalue -- http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html ; parse type "/*"
	 */
	public DumpSyntax getDumpSyntaxByAccept(String mimetype) {
		if(mimetype==null) { return null; }
		String[] mimes = mimetype.split(",");
		if(mimes.length<1) return null;
		for(String mime: mimes) {
			for(String dsmime: syntaxesByMimeType.keySet()) {
				if(mime.contains(dsmime)) {
					return syntaxesByMimeType.get(dsmime);
				}
			}
		}
		return null;
	}

	public Map<String, DumpSyntax> getSyntaxesByFormat() {
		return syntaxesByFormat;
	}

}
