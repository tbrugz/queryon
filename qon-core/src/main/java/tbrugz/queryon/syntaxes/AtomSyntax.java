package tbrugz.queryon.syntaxes;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.QueryOn;

import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.datadump.XMLDataDump;
import tbrugz.sqldump.util.SQLUtils;

/**
 * Atom Feed Syntax.
 * 
 * Required columns: TITLE, ID, SUMMARY|CONTENT, AUTHOR_NAME, UPDATED
 * 
 * <!-- Optional columns: AUTHOR_EMAIL, PUBLISHED -->
 *  
 * see: https://en.wikipedia.org/wiki/Atom_(standard)
 *      https://tools.ietf.org/html/rfc4287
 *      https://validator.w3.org/feed/docs/atom.html
 *      https://tools.ietf.org/html/rfc5005#section-3 - Feed Paging and Archiving
 */
public class AtomSyntax extends XMLDataDump implements WebSyntax {

	static final Log log = LogFactory.getLog(AtomSyntax.class);
	
	static final String ATOM_PREFIX = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<feed xmlns=\"http://www.w3.org/2005/Atom\">";
	static final String ATOM_SUFFIX = "</feed>";
	
	static final String ATOM_GENERATOR = "QueryOn: Atom Syntax - "+QueryOn.QON_PROJECT_URL;
	
	final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
	
	long limit, offset;
	String baseHref = null;
	String fullQueryName;
	
	/*public AtomSyntax() {
		super();
	}*/
	
	@Override
	public void procProperties(Properties prop) {
		dateFormatter = df;
		super.procProperties(prop);
	}
	
	@Override
	public void initDump(String schema, String tableName, List<String> pkCols, ResultSetMetaData md)
			throws SQLException {
		super.initDump(schema, tableName, pkCols, md);
		rowElement = "entry";
		dumpRowElement = false;
		fullQueryName = (schemaName!=null?schemaName+".":"") + tableName;
		
		// column names to lower
		for(int i=0;i<lsColNames.size();i++) {
			lsColNames.set(i, lsColNames.get(i).toLowerCase());
		}
		
		List<String> warnings = validate();
		for(String s: warnings) {
			log.warn(s);
		}
	}
	
	@Override
	public void setBaseHref(String href) {
		this.baseHref = href;
	}
	
	@Override
	public void setLimit(long limit) {
		this.limit = limit;
	}
	
	@Override
	public void setOffset(long offset) {
		this.offset = offset;
	}
	
	// setFilters...
	
	@Override
	public void dumpHeader(Writer w) throws IOException {
		out(ATOM_PREFIX, w);
		out("\n\t<title>"+fullQueryName+"</title>\n", w);
		//out("\t<subtitle></subtitle>\n", fos);
		out("\t<link href=\""+baseHref+"\"/>\n", w);
		String urlSelf = DataDumpUtils.xmlEscapeText(baseHref + fullQueryName + ".atom" + (offset>0?"?offset="+offset:""));
		String urlNext = DataDumpUtils.xmlEscapeText(baseHref + fullQueryName + ".atom" + ("?offset="+(offset+limit)));
		out("\t<link rel=\"self\" href=\"" + urlSelf + "\"/>\n", w);
		//XXX add "previous"?
		out("\t<link rel=\"next\" href=\"" + urlNext + "\"/>\n", w); //XXX: only if rowcount == limit? add at footer?
		out("\t<generator>"+ATOM_GENERATOR+"</generator>\n", w);
		out("\t<id>"+getUrnId(null)+"</id>\n", w);
		Date updated = new Date(); //XXX: how to know? first row??
		out("\t<updated>"+df.format(updated)+"</updated>\n", w);
		//out("", fos);
	}
	
	@Override
	public void dumpRow(ResultSet rs, long count, Writer w) throws IOException, SQLException {
		out("\t<"+rowElement+">\n",w);
		
		//super.dumpRow(rs, count, w);
		StringBuilder sb = new StringBuilder();
		List<Object> vals = SQLUtils.getRowObjectListFromRS(rs, lsColTypes, numCol, true);
		
		String[] allowedCols = {"title", "id", "summary", "author_name", "content", "updated"};
		for(int i=0 ; i<allowedCols.length ; i++) {
			int coli = lsColNames.indexOf(allowedCols[i]);
			if(coli<0) { continue; }
			String colName = lsColNames.get(coli);
			String value = DataDumpUtils.getFormattedXMLValue(vals.get(coli), lsColTypes.get(coli), floatFormatter, dateFormatter, doEscape(coli));
			if(value!=null) {
				String startTag = "<"+colName+">";
				String endTag = "</"+colName+">";
				if("id".equals(colName)) { value = getUrnId(value); }
				if("author_name".equals(colName)) {
					startTag = "<author><name>";
					endTag = "</name></author>";
				}
				
				sb.append( "\t\t" + startTag + value + endTag + "\n" );
			}
		}
		//XXX: add category? <category term="technology"/>
		//XXX: add published? <published>2003-12-13T09:17:51-08:00</published>
		
		String url = DataDumpUtils.xmlEscapeText( baseHref + "?limit=1&offset="+ (offset+count) );
		String urlHtml = DataDumpUtils.xmlEscapeText( baseHref + ".html" + "?limit=1&offset="+ (offset+count) );
		sb.append("\t\t<link href=\"" + url + "\"/>\n");
		sb.append("\t\t<link href=\"" + urlHtml + "\" rel=\"alternate\" type=\"text/html\"/>");

		//XXX: add xtra columns? another namespace?
		
		dumpAndClearBuffer(sb, w);

		out("\t</"+rowElement+">\n",w);
	}

	@Override
	public void dumpFooter(long count, boolean hasMoreRows, Writer w) throws IOException {
		out(ATOM_SUFFIX, w);
	}
	
	@Override
	public String getSyntaxId() {
		return "atom";
	}
	
	@Override
	public String getMimeType() {
		return "application/atom+xml";
	}
	
	String getUrnId(String id) {
		return "urn:queryon:"+fullQueryName+(id==null?"":":"+id);
	}
	
	List<String> validate() {
		List<String> warnings = new ArrayList<String>();
		if(!lsColNames.contains("title")) {
			warnings.add("column 'title' should be present");
		}
		if(!lsColNames.contains("id")) {
			warnings.add("column 'id' should be present");
		}
		if(!lsColNames.contains("author_name")) {
			warnings.add("column 'author_name' should be present");
		}
		if(!lsColNames.contains("summary") && !lsColNames.contains("content")) {
			warnings.add("column 'summary' or 'content' should be present");
		}
		if(!lsColNames.contains("updated")) {
			warnings.add("column 'updated' should be present");
		}
		return warnings;
	}

}
