package tbrugz.queryon.processor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.subject.Subject;

import tbrugz.queryon.ProcessorServlet;
import tbrugz.queryon.RequestSpec;
import tbrugz.queryon.WebProcessor;
import tbrugz.queryon.exception.InternalServerException;
import tbrugz.sqldump.datadump.SQLQueries;
import tbrugz.sqldump.dbmodel.DBIdentifiable;

public class SQLQueriesLoader extends SQLQueries implements WebProcessor {

	static final Log log = LogFactory.getLog(SQLQueriesLoader.class);
	
	ServletContext servletContext;
	
	public SQLQueriesLoader() {
		runQueries = false;
		addQueriesToModel = true;
		defaultSchemaName = null;
		grabColsInfoFromMetadata = true;
	}
	
	@Override
	protected Set<String> listResourcesFromPath(String path) {
		Set<String> res = servletContext.getResourcePaths(path);
		if(res!=null) {
			log.debug("listResourcesFromPath: resources="+res+" [path="+path+"]");
		}
		//else {
		//	log.warn("listResourcesFromPath: path '"+path+"' can't be listed");
		//}
		return res;
	}
	
	@Override
	protected InputStream getResourceAsStream(String path) {
		return servletContext.getResourceAsStream(path);
	}
	
	@Override
	public void setDBIdentifiable(DBIdentifiable dbid) {
	}

	@Override
	public void setSubject(Subject currentUser) {
	}

	@Override
	public void process(ServletContext context, RequestSpec reqspec, HttpServletResponse resp) {
		this.servletContext = context;
		try {
			ProcessorServlet.setOutput(this, resp);
		}
		catch(IOException e) {
			throw new InternalServerException(e.getMessage(), e);
		}
		process();
	}

}
