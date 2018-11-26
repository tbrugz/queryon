package tbrugz.queryon.soap;

import tbrugz.sqldump.datadump.XMLDataDump;

public class SoapDumpSyntax extends XMLDataDump /* implements WebSyntax */ {

	public static final String SOAP_ID = "soap";
	public static final String SOAP_MIMETYPE = "application/soap+xml";
	
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

}
