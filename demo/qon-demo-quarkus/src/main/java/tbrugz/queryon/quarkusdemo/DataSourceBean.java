package tbrugz.queryon.quarkusdemo;

//import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.sql.DataSource;

// commons-logging
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// slf4j
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.Startup;
import tbrugz.sqldump.util.DataSourceProvider;

@Startup
//@ApplicationScoped // lazy
@Singleton //eager
public class DataSourceBean implements DataSourceProvider {

	static final Log log = LogFactory.getLog(DataSourceBean.class);
	//static final Logger log = LoggerFactory.getLogger(DataSourceBean.class);

	//@Inject
	AgroalDataSource defaultDataSource;
	
	public DataSourceBean() {
		//log.info("new DataSourceBean()");
	}
	
	/*
	@Inject
	public DataSourceBean(AgroalDataSource defaultDataSource) {
		log.info("new DataSourceBean(): injecting "+defaultDataSource);
		this.defaultDataSource = defaultDataSource;
	}
	*/
	
	@Inject
	public void setDataSource(AgroalDataSource datasource) {
		if(datasource==null) {
			log.info("setDataSource: will not inject null datasource...");
			return;
		}
		log.info("setDataSource: injecting datasource: "+datasource);
		defaultDataSource = datasource;
	}

	@Override
	public DataSource getDataSource(String name) {
		//log.info("getDataSource: datasource name ["+name+"] ignored (unique datasource)");
		//log.info("defaultDataSource = "+defaultDataSource);
		return defaultDataSource;
	}
	
}
