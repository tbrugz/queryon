package tbrugz.queryon.soap;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.exception.InternalServerException;

public class XmlUtils {

	@SuppressWarnings("unused")
	private static final Log log = LogFactory.getLog(XmlUtils.class);
	
	public static List<Element> getElementsFromNodeList(NodeList nl) {
		List<Element> l = new ArrayList<Element>();
		for(int i=nl.getLength()-1;i>=0;i--) {
			Node n = nl.item(i);
			if(n.getNodeType() == Node.ELEMENT_NODE) {
				l.add((Element)n);
			}
		}
		return l;
	}
	
	public static String getUniqueTagValueOfDescendant(Element el, String tagName) {
		NodeList nl = el.getElementsByTagName(tagName);
		int len = nl.getLength();
		if(len==1) {
			Element el1 = (Element) nl.item(0);
			return el1.getTextContent();
		}
		else if(len>1) {
			throw new IllegalStateException("Element has more than 1 descendant with tag '"+tagName+"' [size = "+len+"]");
		}
		return null;
	}

	public static Element getUniqueChild(Element el, String tagName) {
		NodeList nl = el.getChildNodes();
		int len = nl.getLength();
		Element ex = null;
		for(int i=0;i<len;i++) {
			Node n = nl.item(i);
			if(n.getNodeType()==Node.ELEMENT_NODE) {
				Element el1 = (Element) n;
				if(el1.getTagName().equals(tagName)) {
					if(ex!=null) {
						throw new IllegalStateException("Element has more than 1 child with tag '"+tagName+"' [size = "+len+"]");
					}
					ex = el1;
				}
			}
		}
		return ex;
	}

	public static String getUniqueTagValueOfChildren(Element el, String tagName) {
		Element ex = getUniqueChild(el, tagName);
		if(ex!=null) {
			return ex.getTextContent();
		}
		return null;
	}
	
	public static void writeSoapFault(HttpServletResponse resp, Throwable e, /*int status,*/ boolean debugMode) throws IOException {
		String message = e.getMessage();
		if(message==null) { message = e.toString(); }
		
		String faultCode = "Server";
		if(e instanceof InternalServerException) {
			faultCode = "Server";
		}
		else if(e instanceof BadRequestException) {
			faultCode = "Client";
		}
		
		resp.reset();
		// https://www.w3.org/TR/soap11/#_Toc478383529
		resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		resp.setContentType(SoapDumpSyntax.SOAP_MIMETYPE);
		
		StringBuilder sb = new StringBuilder();
		String prefix = "soapenv";
		sb.append(SoapDumpSyntax.getSoapHeader(prefix));
		sb.append("<"+prefix+":Fault>"+"\n");
		
		sb.append("<"+prefix+":Code>"+"<"+prefix+":Value>"+prefix+":"+faultCode+"</"+prefix+":Value>"+"</"+prefix+":Code>"+"\n");
		sb.append("<"+prefix+":Reason>"+"<"+prefix+":Text>"+message+"</"+prefix+":Text>"+"</"+prefix+":Reason>"+"\n");
		
		if(debugMode) {
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			sb.append("<"+prefix+":Detail>"+"<"+prefix+":Text>"+errors.toString()+"</"+prefix+":Text>"+"</"+prefix+":Detail>"+"\n");
		}

		sb.append("</"+prefix+":Fault>"+"\n");
		sb.append(SoapDumpSyntax.getSoapFooter(prefix));
		resp.getWriter().write(sb.toString());
	}
	
	
}
