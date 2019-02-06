package tbrugz.queryon.soap;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
	public static final String TAG_FILTERS = "filters";
	public static final String TAG_VALUE = "value";
	public static final String ATTR_DIRECTION = "direction";
	
	public static final String[] KNOWN_TAGS = { TAG_FILTERS, PARAM_FIELDS, PARAM_LIMIT, PARAM_OFFSET, PARAM_DISTINCT, PARAM_ORDER };
	static final List<String> knownTags = Arrays.asList(KNOWN_TAGS);
	
	static final Pattern soapPositionalParamTagPattern = Pattern.compile("parameter([1-9]+[0-9]*)", Pattern.DOTALL);
	
	Map<String, String> xtraParametersMap;
	
	//Element requestEl;
	//String nsPrefix;
	
	static {
		Collections.sort(knownTags);
	}

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
	
	@Override
	protected void processFilters(Set<String> allowedFilters) throws UnsupportedEncodingException {
		Element oFilters = XmlUtils.getUniqueChild(getRequestElement(), TAG_FILTERS);
		if(oFilters==null) { return; }
		//log.info("processFilters: "+oFilters);
		NodeList nl = oFilters.getChildNodes();
		for(int i=0;i<nl.getLength();i++) {
			Node n = nl.item(i);
			if(n.getNodeType() != Node.ELEMENT_NODE) { continue; }
			Element el = (Element) n;
			String tag = el.getTagName();
			String field = XmlUtils.getUniqueTagValueOfChildren(el, TAG_FIELD);
			//TODO: check allowedFilters
			log.info("processFilters: tag=="+tag+" / field=="+field);
			
			Map<String, String> uniFilter = getUniFilter(tag);
			if(uniFilter!=null) {
				String value = XmlUtils.getUniqueTagValueOfChildren(el, TAG_VALUE);
				uniFilter.put(field, value);
				log.info("uniFilter: value = "+value);
				continue;
			}
			Map<String, String[]> multiFilter = getMultiFilter(tag);
			if(multiFilter!=null) {
				List<String> values = XmlUtils.getTagValuesOfDescendants(el, TAG_VALUE);
				multiFilter.put(field, values.toArray(new String[]{}));
				log.info("multiFilter: values = "+values);
				continue;
			}
			Set<String> setFilter = getSetFilter(tag);
			if(setFilter!=null) {
				setFilter.add(field);
				log.info("setFilter: field = "+field);
				continue;
			}
			
			//log.warn("unknown filter: "+tag);
			throw new BadRequestException("unknown filter: "+tag);
		}
	}
	
	@Override
	protected void processParams(List<String> parts) {
		xtraParametersMap = new HashMap<>();
		Map<String, String> positionalValues = new TreeMap<>();
		NodeList nl = getRequestElement().getChildNodes();
		for(int i=0;i<nl.getLength();i++) {
			Node n = nl.item(i);
			if(n.getNodeType() != Node.ELEMENT_NODE) { continue; }
			Element el = (Element) n;
			String tagName = el.getTagName();
			//log.info("processParams: found parameter '"+tagName+"'");
			if(Collections.binarySearch(knownTags, tagName)<0) {
				String tagValue = el.getTextContent();
				log.info("processParams: will add '"+tagName+"' - value '"+tagValue+"'");
				xtraParametersMap.put(tagName,tagValue);
				if( //(action.atype==ActionType.SELECT || action.atype==ActionType.EXECUTE) &&
					soapPositionalParamTagPattern.matcher(tagName).matches()) {
					log.info("processParams: will add positional param '"+tagName+"' - value '"+tagValue+"'");
					positionalValues.put(tagName, tagValue);
				}
			}
		}
		for(Map.Entry<String, String> e: positionalValues.entrySet()) {
			params.add(e.getValue());
		}
	}
	
	@Override
	public Map<String, String> getParameterMapUniqueValues() {
		return xtraParametersMap;
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
	
	Map<String, String> getUniFilter(String filter) {
		switch (filter) {
		case "filterEquals":
			return filterEquals;
		case "filterNotEquals":
			return filterNotEquals;
		case "filterGreaterThan":
			return filterGreaterThan;
		case "filterGreaterOrEqual":
			return filterGreaterOrEqual;
		case "filterLessThan":
			return filterLessThan;
		case "filterLessOrEqual":
			return filterLessOrEqual;
		default:
			return null;
		}
	}

	Map<String, String[]> getMultiFilter(String filter) {
		switch (filter) {
		case "filterIn":
			return filterIn;
		case "filterNotIn":
			return filterNotIn;
		case "filterLike":
			return filterLike;
		case "filterNotLike":
			return filterNotLike;
		default:
			return null;
		}
	}

	Set<String> getSetFilter(String filter) {
		switch (filter) {
		case "filterNull":
			return filterNull;
		case "filterNotNull":
			return filterNotNull;
		default:
			return null;
		}
	}

}
