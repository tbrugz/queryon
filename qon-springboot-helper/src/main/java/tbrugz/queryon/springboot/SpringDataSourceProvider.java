package tbrugz.queryon.springboot;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

import tbrugz.sqldump.util.DataSourceProvider;

@Configuration
public class SpringDataSourceProvider implements DataSourceProvider, ApplicationContextAware {

	private static final Log log = LogFactory.getLog(SpringDataSourceProvider.class);

	static ApplicationContext ctx;
	
	public SpringDataSourceProvider() {
		log.debug("SpringDataSourceProvider()");
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		log.info("setApplicationContext(), ctx = "+applicationContext);
		ctx = applicationContext;
	}

	@Override
	public DataSource getDataSource(String name) {
		if(ctx == null) {
			String message = "getDataSource: ApplicationContext is null ( make sure to @Import(SpringDataSourceProvider.class) )";
			log.error(message);
			throw new IllegalStateException(message);
		}
		if(name==null || name.isEmpty()) {
			//log.debug("getDataSource (empty name)");
			return ctx.getBean(DataSource.class);
		}
		//log.debug("getDataSource: name = "+name);
		return ctx.getBean(name, DataSource.class);
	}

}
