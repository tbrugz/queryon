package tbrugz.queryon.soap;

import java.io.Writer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.ws.Provider;
import javax.xml.ws.Service;
import javax.xml.ws.ServiceMode;
import javax.xml.ws.WebServiceProvider;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Node;

import tbrugz.queryon.api.ODataServlet;

/*
 * https://stackoverflow.com/questions/5181194/message-level-jax-ws-service
 * https://stackoverflow.com/questions/522395/getting-raw-xml-from-soapmessage-in-java?rq=1
 * 
 * http://localhost:8080/qon-soap/soap
 */
@Deprecated
@WebServiceProvider(serviceName = "qonsoap", portName = "qonsoap-port", targetNamespace = "http://bitbucket.org/tbrugz/queryon/soap")
@ServiceMode(value = Service.Mode.MESSAGE)
public class QonSoapProvider implements Provider<SOAPMessage> {

	private static final Log log = LogFactory.getLog(QonSoapProvider.class);
	
	@Override
	public SOAPMessage invoke(SOAPMessage msg) {
		log.info(">> qon-soap: "+msg+" / "+msg.getContentDescription());
		
		try {
			SOAPPart sp = msg.getSOAPPart();
			
			SOAPEnvelope se = sp.getEnvelope();
			SOAPBody sb = se.getBody();
			//SOAPHeader sh = se.getHeader();

			//log.info("> "+sp+" / "+se+" / "+sb+" / "+sh + "\n "+sp.getAttributes()+" / "+se.get);
			log.info("sb: "+sb.getNodeName());
			log.info("sb/fc: "+sb.getFirstChild().getNodeName());
			/*sb.getNodeName()
			
			Writer w = new StringWriter();
			serialize(sb, w);
			log.info("body: "+w.toString());*/
			
			//SOAPBody body = msg.getSOAPBody();
			//body.get
		}
		catch(SOAPException e) {
			log.warn("Error: "+e, e);
		}
		/*catch(ParserConfigurationException e) {
			log.warn("Error: "+e, e);
		}*/
		
		return null;
	}
	
	void serialize(Node node, Writer writer) throws ParserConfigurationException {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		DOMImplementation domImpl = docBuilder.getDOMImplementation();
		
		ODataServlet.serialize(domImpl, node, writer);
	}

}
