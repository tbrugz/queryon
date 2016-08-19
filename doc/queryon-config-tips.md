
about limit, limit-default, limit-max
-------------------------------------

**Limit** is the maximum number of rows that may be returned for a given query.
Limit may be configured in the query api (parameter `limit=<n>`), in the query itself
(`/* limit-default=<n> */` & `/* limit-max=<m> */`), and in queryon properties
(`queryon.limit.default=<n>` & `queryon.limit.max=<m>`).

**limit-max** is the maximum number of rows that may be returned and takes precedence
over other limits. It is defined by `min(limit-max=<m>, queryon.limit.max=<m>)`, and
if those values arent defined the default is `RequestSpec.DEFAULT_LIMIT`.

**limit-default** will be used if the limit in the api call (`limit=<n>`) is not defined.
Query limit-default (`/* limit-default=<n> */`) takes precedence on queryon's properties
limit-default.

The limit in the api call (`limit=<n>`), if defined, takes precedence over 
**limit-default** but not over **limit-max**.
