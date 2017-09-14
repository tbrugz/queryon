package tbrugz.queryon.syntaxes;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.datadump.JSONDataDump;

public class ODataJsonSyntax extends JSONDataDump implements WebSyntax, Cloneable {

	static final String DATA_ELEMENT = "value";
	public static final String ODATA_ID = "odata";
	
	String baseHref;
	long limit, offset;
	String fullQueryName;
	boolean uniqueRow = false;

	@Override
	public String getSyntaxId() {
		return ODATA_ID;
	}
	
	/*@Override
	public void postProcProperties() {
		super.postProcProperties();
		addMetadata = false;
	}*/
	
	@Override
	public void initDump(String schema, String tableName, List<String> pkCols, ResultSetMetaData md)
			throws SQLException {
		super.initDump(schema, tableName, pkCols, md);
		fullQueryName = (schemaName!=null?schemaName+".":"") + tableName;
		dataElement = DATA_ELEMENT;
		beforeDumpHeader();
	}
	
	protected void beforeDumpHeader() {
		if(uniqueRow) {
			encloseRowWithCurlyBraquets = false;
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
	
	@Override
	public void setUniqueRow(boolean unique) {
		this.uniqueRow = unique;
		beforeDumpHeader();
	}
	
	@Override
	public void dumpHeader(Writer fos) throws IOException {
		out("{", fos);
		dumpXtraHeader(fos);
		if(!uniqueRow) {
			outNoPadding("\n"+padding+"\t\""+dataElement+"\": "
					+"["
					+"\n", fos);
		}
		else {
			outNoPadding("\n", fos);
			// should work most times, but not if $select=column_a ...
			/*
			if(lsColNames.size()==1) {
				lsColNames.set(0, "value");
			}
			*/
		}
	}
	
	@Override
	public void dumpFooter(long count, boolean hasMoreRows, Writer fos) throws IOException {
		if(!uniqueRow) {
			out("\t"+"]",fos);
		}
		else if(count==0) {
			out("\t\"@null\": true", fos);
			//XXX: throw new NotFoundException("Not Found");
		}
		out("\n"+padding+"}", fos);
	}
	
	@Override
	protected void dumpXtraHeader(Writer w) throws IOException {
		String context = baseHref+"$metadata#"+fullQueryName;
		String urlNext = DataDumpUtils.xmlEscapeText(baseHref + fullQueryName + ("?$skip="+(offset+limit)));
		
		out("\n"+padding+"\t\"@odata.context\": \""+context+"\",", w);
		if(!uniqueRow && limit>0) {
			out("\n"+padding+"\t\"@odata.nextLink\": \""+urlNext+"\",", w);
		}
	}
	
}
