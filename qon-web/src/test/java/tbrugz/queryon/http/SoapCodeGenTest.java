package tbrugz.queryon.http;

import static tbrugz.queryon.http.JettySetup.qonUrl;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;

import javax.naming.NamingException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.Detail;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.soap.SOAPFaultException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.tools.common.ToolContext;
import org.apache.cxf.tools.wsdlto.WSDLToJava;
/*
import org.bitbucket.tbrugz.queryon.queryonservice_wsdl.QueryOnService;
import org.bitbucket.tbrugz.queryon.queryonservice.FieldsType;
import org.bitbucket.tbrugz.queryon.queryonservice.FiltersType;
import org.bitbucket.tbrugz.queryon.queryonservice.ListOfPUBLICDEPT;
import org.bitbucket.tbrugz.queryon.queryonservice.ListOfQUERYNAMEDPARAMS1;
import org.bitbucket.tbrugz.queryon.queryonservice.ListOfQUERYQUERYWITHPOSITIONALPARAMS;
import org.bitbucket.tbrugz.queryon.queryonservice.MultiValueFilterType;
import org.bitbucket.tbrugz.queryon.queryonservice.ObjectFactory;
import org.bitbucket.tbrugz.queryon.queryonservice.PUBLICDEPTRequest;
import org.bitbucket.tbrugz.queryon.queryonservice.PUBLICDEPTType;
import org.bitbucket.tbrugz.queryon.queryonservice.QUERYNAMEDPARAMS1Request;
import org.bitbucket.tbrugz.queryon.queryonservice.QUERYNAMEDPARAMS1Type;
import org.bitbucket.tbrugz.queryon.queryonservice.QUERYQUERYWITHPOSITIONALPARAMSRequest;
import org.bitbucket.tbrugz.queryon.queryonservice.QUERYQUERYWITHPOSITIONALPARAMSType;
import org.bitbucket.tbrugz.queryon.queryonservice_wsdl.QueryOnServicePortType;
*/
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

import tbrugz.sqldump.sqlrun.SQLRun;

public class SoapCodeGenTest {

	private static final Log log = LogFactory.getLog(SoapCodeGenTest.class);
	
	public static final String basedir = "src/test/java";
	public static final String outdir = "work/output";

	public static final String generatedCodeDir = "target/test/java/";
	
	static final String soaplUrl = qonUrl + "/qonsoap";
	static final String wsdlUrl = soaplUrl+"?wsdl";
	
	static DocumentBuilderFactory dbFactory;
	static DocumentBuilder dBuilder;
	
	@BeforeClass
	public static void setup() throws Exception {
		setupH2();
		JettySetup.setupServer();
		
		dbFactory = DocumentBuilderFactory.newInstance();
		dBuilder = dbFactory.newDocumentBuilder();
	}
	
	@AfterClass
	public static void shutdown() throws Exception {
		JettySetup.shutdown();
	}

	@Before
	public void before() throws ClassNotFoundException, IOException, SQLException, NamingException {
		setupH2();
	}
	
	@After
	public void after() {
	}
	
	public static void setupH2() throws ClassNotFoundException, IOException, SQLException, NamingException {
		String[] params = {"-propfile="+basedir+"/tbrugz/queryon/http/sqlrun.properties"};
		SQLRun.main(params);
	}

	//@Test
	//@Ignore()
	//public void nullTest() {}
	
	//---------------------
	
	// https://cxf.apache.org/docs/wsdl-to-java.html
	@Test
	@Ignore("see SoapWebTest")
	public void generateJavaFromWsdl() throws Exception {
		String url = soaplUrl+"?wsdl";
		String[] args = { "-d", generatedCodeDir, url };
		
		//WSDLToJava.main(args); //do not generate exceptions...
		WSDLToJava w2j = new WSDLToJava(args);
		w2j.run(new ToolContext());
		
		// XXX compile? add to (test) classpath?
	}

	/*
	
	// XXX best way to 'callGeneratedJavaFromWsdl'?
	@Test
	public void callGeneratedJavaFromWsdl1() throws IOException, XMLStreamException, SAXException {
		QueryOnService qons = new QueryOnService(new URL(wsdlUrl));
		QueryOnServicePortType qonsp = qons.getQueryOnServicePort();
		
		{
			PUBLICDEPTRequest r = new PUBLICDEPTRequest();
			r.setLimit(2);
			r.setOffset(1);
			ListOfPUBLICDEPT lopd = qonsp.getPUBLICDEPT(r);
			log.info(">>> test1");
			{
				List<PUBLICDEPTType> l = lopd.getPUBLICDEPT();
				for(PUBLICDEPTType pd: l) {
					log.info("id="+pd.getID()+" / name="+pd.getNAME());
				}
			}
		}
	}
	
	@Test
	public void callGeneratedJavaFromWsdl2() throws IOException, XMLStreamException, SAXException {
		QueryOnService qons = new QueryOnService(new URL(wsdlUrl));
		QueryOnServicePortType qonsp = qons.getQueryOnServicePort();
		
		{
			PUBLICDEPTRequest r = new PUBLICDEPTRequest();
			r.setLimit(-1);
			r.setOffset(null);
			r.setLimit(null);
			r.setDistinct(true);
			ListOfPUBLICDEPT lopd = qonsp.getPUBLICDEPT(r);
			List<PUBLICDEPTType> l = lopd.getPUBLICDEPT();
			log.info(">>> test2");
			for(PUBLICDEPTType pd: l) {
				log.info("id="+pd.getID()+" / name="+pd.getNAME());
			}
		}
		
		try {
			//XXX: call something that should generate an error...
			//Assert.fail("Exception should have been thrown");
		}
		catch(SOAPFaultException ex) {
			String faultcode = ex.getFault().getFaultCode();
			String faultstring = ex.getFault().getFaultString();
			Detail detail = ex.getFault().getDetail();
			String faultdetail = detail!=null?detail.getValue():null;
			log.info("faultcode: "+faultcode+" // faultstring: "+faultstring+" // faultdetail: "+faultdetail);
		}
	}
	
	@Test
	public void callWithFields() throws IOException, XMLStreamException, SAXException {
		QueryOnService qons = new QueryOnService(new URL(wsdlUrl));
		QueryOnServicePortType qonsp = qons.getQueryOnServicePort();
		
		{
			PUBLICDEPTRequest r = new PUBLICDEPTRequest();
			r.setLimit(2);
			r.setOffset(1);
			FieldsType ft = new FieldsType();
			ft.setField("NAME");
			r.setFields(ft);
			ListOfPUBLICDEPT lopd = qonsp.getPUBLICDEPT(r);
			log.info(">>> test1");
			{
				List<PUBLICDEPTType> l = lopd.getPUBLICDEPT();
				for(PUBLICDEPTType pd: l) {
					log.info("id="+pd.getID()+" / name="+pd.getNAME());
				}
			}
		}
	}

	@Test
	public void callQueryWithNamedParam() throws IOException, XMLStreamException, SAXException {
		QueryOnService qons = new QueryOnService(new URL(wsdlUrl));
		QueryOnServicePortType qonsp = qons.getQueryOnServicePort();
		
		{
			QUERYNAMEDPARAMS1Request r = new QUERYNAMEDPARAMS1Request();
			r.setLimit(2);
			r.setOffset(1);
			r.setPar1("abc");
			r.setPar2("def");
			//FieldsType ft = new FieldsType();
			//ft.setField("NAME");
			//r.setFields(ft);
			ListOfQUERYNAMEDPARAMS1 lopd = qonsp.getQUERYNAMEDPARAMS1(r);
			log.info(">>> test1");
			{
				List<QUERYNAMEDPARAMS1Type> l = lopd.getQUERYNAMEDPARAMS1();
				for(QUERYNAMEDPARAMS1Type pd: l) {
					log.info("c1="+pd.getC1());
				}
			}
		}

		{
			QUERYNAMEDPARAMS1Request r = new QUERYNAMEDPARAMS1Request();
			r.setLimit(2);
			r.setOffset(1);
			r.setPar1("abc");
			try {
				//@SuppressWarnings("unused")
				//ListOfQUERYNAMEDPARAMS1 lopd = 
				qonsp.getQUERYNAMEDPARAMS1(r);
				Assert.fail("Exception should have been thrown");
			}
			catch(SOAPFaultException e) {
				log.info("Expected exception [SOAPFaultException]: "+e);
			}
		}
	}
	
	@Test
	public void callQueryWithPositionalParam() throws IOException, XMLStreamException, SAXException {
		QueryOnService qons = new QueryOnService(new URL(wsdlUrl));
		QueryOnServicePortType qonsp = qons.getQueryOnServicePort();
		
		{
			QUERYQUERYWITHPOSITIONALPARAMSRequest r = new QUERYQUERYWITHPOSITIONALPARAMSRequest();
			r.setLimit(2);
			r.setOffset(1);
			r.setParameter1("abc");
			r.setParameter2("def");
			r.setParameter3("ghi");
			ListOfQUERYQUERYWITHPOSITIONALPARAMS lopd = qonsp.getQUERYQUERYWITHPOSITIONALPARAMS(r);
			log.info(">>> test1");
			{
				List<QUERYQUERYWITHPOSITIONALPARAMSType> l = lopd.getQUERYQUERYWITHPOSITIONALPARAMS();
				for(QUERYQUERYWITHPOSITIONALPARAMSType pd: l) {
					log.info("c1="+pd.getC1());
				}
			}
		}
	}

	@Test
	public void callWithFilterIn() throws IOException, XMLStreamException, SAXException {
		QueryOnService qons = new QueryOnService(new URL(wsdlUrl));
		QueryOnServicePortType qonsp = qons.getQueryOnServicePort();
		
		{
			PUBLICDEPTRequest r = new PUBLICDEPTRequest();
			
			//
			ObjectFactory of = new ObjectFactory();
			MultiValueFilterType mvft = of.createMultiValueFilterType();
			mvft.setField("NAME");
			mvft.setValue("HR");
			FiltersType ft = of.createFiltersType();
			ft.setFilterIn(mvft);
			r.setFilters(ft);
			//
			
			ListOfPUBLICDEPT lopd = qonsp.getPUBLICDEPT(r);
			log.info(">>> callWithFilterIn");
			{
				List<PUBLICDEPTType> l = lopd.getPUBLICDEPT();
				for(PUBLICDEPTType pd: l) {
					log.info("id="+pd.getID()+" / name="+pd.getNAME());
				}
			}
		}
	}
	
	*/
}