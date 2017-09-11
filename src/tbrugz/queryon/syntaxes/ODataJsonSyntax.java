package tbrugz.queryon.syntaxes;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

import tbrugz.sqldump.datadump.DataDumpUtils;
import tbrugz.sqldump.datadump.JSONDataDump;

public class ODataJsonSyntax extends JSONDataDump implements WebSyntax, Cloneable {

	String baseHref;
	long limit, offset;
	String fullQueryName;

	public static String oDataId() {
		return "odata";
	}
	
	@Override
	public String getSyntaxId() {
		return oDataId();
	}
	
	@Override
	public void postProcProperties() {
		super.postProcProperties();
		addMetadata = false;
	}
	
	@Override
	public void initDump(String schema, String tableName, List<String> pkCols, ResultSetMetaData md)
			throws SQLException {
		super.initDump(schema, tableName, pkCols, md);
		fullQueryName = (schemaName!=null?schemaName+".":"") + tableName;
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
	protected void dumpXtraHeader(Writer w) throws IOException {
		String context = baseHref+"$metadata#"+fullQueryName;
		String urlNext = DataDumpUtils.xmlEscapeText(baseHref + ("?offset="+(offset+limit)));
		
		out("\n"+padding+"\t\"@odata.context\": \""+context+"\",", w);
		if(limit>0) {
			out("\n"+padding+"\t\"@odata.nextLink\": \""+urlNext+"\",", w);
		}
	}
	
}
