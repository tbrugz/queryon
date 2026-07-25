package tbrugz.queryon.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SessionLogFilter implements Filter {

    static final Log log = LogFactory.getLog(SessionLogFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest) {
            chain.doFilter(new SessionAuditRequestWrapper((HttpServletRequest) request), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {}

    static class SessionAuditRequestWrapper extends HttpServletRequestWrapper {
        
        public SessionAuditRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public HttpSession getSession() {
            return getSession(true);
        }

        @Override
        public HttpSession getSession(boolean create) {
            boolean doCreate = false;
            if (create) {
                // Only log if a session is actively being forced/created
                HttpSession currentSession = super.getSession(false);
                if (currentSession == null) {
                    doCreate = true;
                    logSessionCreation();
                }
                else {
                    //log.debug("session exists... [currentSession: id="+currentSession.getId()+" ; time="+currentSession.getCreationTime()+"]");
                }
            }
            HttpSession newSession = super.getSession(create);
            if(doCreate) {
                log.warn("created new session... [newSession: id="+newSession.getId()+" ; time="+newSession.getCreationTime()+"]");
            }
            return newSession;
        }

        private void logSessionCreation() {
            Exception stackTracer = new Exception("Session creation detected!");
            log.warn("session being created... [pathInfo: "+getPathInfo()+" ; method: "+getMethod()+"]", stackTracer);
        }
    }

}
