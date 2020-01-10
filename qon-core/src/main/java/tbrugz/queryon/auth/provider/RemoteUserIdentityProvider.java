package tbrugz.queryon.auth.provider;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class RemoteUserIdentityProvider implements IdentityProvider {

	static final Log log = LogFactory.getLog(RemoteUserIdentityProvider.class);

	static final String DEFAULT_AUTH_HTTPREALM = "httpRealm";
	
	static final String PROP_AUTH_HTTPREALM = "queryon.auth.http-realm";
	static final String PROP_AUTH_USERNAME_PATTERN = "queryon.auth.http-username-pattern";
	
	HttpServletRequest request;
	Properties prop;
	
	@Override
	public void setInfo(HttpServletRequest request, Properties prop) {
		this.request = request;
		this.prop = prop;
	}

	@Override
	public boolean isAuthenticated() {
		if(request!=null && request.getRemoteUser()!=null) {
			return true;
		}
		return false;
	}

	@Override
	public String getIdentity() {
		if(isAuthenticated()) {
			String userIdentity = request.getRemoteUser();
			String usernamePattern = prop.getProperty(PROP_AUTH_USERNAME_PATTERN);
			if(usernamePattern!=null) {
				Matcher m = Pattern.compile(usernamePattern).matcher(String.valueOf(userIdentity));
				if(m.find()) {
					try {
						userIdentity = m.group(1);
					}
					catch(IllegalStateException e) {
						log.warn("getSubject: Exception: "+e+" [pattern: "+usernamePattern+"]");
					}
					catch(IndexOutOfBoundsException e) {
						log.warn("getSubject: Exception: "+e+" [pattern: "+usernamePattern+"]");
					}
				}
				/*else {
					log.debug("userIdentity ["+userIdentity+"] does not match http pattern [pattern: "+usernamePattern+"]");
				}*/
			}
			return userIdentity;
		}
		return null;
	}

	@Override
	public String getRealm() {
		if(isAuthenticated()) {
			return prop.getProperty(PROP_AUTH_HTTPREALM, DEFAULT_AUTH_HTTPREALM);
		}
		return null;
	}

}
