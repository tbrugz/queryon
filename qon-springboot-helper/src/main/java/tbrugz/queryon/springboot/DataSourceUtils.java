package tbrugz.queryon.springboot;

import javax.sql.DataSource;

import org.apache.catalina.Context;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.springframework.core.env.Environment;

public class DataSourceUtils {

	private static final Log log = LogFactory.getLog(DataSourceConfig.class);
	
	public static void addHikariDataSource(Context context, Environment env, String prefix, String dataSourceId) {
		//log.debug("addHikariDataSource...");
		//String prefix = "spring.datasource.";
		String dataSourceName = env.getProperty(prefix+"datasource-name");
		if(dataSourceName==null) {
			/*String dsNamePrefix = prefix+"-";
			if(prefix.startsWith(dsNamePrefix)) {
				dataSourceName = prefix.substring(dsNamePrefix.length());
			}*/
			if(dataSourceId!=null) {
				dataSourceName = dataSourceId;
			}
			else {
				dataSourceName = "jdbc/datasource";
			}
		}

		log.debug("addHikariDataSource["+dataSourceId+"]: "+dataSourceName);

		ContextResource resource = new ContextResource();
		resource.setName(dataSourceName);
		resource.setType(DataSource.class.getName());
		//resource.setProperty("factory", "org.apache.tomcat.jdbc.pool.DataSourceFactory");
		resource.setProperty("factory", "com.zaxxer.hikari.HikariJNDIFactory");
		//resource.setProperty("jmxEnabled", "false");
		
		//setPropIfNotEmpty(resource, "url", env.getProperty(prefix+"url"));
		// Hikari uses 'jdbcUrl'...
		setPropIfNotEmpty(resource, "jdbcUrl", env.getProperty(prefix+"url"));
		setPropIfNotEmpty(resource, "username", env.getProperty(prefix+"username"));
		setPropIfNotEmpty(resource, "password", env.getProperty(prefix+"password"));
		setPropIfNotEmpty(resource, "driverClassName", env.getProperty(prefix+"driver-class-name"));
		
		context.getNamingResources().addResource(resource);
		log.info("added datasource '"+dataSourceName+"'");
	}

	static void setPropIfNotEmpty(ContextResource resource, String key, String value) {
		if(value!=null) {
			resource.setProperty(key, value);
			//log.debug("property set: "+key);
		}
		else {
			//log.debug("property not set: "+key);
		}
	}

}
