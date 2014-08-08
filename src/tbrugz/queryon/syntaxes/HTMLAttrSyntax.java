package tbrugz.queryon.syntaxes;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.datadump.HTMLDataDump;
import tbrugz.sqldump.util.SQLUtils;

public class HTMLAttrSyntax extends HTMLDataDump {
	
	static final String[] SUFFIXES = {"_STYLE", "_CLASS", "_HREF"}; //XXX: change to enum!
	//static final List<String> ATTRIB = Arrays.asList(new String[]{SUFFIXES[0].substring(1).toLowerCase(), SUFFIXES[1].substring(1).toLowerCase()});
	static final List<String> ATTRIBS = Arrays.asList("style", "class");
	
	/*static final Map<String, StringDecorator> decorators = new HashMap<String, StringDecorator>();
	
	static class HRefDecorator extends StringDecorator {
		@Override
		public String get(String str) {
			return "<a href=\"\">"+str+"</a>";
		}
	}
	
	static {
		decorators.put(SUFFIXES[2], )
	}*/

	//XXX: add HREF/LINK suffix
	//static String[] ATTRS = {"style", "class"};
	//static final int SUFFIX_NAME_SIZE =  SUFFIXES[0].length();
	
	Set<String> finalColNames = new HashSet<String>();

	protected boolean hrefDumpTargetBlank = true; //XXX: add prop for 'hrefDumpTargetBlank'
	
	@Override
	public void initDump(String tableName, List<String> pkCols, ResultSetMetaData md) throws SQLException {
		this.tableName = tableName;
		numCol = md.getColumnCount();
		lsColNames.clear();
		lsColTypes.clear();
		finalColNames.clear();
		
		for(int i=0;i<numCol;i++) {
			boolean isFullColumn = true;
			String colname = md.getColumnName(i+1);
			
			for(String suf: SUFFIXES) {
				if(colname.endsWith(suf)) {
					isFullColumn = false;
				}
			}
			
			if(isFullColumn) {
				finalColNames.add(colname);
			}
			lsColNames.add(colname);
			lsColTypes.add(SQLUtils.getClassFromSqlType(md.getColumnType(i+1), md.getPrecision(i+1), md.getScale(i+1)));
		}
	}
	
	//XXX
	@Override
	public void dumpHeader(Writer fos) throws IOException {
		if(prepend!=null) { out(prepend, fos); }
		out("<table class='"+tableName+"'>", fos);
		StringBuffer sb = new StringBuffer();
		if(dumpColElement) {
			for(int i=0;i<lsColNames.size();i++) {
				if(finalColNames.contains(lsColNames.get(i))) {
					sb.append("\n\t<col class=\"type_"+lsColTypes.get(i).getSimpleName()+"\"/>");
				}
			}
		}
		sb.append("\n\t<tr>");
		for(int i=0;i<lsColNames.size();i++) {
			if(finalColNames.contains(lsColNames.get(i))) {
				sb.append("<th>"+lsColNames.get(i)+"</th>");
			}
		}
		out(sb.toString()+"</tr>\n", fos);
	}
	
	@Override
	public void dumpRow(ResultSet rs, long count, Writer fos) throws IOException, SQLException {
		StringBuffer sb = new StringBuffer();
		sb.append("\t"+"<tr>");
		Map<String, Map<String,String>> attrsVals = new HashMap<String, Map<String,String>>();
		List<Object> vals = SQLUtils.getRowObjectListFromRS(rs, lsColTypes, numCol, true);
		
		for(int i=0;i<lsColNames.size();i++) {
			String colName = lsColNames.get(i);
			if(!finalColNames.contains(colName)) {
				for(String suffix: SUFFIXES) {
					if(colName.endsWith(suffix)) {
						String fullCol = colName.substring(0, colName.length()-suffix.length());
						String attr = colName.substring(colName.length()-suffix.length()+1).toLowerCase();
						Map<String,String> attrs = attrsVals.get(fullCol);
						if(attrs==null) {
							attrs = new HashMap<String, String>();
							attrsVals.put(fullCol, attrs);
						}
						attrs.put(attr, DataDumpUtils.getFormattedXMLValue(vals.get(i), lsColTypes.get(i), floatFormatter, dateFormatter, null, escape));
						//System.out.println("fullCol="+fullCol+" ; attrs="+attrs);
					}
				}
			}
		}
		for(int i=0;i<lsColNames.size();i++) {
			String colName = lsColNames.get(i);
			if(finalColNames.contains(colName)) {
				Object value = DataDumpUtils.getFormattedXMLValue(vals.get(i), lsColTypes.get(i), floatFormatter, dateFormatter, nullValueStr, escape);
				Map<String,String> attrs = attrsVals.get(colName);
				String attrsStr = "";
				if(attrs!=null) {
					for(String key: attrs.keySet()) {
						if(ATTRIBS.contains(key)) {
							attrsStr += " "+key+"=\""+attrs.get(key)+"\"";
						}
						value = decorateValue(attrs, key, String.valueOf(value));
					}
				}
				sb.append( "<td"+attrsStr+">"+ value +"</td>");
			}
		}
		sb.append("</tr>");
		out(sb.toString()+"\n", fos);
	}
	
	String decorateValue(Map<String,String> attrs, String key, String value) {
		if("href".equals(key)) {
			String href = attrs.get(key);
			if(href!=null) {
				return "<a href=\""+attrs.get(key)+"\""+(hrefDumpTargetBlank?" target=\"_blank\"":"")+">"+value+"</a>";
			}
		}
		return value;
	}
	
	@Override
	public String getSyntaxId() {
		return "htmlx";
	}
}
