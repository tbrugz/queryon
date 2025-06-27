package tbrugz.queryon.springboot;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletContextListener;

import org.apache.shiro.web.env.EnvironmentLoaderListener;
import org.apache.shiro.web.servlet.ShiroFilter;

//import org.keycloak.adapters.servlet.KeycloakOIDCFilter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

import tbrugz.queryon.AbstractHttpServlet;

/*
 * https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-web-applications.embedded-container.servlets-filters-listeners
 */
//@SpringBootApplication
public class QOnSpringBootApp extends SpringBootServletInitializer {

	protected static final boolean loadQueryOnServletOnStartup = true;
	
	public static void main(String[] args) {
		SpringApplication.run(QOnSpringBootApp.class, args);
	}

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
	
	public static String getSystemTempDir() {
		return System.getProperty("java.io.tmpdir");
	}

	// https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#howto-create-a-deployable-war-file
	// https://howtodoinjava.com/spring-boot/spring-boot-jsp-view-example/
	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(QOnSpringBootApp.class);
	}

	/*
	@Bean
	public DataSourceConfig getDataSourceBean() {
		return new DataSourceConfig();
	}
	*/

	/*@Bean
	@ConfigurationProperties(prefix = "spring.datasource")
	public DataSource getDataSource() {
		DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
		return dataSourceBuilder.build();
	}*/

	// https://stackoverflow.com/questions/32394862/how-to-register-servletcontextlistener-in-spring-boot
	@Bean
	public ServletContextListener shiroListener() {
		return new EnvironmentLoaderListener();
	}
	
	// https://stackoverflow.com/a/20939923/616413
	// http://websystique.com/springmvc/spring-mvc-4-file-upload-example-using-multipartconfigelement/
	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletQueryOnBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.QueryOn();
		ServletRegistrationBean<AbstractHttpServlet> bean = new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping()); //"/q/*"
		bean.setMultipartConfig(new MultipartConfigElement(getSystemTempDir()));
		if(loadQueryOnServletOnStartup) {
			bean.setLoadOnStartup(1);
		}
		return bean;
	}

	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletInfoServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.api.InfoServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletAuthServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.auth.AuthServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletQueryOnInstantBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.QueryOnInstant();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletQueryOnSchemaInstantBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.QueryOnSchemaInstant();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}

	/*
	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletDiffServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.diff.DiffServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}
	*/

	/*
	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletDataDiffServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.diff.DataDiffServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}
	*/

	/*
	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletDiff2QServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.diff.Diff2QServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}
	*/

	/*
	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletDiffManyServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.diff.DiffManyServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}
	*/

	/*
	@Bean
	public ServletRegistrationBean servletCool303Bean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.Cool303RedirectionServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}
	*/

	/*
	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletProcessorServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.ProcessorServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}
	*/

	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletPagesServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.PagesServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletMarkdownServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.MarkdownServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletSwaggerServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.api.SwaggerServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletODataServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.api.ODataServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletGraphQlQonServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.graphql.GraphQlQonServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletQonSoapServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.soap.QonSoapServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}

	/*
	@Bean
	public ServletRegistrationBean<AbstractHttpServlet> servletWebDavServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.webdav.WebDavServlet();
		return new ServletRegistrationBean<AbstractHttpServlet>(servlet, servlet.getDefaultUrlMapping());
	}
	*/

	// https://stackoverflow.com/questions/19825946/how-to-add-a-filter-class-in-spring-boot/30658752#30658752

	/*
	// optional Keycloak filter...
	@Bean
	public FilterRegistrationBean<KeycloakOIDCFilter> keycloakFilterRegistration() {
		FilterRegistrationBean<KeycloakOIDCFilter> registration = new FilterRegistrationBean<KeycloakOIDCFilter>();
		registration.setFilter(new KeycloakOIDCFilter());
		registration.addUrlPatterns("/*");
		registration.setName("KeycloakOIDCFilter");
		registration.setOrder(10);
		return registration;
	}
	*/

	@Bean
	public FilterRegistrationBean<ShiroFilter> shiroFilterRegistration() {
		FilterRegistrationBean<ShiroFilter> registration = new FilterRegistrationBean<ShiroFilter>();
		registration.setFilter(new ShiroFilter());
		registration.addUrlPatterns("/*");
		registration.setName("ShiroFilter");
		registration.setOrder(20);
		return registration;
	}

	/*
	@Bean
	public FilterRegistrationBean<AccessLogFilter> accessLogFilterRegistration() {
		FilterRegistrationBean<AccessLogFilter> registration = new FilterRegistrationBean<AccessLogFilter>();
		registration.setFilter(new AccessLogFilter());
		//registration.addUrlPatterns("/*");
		registration.addServletNames("queryOn", "queryOnInstant", "queryOnSchemaInstant",
				"pagesServlet", "markdownServlet", "swaggerServlet",
				"ODataServlet", "graphQlQonServlet", "qonSoapServlet"
				//"webDavServlet"
				);
		//infoServlet, authServlet
		registration.setName("AccessLogFilter");
		registration.setOrder(100);
		return registration;
	}
	*/

}
