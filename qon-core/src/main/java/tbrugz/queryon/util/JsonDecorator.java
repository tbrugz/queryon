package tbrugz.queryon.util;

import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.util.StringDecorator;

public class JsonDecorator extends StringDecorator {

	@Override
	public String get(String str) {
		if(str==null) {
			return "null";
		}
		return DataDumpUtils.getFormattedJSONString(str);
	}
	
}
