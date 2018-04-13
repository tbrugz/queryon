
queryon-ng
----------
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
* datadiff: added 'fin:' & 'fnin:' param prefixes


queryon 0.6.1
----------
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
