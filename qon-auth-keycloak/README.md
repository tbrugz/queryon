
qon-auth-keycloak
=================

Optional module to integrate Keycloak (Java servlet filter adapter) with Queryon/Shiro.

Note about Keycloak Servlet Filter deprecation:
<https://www.keycloak.org/2022/02/adapter-deprecation>.

Source for Keycloak Servlet Filter:
<https://github.com/keycloak/keycloak/tree/archive/release/24.0/adapters/oidc/servlet-filter>.


adding Keycloak integration
-----

### 1. In `web.xml`, declare Keycloak and Shiro filters, like this:

```xml
	<filter>
		<filter-name>KeycloakFilter</filter-name>
		<filter-class>org.keycloak.adapters.servlet.KeycloakOIDCFilter</filter-class>
	</filter>
	
	<filter-mapping>
		<filter-name>KeycloakFilter</filter-name>
		<url-pattern>/*</url-pattern>
	</filter-mapping>

	<filter>
		<filter-name>ShiroFilter</filter-name>
		<filter-class>org.apache.shiro.web.servlet.ShiroFilter</filter-class>
	</filter>

	<filter-mapping>
		<filter-name>ShiroFilter</filter-name>
		<url-pattern>/*</url-pattern>
		<dispatcher>REQUEST</dispatcher> 
		<dispatcher>FORWARD</dispatcher> 
		<dispatcher>INCLUDE</dispatcher> 
		<dispatcher>ERROR</dispatcher>
	</filter-mapping>
```


### 2. Add a `keycloak.json` to your `WEB-INF` dir - something like this:

```json
{
    "realm": "xxx",
    "auth-server-url": "https://example.com/auth",
    "ssl-required": "external",
    "resource": "xxx",
    "public-client": true,
    "confidential-port": 0
}
```


### 3a. In `shiro.ini`, add:

```ini
[main]
keycloakFilter = tbrugz.queryon.shiro.KeycloakAuthFilter
# ...

[urls]
/** = keycloakFilter
```

**OR**


### 3a. In `queryon.properties`, add `KeycloakIdentityProvider` as identity provider:

```properties
queryon.auth.identity-providers=tbrugz.queryon.auth.provider.KeycloakIdentityProvider
```


### 4. In `shiro.ini`, add (optional, for using keycloak roles):

```ini
[main]
keycloakRealm = tbrugz.queryon.shiro.KeycloakShiroRealm

securityManager.realms = $keycloakRealm, <...>
```


references
-----

https://www.keycloak.org/docs/latest/securing_apps/#_servlet_filter_adapter
