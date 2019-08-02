package tbrugz.queryon.webdav;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.sqldump.dbmodel.Constraint;

public class WebDavRequest extends RequestSpec {

	private static final Log log = LogFactory.getLog(WebDavRequest.class);
	
	public WebDavRequest(DumpSyntaxUtils dsutils, HttpServletRequest req, Properties prop) throws ServletException, IOException {
		super(dsutils, req, prop, 0, "xml", false, 0, null);
	}
	
	void setPropFindRequest(Constraint uk, int urlPartCount) {
		distinct = true;
		if(uk.getUniqueColumns().size() < urlPartCount) {
			log.info("uk column count ["+uk.getUniqueColumns().size()+"] < urlPartCount ["+urlPartCount+"]");
			//throw new BadRequestException("uk column count ["+uk.getUniqueColumns().size()+"] < urlPartCount ["+urlPartCount+"]");
		}
		if(uk.getUniqueColumns().size()+1 < urlPartCount) {
			throw new BadRequestException("uk column count + 1 ["+(uk.getUniqueColumns().size()+1)+"] < urlPartCount ["+urlPartCount+"]");
		}
		if(urlPartCount >= uk.getUniqueColumns().size()) {
			log.info("urlPartCount == "+urlPartCount+" ; uk.getUniqueColumns().size() =="+uk.getUniqueColumns().size()+" ; uk.getUniqueColumns() == "+uk.getUniqueColumns());
			//throw new InternalServerException("urlPartCount == "+urlPartCount+" ; uk.getUniqueColumns().size() =="+uk.getUniqueColumns().size()+" ; uk.getUniqueColumns() == "+uk.getUniqueColumns());
		}
		else {
			columns.add(uk.getUniqueColumns().get(urlPartCount));
		}
		//limit = null;
	}

}
