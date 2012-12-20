package tbrugz.queryon;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import tbrugz.sqldump.datadump.DumpSyntax;
import tbrugz.sqldump.util.Utils;

public class DumpSyntaxUtils {
	
	Map<String, DumpSyntax> syntaxesByFormat = new HashMap<String, DumpSyntax>();
	Map<String, DumpSyntax> syntaxesByFileExtension = new HashMap<String, DumpSyntax>();
	Map<String, DumpSyntax> syntaxesByMimeType = new HashMap<String, DumpSyntax>();

	public DumpSyntaxUtils(Properties prop) {
		for(Class<? extends DumpSyntax> dsc: DumpSyntax.getSyntaxes()) {
			DumpSyntax ds = (DumpSyntax) Utils.getClassInstance(dsc);
			if(ds!=null) {
				ds.procProperties(prop);
				syntaxesByFormat.put(ds.getSyntaxId(), ds);
				syntaxesByFileExtension.put(ds.getDefaultFileExtension(), ds);
				syntaxesByMimeType.put(ds.getMimeType(), ds);
			}
		}
	}
	
	DumpSyntax getDumpSyntax(String format, Properties prop) {
		//XXX file format (id) preferred to file extension. which way is better?
		DumpSyntax dsx = syntaxesByFormat.get(format);
		if(dsx!=null) { return dsx; }
		return syntaxesByFileExtension.get(format);
	}

	DumpSyntax getDumpSyntaxByAccept(String mimetype, Properties prop) {
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

}
