/*
 * License: http://www.apache.org/licenses/LICENSE-2.0
 */
package tbrugz.queryon.shiro;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.activedirectory.ActiveDirectoryRealm;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;
import org.apache.shiro.subject.PrincipalCollection;

public class QOnActiveDirectoryRealm extends ActiveDirectoryRealm implements AuthorizationInfoInformer {

    private static final Log log = LogFactory.getLog(QOnActiveDirectoryRealm.class);
    
    private String searchFilter = "(&(objectClass=*)(userPrincipalName={0}))";
    
    private String groupRolesRegexMapper;

    private String regexMapperRolePrefix;
    
    private String regexMapperRoleSuffix;
	
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
	
	public String getRegexMapperRolePrefix() {
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
	}

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

    private Set<String> getRoleNamesForUser(String username, LdapContext ldapContext) throws NamingException {
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

        NamingEnumeration<SearchResult> answer = ldapContext.search(searchBase, searchFilter, searchArguments, searchCtls);
        
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
                roleNames.add( (regexMapperRolePrefix!=null?regexMapperRolePrefix:"")
                        +match
                        +(regexMapperRoleSuffix!=null?regexMapperRolePrefix:"") );
            }
        }
        return roleNames;
    }
    

}
