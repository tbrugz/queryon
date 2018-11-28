package tbrugz.queryon.soap;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

import tbrugz.sqldump.datadump.XMLDataDump;

public class SoapDumpSyntax extends XMLDataDump /* implements WebSyntax */ {

	public static final String SOAP_ID = "soap";
	public static final String SOAP_MIMETYPE = "text/xml"; //"application/soap+xml";

	public static final String DEFAULT_SOAPENV_PREFIX = "soapenv";
	
	String prefix;
	String fullName;
	
	public SoapDumpSyntax() {
		this.prefix = "ns1";
	}
	
	@Override
	public String getMimeType() {
		return SOAP_MIMETYPE;
	}
	
	@Override
	public String getDefaultFileExtension() {
		return "soap.xml";
	}
	
	@Override
	public String getSyntaxId() {
		return SOAP_ID;
	}
	
	public static String getSoapHeader(String prefix) {
		return
			"<?xml version = \"1.0\"?>"+
			"<"+prefix+":Envelope xmlns:"+prefix+"=\""+QonSoapServlet.NS_SOAP_ENVELOPE+"\">"+
			"<"+prefix+":Body>"+"\n";
	}
	
	public static String getSoapFooter(String prefix) {
		return
			"</"+prefix+":Body>"+"\n"+
			"</"+prefix+":Envelope>";
	}

	String getNamespace() {
		//XXX: set serviceName
		return QonSoapServlet.NS_QON_PREFIX;
	}
	
	@Override
	public void initDump(String schema, String tableName, List<String> pkCols, ResultSetMetaData md) throws SQLException {
		super.initDump(schema, tableName, pkCols, md);
		fullName = (schemaName!=null?schemaName+".":"") + tableName;
		rowElement = schemaName+"."+tableName;
	}
	
	@Override
	public void dumpHeader(Writer fos) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(getSoapHeader(DEFAULT_SOAPENV_PREFIX));
		sb.append("<"+prefix+":"+"listOf"+fullName+" xmlns:"+prefix+"=\""+getNamespace()+"\">\n");
		out(sb.toString(), fos);
	}
	
	@Override
	public void dumpFooter(long count, boolean hasMoreRows, Writer fos) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("</"+prefix+":"+"listOf"+fullName+">\n");
		sb.append(getSoapFooter(DEFAULT_SOAPENV_PREFIX));
		out(sb.toString(), fos);
	}

}
