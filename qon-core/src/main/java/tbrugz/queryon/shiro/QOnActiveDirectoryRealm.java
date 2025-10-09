/*
 * License: http://www.apache.org/licenses/LICENSE-2.0
 */
package tbrugz.queryon.shiro;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.activedirectory.ActiveDirectoryRealm;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;
import org.apache.shiro.subject.PrincipalCollection;

/*
 * User Naming Attributes
 * https://msdn.microsoft.com/en-us/library/windows/desktop/ms677605(v=vs.85).aspx
 * 
 * SAM-Account-Name attribute
 * https://msdn.microsoft.com/en-us/library/windows/desktop/ms679635(v=vs.85).aspx
 * 
 * https://github.com/apache/shiro/search?utf8=%E2%9C%93&q=DefaultLdapContextFactory&type=Code
 * https://github.com/apache/shiro/blob/b74c1a7aa8df8323533d2fc9ab80273f860b6b14/core/src/main/java/org/apache/shiro/realm/ldap/DefaultLdapContextFactory.java
 * https://github.com/apache/shiro/blob/b74c1a7aa8df8323533d2fc9ab80273f860b6b14/core/src/main/java/org/apache/shiro/realm/ldap/AbstractLdapRealm.java
 * https://github.com/apache/shiro/blob/b74c1a7aa8df8323533d2fc9ab80273f860b6b14/core/src/main/java/org/apache/shiro/realm/activedirectory/ActiveDirectoryRealm.java
 */
public class QOnActiveDirectoryRealm extends ActiveDirectoryRealm implements AuthorizationInfoInformer {

    private static final Log log = LogFactory.getLog(QOnActiveDirectoryRealm.class);
    
    private static final String DEFAULT_SEARCH_FILTER = "(&(objectClass=*)(userPrincipalName={0}))";
    
    private String searchFilter = DEFAULT_SEARCH_FILTER;
    
    private String groupRolesRegexMapper;

    /*private String regexMapperRolePrefix;
    
    private String regexMapperRoleSuffix;*/

	/*
	 * used in 'getRoleNamesForUser'
	 */
	public String getSearchFilter() {
		return searchFilter;
	}

	public void setSearchFilter(String searchFilter) {
		this.searchFilter = searchFilter;
	}

	public String getGroupRolesRegexMapper() {
		return groupRolesRegexMapper;
	}

	public void setGroupRolesRegexMapper(String groupRolesRegexMapper) {
		this.groupRolesRegexMapper = groupRolesRegexMapper;
	}
	
	/*public String getRegexMapperRolePrefix() {
		return regexMapperRolePrefix;
	}

	public void setRegexMapperRolePrefix(String regexMapperRolePrefix) {
		this.regexMapperRolePrefix = regexMapperRolePrefix;
	}

	public String getRegexMapperRoleSuffix() {
		return regexMapperRoleSuffix;
	}

	public void setRegexMapperRoleSuffix(String regexMapperRoleSuffix) {
		this.regexMapperRoleSuffix = regexMapperRoleSuffix;
	}*/

    @Override
    protected AuthorizationInfo queryForAuthorizationInfo(PrincipalCollection principals, LdapContextFactory ldapContextFactory) throws NamingException {

        String username = (String) getAvailablePrincipal(principals);

        // Perform context search
        LdapContext ldapContext = ldapContextFactory.getSystemLdapContext();

        Set<String> roleNames;

        try {
            roleNames = getRoleNamesForUser(username, ldapContext);
        } finally {
            LdapUtils.closeContext(ldapContext);
        }

        return buildAuthorizationInfo(roleNames);
    }

    @Override
    protected Set<String> getRoleNamesForUser(String username, LdapContext ldapContext) throws NamingException {
        Set<String> roleNames;
        roleNames = new LinkedHashSet<String>();

        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        String userPrincipalName = username;
        //XXX: property for using (or not) principalSuffix
        /*if (principalSuffix != null) {
            userPrincipalName += principalSuffix;
        }*/

        //SHIRO-115 - prevent potential code injection:
        //String searchFilter = "(&(objectClass=*)(userPrincipalName={0}))";
        //String searchFilter = "(&(objectClass=*)(sAMAccountName={0}))";
        //String searchFilter = "(&(objectClass=*)(mail={0}))";
        Object[] searchArguments = new Object[]{userPrincipalName};

        //log.info("searchBase: "+searchBase+" searchFilter: "+searchFilter+" userPrincipalName: "+userPrincipalName);
        NamingEnumeration<SearchResult> answer = null;
        try {
            answer = ldapContext.search(searchBase, searchFilter, searchArguments, searchCtls);
        }
        catch(RuntimeException e) {
            log.warn("[runtime-exception] ldapContext.search: "+e,e);
            throw e;
        }
        catch(NamingException e) {
            log.warn("[naming-exception] ldapContext.search: "+e,e);
            throw e;
        }
        
        Pattern groupRolesRegexPattern = null;
        // "CN=(.+?),CN=Users,DC=example,DC=com"
        // "ad_", ""
        if(groupRolesRegexMapper!=null) {
            groupRolesRegexPattern = Pattern.compile(groupRolesRegexMapper);
        }

        while (answer.hasMoreElements()) {
            SearchResult sr = (SearchResult) answer.next();

            if (log.isDebugEnabled()) {
                log.debug("Retrieving group names for user [" + sr.getName() + "]");
            }

            Attributes attrs = sr.getAttributes();

            if (attrs != null) {
                NamingEnumeration<? extends Attribute> ae = attrs.getAll();
                while (ae.hasMore()) {
                    Attribute attr = (Attribute) ae.next();

                    if (attr.getID().equals("memberOf")) {

                        Collection<String> groupNames = LdapUtils.getAllAttributeValues(attr);

                        if (log.isDebugEnabled()) {
                            log.debug("Groups found for user [" + username + "]: " + groupNames);
                        }
                        //"CN=IT,CN=Users,DC=example,DC=com"

                        Collection<String> rolesForGroups = getRoleNamesForGroups(groupNames);
                        roleNames.addAll(rolesForGroups);
                        
                        if(groupRolesRegexPattern!=null) {
	                        Collection<String> rolesForRegex = getRoleNamesFromGroupNamesRegex(groupNames, groupRolesRegexPattern);
	                        roleNames.addAll(rolesForRegex);
                        }
                    }
                }
            }
        }
        return roleNames;
    }
    
    /**
     * Making 'getAuthorizationInfo' public ;)
     */
    @Override
    public AuthorizationInfo getAuthorizationInfo(PrincipalCollection principals) {
        return super.getAuthorizationInfo(principals);
    }
    
    protected Collection<String> getRoleNamesFromGroupNamesRegex(Collection<String> groupNames, Pattern pattern) {
        Set<String> roleNames = new HashSet<String>(groupNames.size());

        for (String groupName : groupNames) {
            Matcher matcher = pattern.matcher(groupName);
            if(matcher.matches()) {
                int matchGroup = matcher.groupCount()>0?1:0;
                String match = matcher.group(matchGroup);
                roleNames.add( //(regexMapperRolePrefix!=null?regexMapperRolePrefix:"")+
                        match
                        //+(regexMapperRoleSuffix!=null?regexMapperRolePrefix:"")
                        );
            }
        }
        return roleNames;
    }
    
    /*
     * see: ActiveDirectoryRealm.queryForAuthenticationInfo()
     */
    @Override
    protected AuthenticationInfo queryForAuthenticationInfo(AuthenticationToken token, LdapContextFactory ldapContextFactory)
            throws NamingException {

        UsernamePasswordToken upToken = (UsernamePasswordToken) token;

        // Binds using the username and password provided by the user.
        LdapContext ctx = null;
        try {
            ctx = ldapContextFactory.getLdapContext(getUsernameWithSuffix(upToken.getUsername()), String.valueOf(upToken.getPassword()));
        } finally {
            LdapUtils.closeContext(ctx);
        }

        return buildAuthenticationInfo(upToken.getUsername(), upToken.getPassword());
    }

    /*
    @Override
    protected AuthenticationInfo queryForAuthenticationInfo(
    		AuthenticationToken token, LdapContextFactory ldapContextFactory)
    		throws NamingException {
    	try {
    		UsernamePasswordToken upToken = (UsernamePasswordToken) token;
    		log.info("user: "+upToken.getUsername()+" rememberMe:"+upToken.isRememberMe());
    		return super.queryForAuthenticationInfo(token, ldapContextFactory);
    	}
        catch(RuntimeException e) {
            log.warn("[runtime-exception] queryForAuthenticationInfo: "+e,e);
            throw e;
        }
        catch(NamingException e) {
            log.warn("[naming-exception] queryForAuthenticationInfo: "+e,e);
            throw e;
        }
    }
    */

    protected String getUsernameWithSuffix(String username) {
        if (principalSuffix != null
                && !username.toLowerCase(Locale.ROOT).endsWith(principalSuffix.toLowerCase(Locale.ROOT))) {
            return username += principalSuffix;
        }
        return username;
    }

    public String getPrincipalSuffix() {
        return principalSuffix;
    }

    public void setPrincipalSuffix(String s) {
        this.principalSuffix = s;
    }

}
