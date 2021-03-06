package tbrugz.queryon.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

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
	
	public final Set<String> syntaxIds = new TreeSet<String>();
	public final Set<String> syntaxExtensions = new TreeSet<String>();
	public final Set<String> syntaxMimeTypes = new TreeSet<String>();

	public DumpSyntaxUtils(Properties prop) {
		int dscount = 0;
		for(Class<? extends DumpSyntax> dsc: DumpSyntaxRegistry.getSyntaxes()) {
			DumpSyntax ds = (DumpSyntax) Utils.getClassInstance(dsc);
			if(ds!=null) {
				ds.procProperties(prop);
				//ds.needsDBMSFeatures(); ds.setFeatures(null); //no needed... features will be set by RequestSpec
				//XXX: what if syntax was already putted?
				if(syntaxesByFormat.containsKey(ds.getSyntaxId())) {
					log.info("syntaxId ["+ds.getSyntaxId()+"] already loaded: "+syntaxesByFormat.get(ds.getSyntaxId()).getClass().getSimpleName()+" [wont load "+ds.getClass().getSimpleName()+" in syntaxesByFormat]");
				}
				else {
					syntaxesByFormat.put(ds.getSyntaxId(), ds);
				}

				if(syntaxesByFileExtension.containsKey(ds.getDefaultFileExtension())) {
					log.info("syntaxExt ["+ds.getDefaultFileExtension()+"] already loaded: "+syntaxesByFileExtension.get(ds.getDefaultFileExtension()).getClass().getSimpleName()+" [wont load "+ds.getClass().getSimpleName()+" in syntaxesByFileExtension]");
				}
				else {
					syntaxesByFileExtension.put(ds.getDefaultFileExtension(), ds);
				}

				if(syntaxesByMimeType.containsKey(ds.getMimeType())) {
					log.info("syntaxMime ["+ds.getMimeType()+"] already loaded: "+syntaxesByMimeType.get(ds.getMimeType()).getClass().getSimpleName()+" [wont load "+ds.getClass().getSimpleName()+" in syntaxesByMimeType]");
				}
				else {
					syntaxesByMimeType.put(ds.getMimeType(), ds);
				}
				
				syntaxIds.add(ds.getSyntaxId());
				syntaxExtensions.add(ds.getDefaultFileExtension());
				syntaxMimeTypes.add(ds.getMimeType());
				
				log.debug("syntax '"+ds.getClass().getSimpleName()+"': id="+ds.getSyntaxId()+" ; ext="+ds.getDefaultFileExtension()+" ; mime="+ds.getMimeType());
				dscount++;
			}
		}
		log.debug("#syntaxesByFormat: "+syntaxesByFormat.size()+" ; #syntaxExtensions: "+syntaxExtensions.size()+" ; #syntaxMimeTypes: "+syntaxMimeTypes.size()+" ; count: "+dscount);
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
