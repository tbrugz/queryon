package tbrugz.queryon.springboot;

import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.Context;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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

	public static final String DEFAULT_DATASOURCE_PREFIX = "spring.datasource";

	public static final String QUERYON_DATASOURCES_PREFIX = "queryon.datasources-prefix";
	public static final String QUERYON_DATASOURCES = "queryon.datasources";

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

				String dsPrefix = env.getProperty(QUERYON_DATASOURCES_PREFIX);
				log.debug("postProcessContext, dsPrefix: "+dsPrefix);

				@SuppressWarnings("unchecked")
				List<String> prefixes = env.getProperty(QUERYON_DATASOURCES, List.class);
				log.debug("postProcessContext, prefixes: "+prefixes);
				
				if(prefixes==null) {
					prefixes = new ArrayList<>();
					prefixes.add(DEFAULT_DATASOURCE_PREFIX);
				}

				String dsNamePrefix = dsPrefix+"-";
				for(String prefix: prefixes) {
					//String prefix = DataSourceUtils.DEFAULT_DATASOURCE_PREFIX; //"spring.datasource.";
					String dataSourceId = null;
					if(prefix.startsWith(dsNamePrefix)) {
						dataSourceId = prefix.substring(dsNamePrefix.length());
					}
					/*
					else {
						dataSourceName = "jdbc/datasource";
					}
					*/
					DataSourceUtils.addHikariDataSource(context, env, prefix+".", dataSourceId);
				}
				
				/*
				ContextResource resource = new ContextResource();
				String dataSourceName = env.getProperty(prefix+"datasource-name");
				if(dataSourceName==null) {
					dataSourceName = "jdbc/datasource";
				}

				resource.setName(dataSourceName);
				resource.setType(DataSource.class.getName());
				//resource.setProperty("factory", "org.apache.tomcat.jdbc.pool.DataSourceFactory");
				resource.setProperty("factory", "com.zaxxer.hikari.HikariJNDIFactory");
				//resource.setProperty("jmxEnabled", "false");
				
				//setPropIfNotEmpty(resource, "url", env.getProperty(prefix+"url"));
				// Hiraki uses 'jdbcUrl'...
				setPropIfNotEmpty(resource, "jdbcUrl", env.getProperty(prefix+"url"));
				setPropIfNotEmpty(resource, "username", env.getProperty(prefix+"username"));
				setPropIfNotEmpty(resource, "password", env.getProperty(prefix+"password"));
				setPropIfNotEmpty(resource, "driverClassName", env.getProperty(prefix+"driver-class-name"));
				
				context.getNamingResources().addResource(resource);
				log.info("added datasource to '"+dataSourceName+"'");
				*/
			}

			/*
			void setPropIfNotEmpty(ContextResource resource, String key, String value) {
				if(value!=null) {
					resource.setProperty(key, value);
					//log.debug("property set: "+key);
				}
				else {
					//log.debug("property not set: "+key);
				}
			}
			*/
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

	/*
	@Bean
	public DataSource getDataSource() {
		String dataSourceName = env.getProperty(prefix+"datasource-name");
		if(dataSourceName==null) {
			dataSourceName = "jdbc/datasource";
		}
		Connection conn = ConnectionUtil.getConnectionFromDataSource(dataSourceName, ConnectionUtil.DEFAULT_INITIAL_CONTEXT);
		return conn;
	}
	*/

}
