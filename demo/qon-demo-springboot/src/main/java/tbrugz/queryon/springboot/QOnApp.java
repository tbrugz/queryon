package tbrugz.queryon.springboot;

import javax.servlet.ServletContextListener;

import org.apache.shiro.web.env.EnvironmentLoaderListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

import tbrugz.queryon.AbstractHttpServlet;

@SpringBootApplication
public class QOnApp extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(QOnApp.class, args);
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

	// https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#howto-create-a-deployable-war-file
	// https://howtodoinjava.com/spring-boot/spring-boot-jsp-view-example/
	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(QOnApp.class);
	}

	// https://stackoverflow.com/questions/32394862/how-to-register-servletcontextlistener-in-spring-boot
	@Bean
	public ServletContextListener shiroListener() {
		return new EnvironmentLoaderListener();
	}
	
	// https://stackoverflow.com/a/20939923/616413
	@Bean
	public ServletRegistrationBean servletQueryOnBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.QueryOn();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping()); //"/q/*"
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

	/*
	@Bean
	public ServletRegistrationBean servletCool303Bean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.Cool303RedirectionServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

	@Bean
	public ServletRegistrationBean servletProcessorServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.ProcessorServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}
	*/

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

	@Bean
	public ServletRegistrationBean servletWebDavServletBean() {
		AbstractHttpServlet servlet = new tbrugz.queryon.webdav.WebDavServlet();
		return new ServletRegistrationBean(servlet, servlet.getDefaultUrlMapping());
	}

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

}
