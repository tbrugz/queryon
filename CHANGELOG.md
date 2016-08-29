
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
