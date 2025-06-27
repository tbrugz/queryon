package tbrugz.queryon.springboot;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;

public class SpringBootUtils {

	static final Log log = LogFactory.getLog(SpringBootUtils.class);

	public static void showAppInfo(ApplicationContext ctx) {
		log.info("showAppInfo: appName = " + ctx.getId());
		log.info("showAppInfo: startup = " + new Date(ctx.getStartupDate()) );
			//" ; displayName = " + getApplicationContext().getDisplayName() +
			//" ; context = " + getApplicationContext() +
	}

	// see: https://www.baeldung.com/spring-show-all-beans
	public static void displayServletBeans(ApplicationContext ctx) {
		boolean showInfo = log.isInfoEnabled();
		if(showInfo) {
			String[] allBeanNames = ctx.getBeanDefinitionNames();
			log.info("Filters:");
			for(String beanName : allBeanNames) {
				Object bean = ctx.getBean(beanName);
				if(bean instanceof FilterRegistrationBean) {
					log.info("- " + beanName + ": "+bean);
				}
			}
			log.info("Servlets:");
			for(String beanName : allBeanNames) {
				Object bean = ctx.getBean(beanName);
				//log.info(beanName + ": "+bean.getClass());
				if(bean instanceof ServletRegistrationBean) {
					log.info("- " + beanName + ": "+bean);
				}
			}
		}
	}

}
