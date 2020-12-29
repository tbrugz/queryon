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
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.sqldump.dbmodel.Constraint;

public class WebDavRequest extends RequestSpec {

	private static final Log log = LogFactory.getLog(WebDavRequest.class);
	
	//final boolean multiModel;
	
	public WebDavRequest(HttpServletRequest req, Properties prop, boolean multiModel) throws ServletException, IOException {
		super(req, prop, multiModel?1:0, "xml", false, 0, null);
		//this.multiModel = multiModel;
		//log.info("multiModel="+multiModel+" / modelId="+this.getModelId());
	}
	
	void setUniqueKey(Constraint uk) throws IOException {
		int paramCount = getParams().size();
		
		if(paramCount > uk.getUniqueColumns().size()+1) {
			throw new BadRequestException("uk column count + 1 ["+(uk.getUniqueColumns().size()+1)+"] < paramCount ["+paramCount+"]");
		}
		
		if(paramCount > uk.getUniqueColumns().size()) {
			// full key & column defined
			log.debug("paramCount ["+paramCount+"] > uk column count ["+uk.getUniqueColumns().size()+"] [full key & column defined]");
			if(paramCount > uk.getUniqueColumns().size() + 1) {
				log.warn("paramCount ["+paramCount+"] > uk column count + 1 ["+(uk.getUniqueColumns().size()+1)+"]?");
			}
			//throw new BadRequestException("uk column count ["+uk.getUniqueColumns().size()+"] < urlPartCount ["+urlPartCount+"]");
			String column = String.valueOf(getParams().remove(paramCount-1));
			//log.info("column = "+column+" / params = "+getParams());
			addColumnIfNotAlreadyAdded(column);
			if(httpMethod.equals(QueryOn.METHOD_GET) || httpMethod.equals(QueryOn.METHOD_HEAD)) {
				uniValueCol = column;
			}
			else if(httpMethod.equals(QueryOn.METHOD_PUT)) {
				updatePartValues.put(column, new SimplePart(request.getInputStream()));
			}
			else if(httpMethod.equals(QueryOn.METHOD_DELETE)) {
				updateValues.put(column, null);
			}
		}
		else if(paramCount == uk.getUniqueColumns().size()) {
			// full key
			log.debug("paramCount == "+paramCount+" ; uk.getUniqueColumns().size() =="+uk.getUniqueColumns().size()+" ; uk.getUniqueColumns() == "+uk.getUniqueColumns());
		}
		else {
			//full key not defined
			if(httpMethod.equals(WebDavServlet.METHOD_PROPFIND)) {
				distinct = true;
				addColumnIfNotAlreadyAdded(uk.getUniqueColumns().get(paramCount));
			}
		}
	}
	
	@Override
	protected String getModelId(HttpServletRequest req, int prefixesToIgnore) {
		//log.info("getModelId: prefixesToIgnore="+prefixesToIgnore+" [multimodel = "+(prefixesToIgnore > 0)+"]");
		if(prefixesToIgnore > 0) {
			List<String> partz = getUrlParts(req.getPathInfo());
			//log.info("getModelId: partz = "+partz);
			if(partz.size()>0) {
				return partz.get(0);
			}
			return null;
		}
		return SchemaModelUtils.getDefaultModelId(req.getServletContext());
	}
	
	void addColumnIfNotAlreadyAdded(String column) {
		if(!columns.contains(column)) {
			columns.add(column);
		}
		if(columns.size()>1) {
			throw new IllegalArgumentException("can't allow more than 1 column [col="+column+" ; columns="+columns+"]");
		}
	}
	
	@Deprecated
	List<String> getColumns() {
		return columns;
	}

	String getColumn() {
		if(columns.size()==1) {
			return columns.get(0);
		}
		return null;
	}
	
	@Override
	protected void processBody(HttpServletRequest req) throws NumberFormatException, IOException, ServletException {}

}
