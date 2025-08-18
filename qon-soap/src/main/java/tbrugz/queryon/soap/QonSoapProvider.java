package tbrugz.queryon.soap;

import java.io.Writer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import jakarta.xml.soap.SOAPPart;
import jakarta.xml.ws.Provider;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.ServiceMode;
import jakarta.xml.ws.WebServiceProvider;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Node;

import tbrugz.queryon.api.ODataServlet;
import tbrugz.queryon.util.MiscUtils;

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
		DocumentBuilderFactory docFactory = MiscUtils.getDocumentBuilderFactory();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		DOMImplementation domImpl = docBuilder.getDOMImplementation();
		
		ODataServlet.serialize(domImpl, node, writer);
	}

}
