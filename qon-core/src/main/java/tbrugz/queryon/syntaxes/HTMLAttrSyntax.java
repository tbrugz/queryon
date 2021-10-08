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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.datadump.DumpSyntaxBuilder;
import tbrugz.sqldump.datadump.HTMLDataDump;
import tbrugz.sqldump.datadump.HierarchicalDumpSyntax;
import tbrugz.sqldump.util.SQLUtils;

/*
 * XXX: pivot: know limitation: attributes doesn't work with measures in rows
 */
public class HTMLAttrSyntax extends HTMLDataDump implements DumpSyntaxBuilder, Cloneable, HierarchicalDumpSyntax {

	static final Log log = LogFactory.getLog(HTMLAttrSyntax.class);
	
	static final String[] SUFFIXES = {"_STYLE", "_CLASS", "_TITLE", "_HREF"}; //XXX: change to enum!
	
	//static final List<String> ATTRIB = Arrays.asList(new String[]{SUFFIXES[0].substring(1).toLowerCase(), SUFFIXES[1].substring(1).toLowerCase()});
	static final List<String> ATTRIBS = Arrays.asList("style", "class", "title");

	static final String[] ROWWIDE_COLS = {"ROW_STYLE", "ROW_CLASS"};
	transient static final List<String> ROWWIDE_COLS_LIST = Arrays.asList(ROWWIDE_COLS);
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

	//final List<String> finalColNames = new ArrayList<String>();
	final List<String> rowSpecialAttr = new ArrayList<String>();
	final List<Integer> rowSpecialAttrIdx = new ArrayList<Integer>();

	protected boolean hrefDumpTargetBlank = true; //XXX: add prop for 'hrefDumpTargetBlank'
	
	protected List<String> lsColDbTypes = new ArrayList<String>();
	
	//protected int onColsColCount = 0;
	//protected int onRowsColCount = 0;
	
	/*public HTMLAttrSyntax(String padding, boolean innerTable) {
		super(padding, innerTable);
	}*/
	
	public HTMLAttrSyntax() {
		super();
		dumpColElement = true;
	}
	
	@Override
	public void initDump(String schema, String tableName, List<String> pkCols, ResultSetMetaData md) throws SQLException {
		super.initDump(schema, tableName, pkCols, md);
		//finalColNames.clear();
		//finalColTypes.clear();
		lsColDbTypes.clear();
		rowSpecialAttr.clear();
		rowSpecialAttrIdx.clear();

		List<Integer> finalColsToRemove = new ArrayList<Integer>();
		for(int i=0;i<numCol;i++) {
			boolean isFullColumn = true;
			String colname = lsColNames.get(i);
			
			for(String suf: SUFFIXES) {
				if(colname.endsWith(suf)) {
					isFullColumn = false;
				}
			}
			//if(isPivotResultSet()) {
			for(String suf: SUFFIXES) {
				if(colname.contains(suf+colSep)) {
					isFullColumn = false;
				}
			}
			//}
			if(ROWWIDE_COLS_LIST.contains(colname)) {
				isFullColumn = false;
				rowSpecialAttr.add(colname);
				rowSpecialAttrIdx.add(i);
			}
			
			if(isFullColumn) {
				//finalColNames.add(colname);
				//finalColTypes.add(lsColTypes.get(i));
				lsColDbTypes.add(md.getColumnTypeName(i+1));
			}
			else { //if(!isFullColumn) {
				finalColsToRemove.add(i);
			}

		}

		for(int i=finalColsToRemove.size()-1;i>=0;i--) {
			finalColNames.remove((int)finalColsToRemove.get(i));
			finalColTypes.remove((int)finalColsToRemove.get(i));
		}

		if(finalColNames.size() != finalColTypes.size() || finalColNames.size() != lsColDbTypes.size()) {
			log.warn("finalColName [#"+finalColNames.size()+";"+finalColNames+"],"+
				" finalColTypes [#"+finalColTypes.size()+";"+finalColTypes+"]"+
				" & lsColDbTypes [#"+lsColDbTypes.size()+";"+lsColDbTypes+"] should have the same size");
		}
		
		//dumpColType = true;
		dumpIsNumeric = true;

		/*for(int i=0;i<numCol;i++) {
			lsColDbTypes.add(md.getColumnTypeName(i+1));
		}*/
	}
	
	@Override
	public void dumpHeader(Writer fos) throws IOException {
		tablePrepend(fos);
		//if(prepend!=null && (!innerTable || xpendInnerTable)) { out(prepend, fos); }
		StringBuilder sb = new StringBuilder();
		sb.append("<table class='"+getTableStyleClass()+"'>");
		if(dumpStyleNumericAlignRight) {
			appendStyleNumericAlignRight(sb);
		}
		if(dumpCaptionElement){
			sb.append(nl()+"\t<caption>" + (schemaName!=null?schemaName+".":"") + tableName + "</caption>");
		}
		if(dumpColElement) {
			sb.append(nl()+"\t<colgroup>");
			for(int i=0;i<finalColNames.size();i++) {
				//if(finalColNames.contains(lsColNames.get(i))) {
					sb.append(nl()+"\t\t<col colname=\""+finalColNames.get(i)+"\" type=\""+finalColTypes.get(i).getSimpleName()+"\" dbtype=\""+lsColDbTypes.get(i)+"\"/>");
				//}
			}
			sb.append(nl()+"\t</colgroup>");
		}
		sb.append("\n");
		
		/*
		//System.out.println("onRowsColCount="+onRowsColCount+" ; onColsColCount="+onColsColCount+" ; finalColNames="+finalColNames);
		boolean dumpedAsLeast1row = false;
		if(isPivotResultSet()) {
			guessPivotCols();
			for(int cc=0;cc<onColsColCount;cc++) {
				sb.append("\n\t<tr>");
				for(int i=0;i<finalColNames.size();i++) {
					//if(finalColNames.contains(lsColNames.get(i))) {
						String[] parts = finalColNames.get(i).split(PivotResultSet.COLS_SEP_PATTERN);
						
						if(parts.length>cc) {
							//split...
							String[] p2 = parts[cc].split(PivotResultSet.COLVAL_SEP_PATTERN);
							if(p2.length>1) {
								sb.append("<th>"+p2[1]+"</th>");
							}
							else {
								if(i<onRowsColCount) {
									sb.append("<th class='blank'"+
											(i<onRowsColCount?" dimoncol=\"true\"":"")+
											"/>");
								}
								else {
									sb.append("<th measure=\"true\">"+parts[cc]+"</th>");
								}
							}
						}
						else if(cc+1==onColsColCount) {
							if(i<onRowsColCount) {
								sb.append("<th dimoncol=\"true\" measure=\"true\">"+finalColNames.get(i)+"</th>");
							}
							else {
								sb.append("<th>"+finalColNames.get(i)+"</th>");
							}
						}
						else {
							sb.append("<th class='blank'"+
									(i<onRowsColCount?" dimoncol=\"true\"":"")+
									"/>");
						}
					//}
				}
				sb.append("</tr>");
				dumpedAsLeast1row = true;
			}
		}
		if(!dumpedAsLeast1row) {
			sb.append("\n\t<tr>");
			for(int i=0;i<finalColNames.size();i++) {
				//if(finalColNames.contains(lsColNames.get(i))) {
					sb.append("<th>"+finalColNames.get(i)+"</th>");
				//}
			}
			sb.append("</tr>");
		}
		sb.append("\n");*/
		addTableHeaderRows(sb);
		
		out(sb.toString(), fos);
	}
	
	/*
	void guessPivotCols() {
		onColsColCount = 0;
		onRowsColCount = 0;
		for(int i=0;i<lsColNames.size();i++) {
			if(finalColNames.contains(lsColNames.get(i))) {
				int l = lsColNames.get(i).split(PivotResultSet.COLS_SEP_PATTERN).length;
				if(l>1) {
					if(l>onColsColCount) {
						onColsColCount = l;
						onRowsColCount = i;
						break;
					}
				}
			}
		}
		
		if(onColsColCount==0 && onRowsColCount==0) {
			for(int i=0;i<lsColNames.size();i++) {
				if(finalColNames.contains(lsColNames.get(i))) {
					int l2 = lsColNames.get(i).split(PivotResultSet.COLVAL_SEP_PATTERN).length;
					if(l2>1) {
						onColsColCount = 1;
						onRowsColCount = i;
						break;
					}
				}
			}
		}
	}
	
	protected void appendStyleNumericAlignRight(StringBuilder sb) {
		List<String> styleSelector = new ArrayList<String>();
		for(int i=0;i<finalColNames.size();i++) {
			//int idx = finalColNames.indexOf(lsColNames.get(i));
			if( finalColTypes.get(i).equals(Integer.class) || finalColTypes.get(i).equals(Double.class) ) {
				styleSelector.add("table."+tableName+" td:nth-child("+(i+1)+")");
			}
		}
		if(styleSelector.size()>0) {
			sb.append("\n\t<style>\n\t\t").append(Utils.join(styleSelector, ", ")).append(" { text-align: right; }\n\t</style>");
		}
	}
	*/

	@Override
	public void dumpRow(ResultSet rs, long count, Writer fos) throws IOException, SQLException {
		StringBuilder sb = new StringBuilder();
		List<Object> vals = SQLUtils.getRowObjectListFromRS(rs, lsColTypes, numCol, true);
		appendBreaksIfNeeded(vals, null, sb);
		sb.append("\t"+"<tr");
		for(int i=0;i<rowSpecialAttrIdx.size();i++) {
			int idx = rowSpecialAttrIdx.get(i);
			String colName = rowSpecialAttr.get(i);
			int rowIdx = ROWWIDE_COLS_LIST.indexOf(colName);
			String attrib = ROWWIDE_ATTRIBS.get(rowIdx);
			Object val = vals.get(idx);
			if(val!=null) {
				sb.append(" "+attrib+"=\""+DataDumpUtils.xmlEscapeTextFull(val.toString())+"\"");
			}
		}
		sb.append(">");
		
		Map<String, Map<String,String>> attrsVals = new HashMap<String, Map<String,String>>();
		for(int i=0;i<lsColNames.size();i++) {
			String colName = lsColNames.get(i);
			if(!finalColNames.contains(colName)) {
				for(String suffix: SUFFIXES) {
					//if(colName.endsWith(suffix)) {
					if(colName.endsWith(suffix) || colName.contains(suffix+colSep)) {
						String fullCol = colName.replace(suffix, "");
						//String fullCol = colName.substring(0, colName.length()-suffix.length());
						//String attr = colName.substring(colName.length()-suffix.length()+1).toLowerCase();
						String attr = suffix.substring(1).toLowerCase();
						Map<String,String> attrs = attrsVals.get(fullCol);
						if(attrs==null) {
							attrs = new HashMap<String, String>();
							attrsVals.put(fullCol, attrs);
						}
						Object val = vals.get(i);
						if(val!=null) {
							String v = DataDumpUtils.getFormattedXMLValue(val, lsColTypes.get(i), floatFormatter, dateFormatter, null, false);
							attrs.put(attr, DataDumpUtils.xmlEscapeTextFull(v));
						}
						//System.out.println("fullCol="+fullCol+" ; attrs="+attrs);
					}
				}
			}
		}
		
		for(int i=0;i<lsColNames.size();i++) {
			String colName = lsColNames.get(i);
			if(finalColNames.contains(colName)) {
				Object origVal = vals.get(i);
				Class<?> ctype = lsColTypes.get(i);
				boolean isResultSet = DataDumpUtils.isResultSet(ctype, origVal);
				boolean isArray = DataDumpUtils.isArray(ctype, origVal);
				if(isResultSet || isArray) {
					ResultSet rsInt = null;
					if(isArray) {
						rsInt = DataDumpUtils.getResultSetFromArray(origVal, false, colName);
					}
					else {
						rsInt = (ResultSet) origVal;
					}
					if(origVal==null) {
						sb.append("<td></td>");
						continue;
					}
					
					out(sb.toString()+"<td>\n", fos);
					sb = new StringBuilder();
					
					HTMLAttrSyntax htmldd = innerClone();
					//htmldd.procProperties(prop);
					DataDumpUtils.dumpRS(htmldd, rsInt.getMetaData(), rsInt, null, colName, fos, true);
					sb.append("\n\t</td>");
				}
				else {
				
				String value = DataDumpUtils.getFormattedXMLValue(origVal, ctype, floatFormatter, dateFormatter, nullValueStr, doEscape(i));
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
						+attrsStr
						+(i<getOnRowsColCount()?" dimoncol=\"true\"":"")
						//+(dumpColType?" coltype=\""+ctype.getSimpleName()+"\"":"")
						+((dumpIsNumeric && DataDumpUtils.isNumericType(ctype))?" numeric=\"true\"":"")
						+">"+ value +"</td>");
				
				//System.out.println("[i="+i+"] origVal=["+origVal+"] colName=["+colName+"] attr=["+attrs+"]");
				}
			}
		}
		sb.append("</tr>");
		out(sb.toString()+"\n", fos);
	}
	
	@Override
	protected void appendBreakRow(List<Object> breakRowValues, String clazz, StringBuilder sb) {
		sb.append("\t"+"<tr>");
		sb.append("<th class=\"break\" colspan=\""+finalColNames.size()+"\">"+getBreakValuesRow(breakRowValues)+"</th>");
		sb.append("</tr>\n");
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

	/*
	@Override
	public void updateProperties(DumpSyntax ds) {
		if(! (ds instanceof HTMLAttrSyntax)) {
			throw new RuntimeException(ds.getClass()+" must be instance of "+this.getClass());
		}
		HTMLAttrSyntax dd = (HTMLAttrSyntax) ds;
		super.updateProperties(dd);
	}
	
	@Override
	public HTMLAttrSyntax clone() {
		HTMLAttrSyntax dd = new HTMLAttrSyntax();
		updateProperties(dd);
		return dd;
	}
	*/
	
	@Override
	public HTMLAttrSyntax innerClone() {
		try {
			HTMLAttrSyntax dd = (HTMLAttrSyntax) clone();
			dd.padding += "\t\t";
			dd.innerTable = true;
			return dd;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

}
