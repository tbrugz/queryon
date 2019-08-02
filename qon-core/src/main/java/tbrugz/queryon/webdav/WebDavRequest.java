package tbrugz.queryon.webdav;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.BadRequestException;
import tbrugz.queryon.QueryOn;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.sqldump.dbmodel.Constraint;

public class WebDavRequest extends RequestSpec {

	private static final Log log = LogFactory.getLog(WebDavRequest.class);
	
	public WebDavRequest(DumpSyntaxUtils dsutils, HttpServletRequest req, Properties prop) throws ServletException, IOException {
		super(dsutils, req, prop, 0, "xml", false, 0, null);
	}
	
	void setUniqueKey(Constraint uk) throws IOException {
		int paramCount = getParams().size();
		
		if(paramCount > uk.getUniqueColumns().size()+1) {
			throw new BadRequestException("uk column count + 1 ["+(uk.getUniqueColumns().size()+1)+"] < paramCount ["+paramCount+"]");
		}
		
		if(paramCount > uk.getUniqueColumns().size()) {
			// full key & column defined
			log.info("uk column count ["+uk.getUniqueColumns().size()+"] < paramCount ["+paramCount+"]");
			//throw new BadRequestException("uk column count ["+uk.getUniqueColumns().size()+"] < urlPartCount ["+urlPartCount+"]");
			String column = String.valueOf(getParams().remove(paramCount-1));
			//log.info("column = "+column+" / params = "+getParams());
			columns.add(column);
			if(httpMethod.equals(QueryOn.METHOD_GET)) {
				uniValueCol = column;
			}
			else if(httpMethod.equals(QueryOn.METHOD_PUT)) {
				String body = getRequestBody(request);
				//log.info("setUniqueKey: PUT: "+column+" / "+body);
				updateValues.put(column, body);
			}
		}
		else if(paramCount == uk.getUniqueColumns().size()) {
			// full key
			log.info("paramCount == "+paramCount+" ; uk.getUniqueColumns().size() =="+uk.getUniqueColumns().size()+" ; uk.getUniqueColumns() == "+uk.getUniqueColumns());
			//throw new InternalServerException("urlPartCount == "+urlPartCount+" ; uk.getUniqueColumns().size() =="+uk.getUniqueColumns().size()+" ; uk.getUniqueColumns() == "+uk.getUniqueColumns());
		}
		else {
			//full key not defined
			if(httpMethod.equals(WebDavServlet.METHOD_PROPFIND)) {
				distinct = true;
				columns.add(uk.getUniqueColumns().get(paramCount));
			}
		}
	}
	
	List<String> getColumns() {
		return columns;
	}
	
	@Override
	protected void processBody(HttpServletRequest req) throws NumberFormatException, IOException, ServletException {}

}
