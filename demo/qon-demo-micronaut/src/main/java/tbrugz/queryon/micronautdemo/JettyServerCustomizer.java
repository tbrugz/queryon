import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;

import jakarta.inject.Singleton;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/*
 * see:
 * https://micronaut-projects.github.io/micronaut-servlet/latest/guide/#jetty
 * https://stackoverflow.com/questions/31923810/error-multiple-servlets-map-to-path-in-embedded-jetty-with-jerseyservlet
 */
@Singleton
public class JettyServerCustomizer implements BeanCreatedEventListener<Server> {

    @Override
    public Server onCreated(BeanCreatedEvent<Server> event) {
        Server jettyServer = event.getBean();
        // perform customizations...

        // Create the ResourceHandler. It is the object that will actually handle the request for a given file. It is
        // a Jetty Handler object so it is suitable for chaining with other handlers as you will see in other examples.
        ResourceHandler resource_handler = new ResourceHandler();
        // Configure the ResourceHandler. Setting the resource base indicates where the files should be served out of.
        // In this example it is the current directory but it can be configured to anything that the jvm has access to.
        resource_handler.setDirectoriesListed(true);
        resource_handler.setWelcomeFiles(new String[]{ "./html/index.html" });
        resource_handler.setResourceBase(".");

        //Jersey ServletContextHandler
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ServletHolder jerseyServlet = servletContextHandler.addServlet(tbrugz.queryon.QueryOn.class, "/q/*");
        jerseyServlet.setInitOrder(0);
        //jerseyServlet.setInitParameter("jersey.config.server.provider.classnames", EntryPoint.class.getCanonicalName());

        // Add the ResourceHandler to the server.
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { servletContextHandler, new DefaultHandler() });
        jettyServer.setHandler(handlers);

        return jettyServer;
    }

}
