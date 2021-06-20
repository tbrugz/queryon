package tbrugz.queryon.springboot;

import javax.sql.DataSource;

import org.apache.catalina.Context;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DataSourceConfig {

	private static final Log log = LogFactory.getLog(DataSourceConfig.class);

	@Autowired
	private Environment env;
	
	/*
	 * https://stackoverflow.com/questions/24941829/how-to-create-jndi-context-in-spring-boot-with-embedded-tomcat-container
	 * https://github.com/brettwooldridge/HikariCP
	 */
	@Bean
	public TomcatServletWebServerFactory tomcatFactory() {
		return new TomcatServletWebServerFactory() {
			@Override
			protected TomcatWebServer getTomcatWebServer(org.apache.catalina.startup.Tomcat tomcat) {
				tomcat.enableNaming();
				log.debug("getTomcatWebServer...");
				return super.getTomcatWebServer(tomcat);
			}
			
			@Override
			protected void postProcessContext(Context context) {
				log.debug("postProcessContext...");

				ContextResource resource = new ContextResource();
				String prefix = "spring.datasource.";
				String dataSourceName = env.getProperty(prefix+"datasource-name");
				if(dataSourceName==null) {
					dataSourceName = "jdbc/datasource";
				}

				resource.setName(dataSourceName);
				resource.setType(DataSource.class.getName());
				//resource.setProperty("factory", "org.apache.tomcat.jdbc.pool.DataSourceFactory");
				resource.setProperty("factory", "com.zaxxer.hikari.HikariJNDIFactory");
				
				//setPropIfNotEmpty(resource, "url", env.getProperty(prefix+"url"));
				// Hiraki uses 'jdbcUrl'...
				setPropIfNotEmpty(resource, "jdbcUrl", env.getProperty(prefix+"url"));
				setPropIfNotEmpty(resource, "username", env.getProperty(prefix+"username"));
				setPropIfNotEmpty(resource, "password", env.getProperty(prefix+"password"));
				setPropIfNotEmpty(resource, "driverClassName", env.getProperty(prefix+"driver-class-name"));
				
				context.getNamingResources().addResource(resource);
			}

			void setPropIfNotEmpty(ContextResource resource, String key, String value) {
				if(value!=null) {
					resource.setProperty(key, value);
					//log.debug("property set: "+key);
				}
				else {
					//log.debug("property not set: "+key);
				}
			}
		};
		
	}
	
	/*
	@Bean
	@ConfigurationProperties(prefix = "spring.datasource")
	public DataSource getDataSource() {
		DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
		return dataSourceBuilder.build();
	}
	*/
}
