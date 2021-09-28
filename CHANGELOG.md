
queryon 0.7 [2021-09-26]
-----------
* refactoring: modules 'qon-core', 'qon-web', 'demo-minimal', 'demo-dbn'
* api: added 'graphql' module
* qon: added BaseApiServlet
* web: added qon-graphql dependency & graphiql.html
* queryon: added HEAD method [2018-07-16]
* graphql: added mutation (execute)
* api: swagger: added responses
* queryon: SQL/query: using 'namedParameterNames', doStatus: using UnionResultSet
* queryon: SQL: added 'bind-null-on-missing-parameters' special construct
* api: added 'soap' module [2018-11-11]
* processor: added SQLQueriesLoader
* queryon: added warning headers 'SQL-Position' & 'SQL-Line'
* updateplugin: multi-model mode added ; QOnExecs/QOnQueries/QOnTables changed
* queryon/graphql: added BeanActions
* api: odata: added beans/singleton queries, added 'currentUser' query
* syntax: htmlx: added support for 'breaks'
* demo: added 'qon-demo-static'
* auth: added AuthServlet
* api: added 'webdav' methods
* demo: added 'qon-demo-pg' [2019-11-20]
* demo: added 'qon-demo-anydb'
* demo: qon-demo-pg: added thorntail profile & Dockerfile
* auth: added 'qon-auth-keycloak' module (keycloak/shiro integration)
* web: pivot: add 'drillthrough' action [2020-02-06]
* demo: refactoring/moving demos to '/demo'
* build/pom: updates for java11
* web: workspace: 'download' with many syntaxes
* web: using swagger-ui v2 & v3 [2020-07-28]
* datadiff: filters: added 'gt', 'ge', 'lt' & 'le' filters + 'null' & 'notnull' filters
* doc: added BUILDING.md, updated README (moved to github.com)
* web: added qon-editor.html
* api: added InfoServlet (from qon-web: info/*.jsp)
* demo: added 'qon-demo-springboot'
* filter: add AccessLogFilter [2021-03-07]
* web: workspace: added 'updatemax' input parameter
* soap: beanQuery support
* queryon: added DiffProcessor ; QOnManage: added 'applydiff' subaction
* queryon: added support for 'Range requests' (blobs)
* queryon: SQL: added 'distinct-count' aggregate
* queryon: SQL: added named parameters (":param" syntax) [2021-07-08]
* web: intercepting inner *_HREF links


queryon 0.6.2 [2018-06-07]
-------------
* web: added "web" props: '.auth-required' & '.appname' (900)
* datadiff: multi-table support
* datadiff: added 'ignorecol' & 'altuk' params
* datadiff: added 'dmlops' param
* web: map: added updateFragment() & onLoadUpdateUiComponents() (945)
* queryon: syntax: added WebSyntax
* queryon: syntax: added AtomSyntax
* datadiff: added 'limit' parameter ; diff2q: also added 'dmlops'
* queryon: added prop 'sql.use-id-decorator'
* queryon: QOnQueries/pages: added audit (created|updated + at|by)
* queryon: insert/update: allow date/timestamp
* api: added SwaggerServlet (989)
* api: swagger: added params: fields, distinct, order, uni/multi filters
* build: added 'download-deps' (ant) to 'generate-resources' maven phase
* api: swagger: added insert/update/delete operations
* api: swagger: added execute (POST) operation
* queryon: execute: added type SCRIPT
* queryon: added 'count' parameter
* queryon: select: added pivot query (params: oncols/onrows) (1037)
* queryon: added 'pivotflags' parameter
* queryon: added 'measures' parameter (pivot)
* queryon: added optimistic-lock
* queryon: added 'utf8' parameter
* web: added workspace.html (experimental)
* filter: added CacheControlFilter (uses parameter 'cache-max-age')
* processor(s): added warnings to servlet context
* api: added ODataServlet (1105)
* api: odata: added ODataRequest, $top & $skip parameters
* api: odata: added $orderby parameter
* api: odata: added $select parameter
* api: odata: added $value parameter
* api: odata: added $count parameter
* queryon: added 'aliases' parameter
* api: odata: add $metadata
* queryon: added 'groupby' parameter
* queryon: added 'agg' (aggregate) parameter
* web: index: pivot support
* web: sigma: added optionals EDGE_SIZE & EDGE_COLOR
* queryon: SQL: added 'named-parameters' "special construct"
* queryon: executables now accept binary parameters
* queryon: added props 'queryon.filter.allowed' & 'queryon.groupby.allow'
* queryon: added prop 'queryon.distinct.allow'
* api: odata: added $filter parameter/parser (1230)
* api: odata: added create/remove/update 
* datadiff: added 'fin:' & 'fnin:' param prefixes (1241)


queryon 0.6.1 [2016-08-29]
-------------
* build: added status.jsp & build.revisionNumber
* web: blob dump ui
* web: added map ui (mapproc)
* added DumpSyntax parameters/properties
* added json & csv parameters
* added menu.html
* added qon-editor help doc & markdown parser
* datadiff: added Diff2QServlet
* diff: added DiffManyServlet (579)
* added ExplainAny action (explain plan)
* execute: added 'X-Execute-ReturnCount' & 'X-Warning' headers
* added QOnExecs processor & go.html (624)
* added UpdatePlugin
* select: added 'X-Warning-UnknownColumn' header
* added ResponseSpec
* web: charts: added nvd3chart.html
* demo: added qon-demo-minimal & qon-demo-dbn
* added PagesServlet (788)
* added MarkdownServlet
* diff: added pre/post apply hooks, added ShellHook
* allow multipart/formdata - file/blob uploads / servlet 3.0
* added WebProcessor
* added RS2GraphML (WebProcessor)
* execute: allow 'script' execution
* added $username variable to queries/executable scripts (887)
* SQL: added 'limit-default' integer "special construct"


queryon 0.6.0
-------------
* added ProcessorServlet
* processor: added QOnQueries
* syntaxes: added HTMLAttrSyntax
* added prop 'queryon.processors-on-startup'
* added query editor (qon_queries.jsp)
* added SELECT_ANY & VALIDATE_ANY actions
* web: added download, permalink links, filter & order by
* added shiro security & login pages
* processor: QOnQueries: added prop suffixes '.limit.[insert|update].exact'
* added CorsFilter (299)
* web: added sigma.js graph
* web: added autocomplete (typeahead.js) on filter dialog
* added 'distinct' parameter
* added sqlcmd (Sql Commands) - query editor
* added "like" filter
* added QueryOnSchema servlet (345)
* added multi-model mode
* web: added ddl & diff views
* added 'instant' QueryOn & QueryOnSchema servlets
* added DiffServlet & DataDiffServlet
* filter: added 'nin - not in', 'nlk - not like'
* filter: added 'ne', 'gt', 'ge', 'lt' & 'le' filters
* added 'roles' permissions to queries
* web: added d3 charts
* web: added insert, update & delete rows
* added blob-dump
* sqlcmd: added importedkeys & exportedkeys
* validate: added ResultSetMetadata2RsAdapter
* processor: added QOnTables
* web: added navigation buttons (499)
* sqlcmd: added metadata


queryon 0.5.0
-------------
* initial release
