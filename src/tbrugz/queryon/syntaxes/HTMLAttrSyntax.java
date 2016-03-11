package tbrugz.queryon.syntaxes;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.datadump.HTMLDataDump;
import tbrugz.sqldump.util.SQLUtils;
import tbrugz.sqldump.util.Utils;

public class HTMLAttrSyntax extends HTMLDataDump {
	
	static final String[] SUFFIXES = {"_STYLE", "_CLASS", "_TITLE", "_HREF"}; //XXX: change to enum!
	
	//static final List<String> ATTRIB = Arrays.asList(new String[]{SUFFIXES[0].substring(1).toLowerCase(), SUFFIXES[1].substring(1).toLowerCase()});
	static final List<String> ATTRIBS = Arrays.asList("style", "class", "title");

	static final String[] ROWWIDE_COLS = {"ROW_STYLE", "ROW_CLASS"};
	transient final List<String> ROWWIDE_COLS_LIST = Arrays.asList(ROWWIDE_COLS);
	static final List<String> ROWWIDE_ATTRIBS = Arrays.asList("style", "class");
	
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
	
	final List<String> finalColNames = new ArrayList<String>();
	final List<String> rowSpecialAttr = new ArrayList<String>();
	final List<Integer> rowSpecialAttrIdx = new ArrayList<Integer>();

	protected boolean hrefDumpTargetBlank = true; //XXX: add prop for 'hrefDumpTargetBlank'
	
	public HTMLAttrSyntax(String padding, boolean innerTable) {
		super(padding, innerTable);
	}
	
	public HTMLAttrSyntax() {
		super();
		dumpColElement = true;
	}
	
	@Override
	public void initDump(String schema, String tableName, List<String> pkCols, ResultSetMetaData md) throws SQLException {
		super.initDump(schema, tableName, pkCols, md);
		finalColNames.clear();
		rowSpecialAttr.clear();
		rowSpecialAttrIdx.clear();
		
		for(int i=0;i<numCol;i++) {
			boolean isFullColumn = true;
			String colname = lsColNames.get(i);
			
			for(String suf: SUFFIXES) {
				if(colname.endsWith(suf)) {
					isFullColumn = false;
				}
			}
			if(ROWWIDE_COLS_LIST.contains(colname)) {
				isFullColumn = false;
				rowSpecialAttr.add(colname);
				rowSpecialAttrIdx.add(i);
			}
			
			if(isFullColumn) {
				finalColNames.add(colname);
			}
		}
	}
	
	//XXX
	@Override
	public void dumpHeader(Writer fos) throws IOException {
		tablePrepend(fos);
		//if(prepend!=null && (!innerTable || xpendInnerTable)) { out(prepend, fos); }
		StringBuilder sb = new StringBuilder();
		sb.append("<table class='"+tableName+"'>");
		if(dumpStyleNumericAlignRight) {
			appendStyleNumericAlignRight(sb);
		}
		if(dumpCaptionElement){
			sb.append("\n\t<caption>" + (schemaName!=null?schemaName+".":"") + tableName + "</caption>");
		}
		if(dumpColElement) {
			sb.append("\n<colgroup>");
			for(int i=0;i<lsColNames.size();i++) {
				if(finalColNames.contains(lsColNames.get(i))) {
					sb.append("\n\t<col colname=\""+lsColNames.get(i)+"\" type=\""+lsColTypes.get(i).getSimpleName()+"\"/>");
				}
			}
			sb.append("\n</colgroup>");
		}
		sb.append("\n\t<tr>");
		for(int i=0;i<lsColNames.size();i++) {
			if(finalColNames.contains(lsColNames.get(i))) {
				sb.append("<th>"+lsColNames.get(i)+"</th>");
			}
		}
		out(sb.toString()+"</tr>\n", fos);
	}
	
	protected void appendStyleNumericAlignRight(StringBuilder sb) {
		List<String> styleSelector = new ArrayList<String>();
		for(int i=0;i<lsColNames.size();i++) {
			int idx = finalColNames.indexOf(lsColNames.get(i));
			if(idx>=0 && (lsColTypes.get(i).equals(Integer.class) || lsColTypes.get(i).equals(Double.class)) ) {
				styleSelector.add("table."+tableName+" td:nth-child("+(idx+1)+")");
			}
		}
		if(styleSelector.size()>0) {
			sb.append("\n\t<style>\n\t\t").append(Utils.join(styleSelector, ", ")).append(" { text-align: right; }\n\t</style>");
		}
	}

	@Override
	public void dumpRow(ResultSet rs, long count, Writer fos) throws IOException, SQLException {
		StringBuilder sb = new StringBuilder();
		sb.append("\t"+"<tr");
		List<Object> vals = SQLUtils.getRowObjectListFromRS(rs, lsColTypes, numCol, true);
		for(int i=0;i<rowSpecialAttrIdx.size();i++) {
			int idx = rowSpecialAttrIdx.get(i);
			String colName = rowSpecialAttr.get(i);
			int rowIdx = ROWWIDE_COLS_LIST.indexOf(colName);
			String attrib = ROWWIDE_ATTRIBS.get(rowIdx);
			Object val = vals.get(idx);
			if(val!=null) {
				sb.append(" "+attrib+"=\""+val+"\"");
			}
		}
		sb.append(">");
		
		Map<String, Map<String,String>> attrsVals = new HashMap<String, Map<String,String>>();
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
				Object origVal = vals.get(i);
				
				if(ResultSet.class.isAssignableFrom(lsColTypes.get(i))) {
					if(origVal==null) {
						sb.append("<td></td>");
						continue;
					}
					ResultSet rsInt = (ResultSet) origVal;
					
					out(sb.toString()+"<td>\n", fos);
					sb = new StringBuilder();
					
					HTMLDataDump htmldd = new HTMLAttrSyntax(this.padding+"\t\t", true);
					//htmldd.padding = this.padding+"\t\t";
					//log.info(":: "+rsInt+" / "+lsColNames);
					htmldd.procProperties(prop);
					DataDumpUtils.dumpRS(htmldd, rsInt.getMetaData(), rsInt, null, lsColNames.get(i), fos, true);
					sb.append("\n\t</td>");
				}
				else {
				
				String value = DataDumpUtils.getFormattedXMLValue(origVal, lsColTypes.get(i), floatFormatter, dateFormatter, nullValueStr, escape);
				Map<String,String> attrs = attrsVals.get(colName);
				String attrsStr = "";
				if(attrs!=null) {
					for(String key: attrs.keySet()) {
						if(ATTRIBS.contains(key)) {
							String attrVal = attrs.get(key);
							if(attrVal!=null) {
								attrsStr += " "+key+"=\""+attrVal+"\"";
							}
						}
						value = decorateValue(attrs, key, String.valueOf(value));
					}
				}
				sb.append( "<td"
						+(origVal==null?" null=\"true\"":"")
						+attrsStr+">"+ value +"</td>");
				
				}
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
