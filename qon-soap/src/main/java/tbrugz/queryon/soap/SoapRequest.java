package tbrugz.queryon.soap;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.sqldump.util.Utils;

public class SoapRequest extends RequestSpec {
	
	static final Log log = LogFactory.getLog(SoapRequest.class);
	
	public static final String ATTR_REQUEST_ELEMENT = "request_element";
	public static final String ATTR_NS_PREFIX = "ns_prefix";
	
	public static final String TAG_FIELD = "field";
	public static final String ATTR_DIRECTION = "direction";
	
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
	
	@Override
	protected int getFinalOffset(HttpServletRequest req) {
		String offsetStr = XmlUtils.getUniqueTagValueOfChildren(getRequestElement(), PARAM_OFFSET);
		return offsetStr!=null ? Integer.parseInt(offsetStr) : 0;
	}
	
	@Override
	protected boolean isDistinct(HttpServletRequest req, boolean allowDistinct) {
		String str = XmlUtils.getUniqueTagValueOfChildren(getRequestElement(), PARAM_DISTINCT);
		return getBoolValue(str);
	}
	
	@Override
	protected SoapDumpSyntax getOutputSyntax(HttpServletRequest req, DumpSyntaxUtils dsutils, boolean allowGetDumpSyntaxByAccept, String defaultOutputSyntax) {
		SoapDumpSyntax dumpSyntax = new SoapDumpSyntax();
		return dumpSyntax;
	}
	
	/*@Override
	protected DumpSyntaxInt getOutputSyntax(HttpServletRequest req, DumpSyntaxUtils dsutils, boolean allowGetDumpSyntaxByAccept, String defaultOutputSyntax) {
		return null;
	}*/
	
	@Override
	protected String getFields(HttpServletRequest req) {
		Element eFields = XmlUtils.getUniqueChild(getRequestElement(), PARAM_FIELDS);
		if(eFields==null) { return null; }
		List<String> els = XmlUtils.getTagValuesOfDescendants(eFields, TAG_FIELD);
		//log.info("fields[]: "+els);
		if(els.size()==0) { return null; }
		return Utils.join(els, ",");
	}
	
	@Override
	protected void processOrder(HttpServletRequest req) {
		Element oFields = XmlUtils.getUniqueChild(getRequestElement(), PARAM_ORDER);
		if(oFields==null) { return; }
		NodeList nl = oFields.getElementsByTagName(TAG_FIELD);

		if(nl.getLength()>0) {
			for(int i=0;i<nl.getLength();i++) {
				Element el = (Element) nl.item(i);
				orderCols.add(el.getTextContent());
				String dir = el.getAttribute(ATTR_DIRECTION);
				if(dir!=null && ORDER_DESC.equalsIgnoreCase(dir)) {
					orderAscDesc.add(ORDER_DESC);
				}
				else {
					orderAscDesc.add(ORDER_ASC);
				}
			}
		}
		//log.info("orderCols: "+orderCols);
		//log.info("orderAscDesc: "+orderAscDesc);
	}
	
	//-----
	
	public static boolean getBoolValue(String s) {
		return s!=null && (s.equals("true") || s.equals("1"));
	}
	
	public String toStringDebug() {
		return super.toString()+"[limit="+limit+";offset="+offset+";distinct="+distinct+";keyValues="+keyValues+";params="+params+"]";
	}
	
	public static void setAttributesOnRequest(HttpServletRequest req, Element requestEl, String nsPrefix) {
		req.setAttribute(ATTR_REQUEST_ELEMENT, requestEl);
		req.setAttribute(ATTR_NS_PREFIX, nsPrefix);
	}

}
