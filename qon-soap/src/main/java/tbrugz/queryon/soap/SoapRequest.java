package tbrugz.queryon.soap;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.util.DumpSyntaxUtils;

public class SoapRequest extends RequestSpec {
	
	static final Log log = LogFactory.getLog(SoapRequest.class);
	
	public static final String ATTR_REQUEST_ELEMENT = "request_element";
	public static final String ATTR_NS_PREFIX = "ns_prefix";
	
	//Element requestEl;
	//String nsPrefix;

	public SoapRequest(/*Element requestEl, String nsPrefix,*/ DumpSyntaxUtils dsutils, HttpServletRequest req, Properties prop) throws ServletException, IOException {
		super(dsutils, req, prop, 0, null /* soap/xml */ /* SoapDumpSyntax.SOAP_ID / XMLDataDump.XML_SYNTAX_ID */, true, 0, null);
		//this.requestEl = req""uestEl;
		//this.nsPrefix = nsPrefix; 
	}
	
	Element getRequestElement() {
		return (Element) getAttribute(ATTR_REQUEST_ELEMENT);
	}

	String getNsPrefix() {
		return (String) getAttribute(ATTR_NS_PREFIX);
	}
	
	//----- Overrides...
	
	@Override
	protected String getMethod(HttpServletRequest req) {
		return QueryOn.METHOD_GET; //XXX: add methods: POST (insert), PATCH (update), DELETE (delete), POST (execute)
	}
	
	@Override
	protected String getObject(List<String> parts, int prefixesToIgnore) {
		String tagName = getRequestElement().getTagName();
		String nsPrefix = getNsPrefix();
		if(nsPrefix==null) {
			throw new BadRequestException("null prefix");
		}
		if(tagName!=null && tagName.startsWith(nsPrefix)) {
			tagName = tagName.substring(nsPrefix.length()+1);
		}
		if(tagName.endsWith(QonSoapServlet.SUFFIX_REQUEST_ELEMENT)) {
			tagName = tagName.substring(0, tagName.length()-QonSoapServlet.SUFFIX_REQUEST_ELEMENT.length());
		}
		log.info("tagName: "+getRequestElement().getTagName()+" -> "+tagName);
		return tagName;
	}
	
	@Override
	protected Integer getFinalLimit(HttpServletRequest req) {
		String limitStr = XmlUtils.getUniqueTagValueOfChildren(getRequestElement(), PARAM_LIMIT);
		return limitStr!=null ? Integer.parseInt(limitStr) : null;
	}
	
	//XXX getOutputSyntax ...
	@Override
	protected SoapDumpSyntax getOutputSyntax(HttpServletRequest req, DumpSyntaxUtils dsutils, boolean allowGetDumpSyntaxByAccept, String defaultOutputSyntax) {
		SoapDumpSyntax dumpSyntax = new SoapDumpSyntax();
		return dumpSyntax;
	}
	
	/*@Override
	protected DumpSyntaxInt getOutputSyntax(HttpServletRequest req, DumpSyntaxUtils dsutils, boolean allowGetDumpSyntaxByAccept, String defaultOutputSyntax) {
		return null;
	}*/
	
	//-----
	
	public String toStringDebug() {
		return super.toString()+"[limit="+limit+";keyValues="+keyValues+";params="+params+"]";
	}
	
	public static void setAttributesOnRequest(HttpServletRequest req, Element requestEl, String nsPrefix) {
		req.setAttribute(ATTR_REQUEST_ELEMENT, requestEl);
		req.setAttribute(ATTR_NS_PREFIX, nsPrefix);
	}

}
