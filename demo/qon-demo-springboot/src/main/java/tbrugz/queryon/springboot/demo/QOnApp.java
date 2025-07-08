package tbrugz.queryon.springboot.demo;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.shiro.web.env.EnvironmentLoaderListener;
import org.apache.shiro.web.servlet.ShiroFilter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import tbrugz.queryon.AbstractHttpServlet;
import tbrugz.queryon.springboot.QOnSpringBootApp;
import tbrugz.queryon.springboot.SpringBootUtils;
import tbrugz.queryon.springboot.SpringDataSourceProvider;

/*
 * https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-web-applications.embedded-container.servlets-filters-listeners
 *
 * h2 console (when using Spring Boot's developer tools):
 * https://docs.spring.io/spring-boot/reference/data/sql.html#data.sql.h2-web-console
 * https://docs.spring.io/spring-boot/reference/using/devtools.html
 * props: spring.h2.console.enabled, spring.h2.console.path
 */
@SpringBootApplication
// https://stackoverflow.com/a/30273253
//@ComponentScan("tbrugz.queryon.springboot")
/* Use @Import(DataSourceConfig.class) or @Import(SpringDataSourceProvider.class), not both */
//@Import(DataSourceConfig.class)
@Import(SpringDataSourceProvider.class)
public class QOnApp extends QOnSpringBootApp { //SpringBootServletInitializer {

	static final Log log = LogFactory.getLog(QOnApp.class);

	static ApplicationContext applicationContext;
	
	public static void main(String[] args) {
		applicationContext = SpringApplication.run(QOnApp.class, args);
		SpringBootUtils.showAppInfo(applicationContext);
		SpringBootUtils.displayServletBeans(applicationContext);
	}
	
	public static ApplicationContext getApplicationContext() {
		return applicationContext;
	}

	/*
	public static void showAppInfo() {
		ApplicationContext ctx = getApplicationContext();
		log.info("showAppInfo: appName = " + ctx.getId());
		log.info("showAppInfo: startup = " + new Date(ctx.getStartupDate()) );
			//" ; displayName = " + getApplicationContext().getDisplayName() +
			//" ; context = " + getApplicationContext() +
			//" ; QOnApp.class = "+QOnApp.class.getName()+" ; CL = "+QOnApp.class.getClassLoader());
	}

	// see: https://www.baeldung.com/spring-show-all-beans
	public static void displayServletBeans() {
		boolean showInfo = log.isDebugEnabled();
		if(showInfo) {
			ApplicationContext ctx = getApplicationContext();
			String[] allBeanNames = getApplicationContext().getBeanDefinitionNames();
			for(String beanName : allBeanNames) {
				Object bean = ctx.getBean(beanName);
				//log.info(beanName + ": "+bean.getClass());
				if(bean instanceof ServletRegistrationBean) {
					log.debug("- " + beanName + ": "+bean);
				}
			}
		}
	}
	*/
	
	@Bean
	//@Primary
	@ConfigurationProperties(prefix="spring.datasource")
	public DataSource primaryDataSource() {
		return DataSourceBuilder.create().build();
	}

	/*
	@Bean
	@ConfigurationProperties(prefix="spring.secondDatasource")
	public DataSource secondaryDataSource() {
		return DataSourceBuilder.create().build();
	}
	*/
	
	/*
	@Bean
	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return args -> {

			//System.out.println("Let's inspect the beans provided by Spring Boot:");

			String[] beanNames = ctx.getBeanDefinitionNames();
			Arrays.sort(beanNames);
			for (String beanName : beanNames) {
				//System.out.println(beanName);
			}

		};
	}
	*/
	
	/*
	 * https://stackoverflow.com/questions/24941829/how-to-create-jndi-context-in-spring-boot-with-embedded-tomcat-container
	 * TODO: add datasource stuff to configuration class...
	 */
	/*@Bean
	public TomcatServletWebServerFactory tomcatFactory() {
		return new TomcatServletWebServerFactory() {
			@Override
			protected TomcatWebServer getTomcatWebServer(org.apache.catalina.startup.Tomcat tomcat) {
				tomcat.enableNaming();
				return super.getTomcatWebServer(tomcat);
			}
			
			@Override
			protected void postProcessContext(Context context) {
				//containerContext = context;
				//for each configuration in spring.datasource*, add resource in context...
				// or assume one: spring.datasource
				// or list of datasources should be in a property: spring.datasources=ds1,ds2,...
				// https://stackoverflow.com/questions/44803211/read-environment-variable-in-springboot
				context.getNamingResources().addResource(null);
			}
		};
	}*/

	// https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#howto-create-a-deployable-war-file
	// https://howtodoinjava.com/spring-boot/spring-boot-jsp-view-example/
	/*
	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(QOnApp.class);
	}
	*/

	/*@Bean
	@ConfigurationProperties(prefix = "spring.datasource")
	public DataSource getDataSource() {
		DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
		return dataSourceBuilder.build();
	}*/

	/*
	// https://stackoverflow.com/questions/32394862/how-to-register-servletcontextlistener-in-spring-boot
	@Bean
	public ServletContextListener shiroListener() {
		return new EnvironmentLoaderListener();
	}
	*/

	// https://stackoverflow.com/a/20939923/616413
	@Bean
	public ServletRegistrationBean servletQueryOnBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.QueryOn();
		ServletRegistrationBean bean = new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping()); //"/q/*"
		bean.setLoadOnStartup(1);
		return bean;
	}

	@Bean
	public ServletRegistrationBean servletInfoServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.api.InfoServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean servletAuthServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.auth.AuthServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	/*
	@Bean
	public ServletRegistrationBean servletQueryOnInstantBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.QueryOnInstant();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean servletDiffServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.diff.DiffServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean servletDataDiffServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.diff.DataDiffServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean servletDiff2QServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.diff.Diff2QServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean servletDiffManyServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.diff.DiffManyServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}
	*/

	/*
	@Bean
	public ServletRegistrationBean servletCool303Bean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.Cool303RedirectionServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}
	*/

	@Bean
	public ServletRegistrationBean servletProcessorServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.ProcessorServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean servletPagesServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.PagesServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean servletMarkdownServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.MarkdownServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean servletSwaggerServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.api.SwaggerServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean servletODataServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.api.ODataServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean servletGraphQlQonServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.graphql.GraphQlQonServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean servletQonSoapServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.soap.QonSoapServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	/*
	@Bean
	public ServletRegistrationBean servletWebDavServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.webdav.WebDavServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}
	*/

	/*
	// https://stackoverflow.com/questions/19825946/how-to-add-a-filter-class-in-spring-boot/30658752#30658752
	@Bean
	public FilterRegistrationBean shiroFilterRegistration() {
		FilterRegistrationBean registration = new FilterRegistrationBean();
		registration.setFilter(new org.apache.shiro.web.servlet.ShiroFilter());
		registration.addUrlPatterns("/*");
		registration.setName("ShiroFilter");
		registration.setOrder(1);
		return registration;
	}
	*/

}
