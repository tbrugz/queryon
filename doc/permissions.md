
permissions
===========

This document describes the permissions needed to access each module or action in Queryon. The engine uses the Apache Shiro library to check the user permission. For more information on Shiro permissions, see [Understanding Permissions in Apache Shiro](https://shiro.apache.org/permissions.html).

* `[object-type]:STATUS:[object]`: user may query metadata from `[object]` (of `[object-type]`'s type)

* `[object-type]:<SELECT,INSERT,UPDATE,DELETE>:[object]`: user may SELECT/INSERT/UPDATE/DELETE data from the `[object]` object (of `[object-type]` type)

* `[executable-type]:EXECUTE:[executable]`: user may EXECUTE the `[executable]` object (of `[executable-type]` type)

* `PLUGIN:[plugin]`: user may use `[plugin]` plugin (Example plugins: `QOnQueries`, `QOnTables`, `QOnExecs`). Plugins may require aditional sub-permissions.

* `PROCESSOR:[processor]`: user may use `[processor]` processor (example processors: `JAXBSchemaXMLSerializer`, `JSONSchemaSerializer`, `SchemaModelScriptDumper`, `RS2GraphML`)

* `[object-type]:SHOW` - show object definition (DDL) *(see QueryOnSchema)*

* `[object-type]:APPLYDIFF:[model-id]` - apply diff of `[object-type]` to `[model-id]` *(see DiffServlet)*


special/administrative permissions
-----

Some permissions should be granted with care, and usually shoud be granted to developers or administrators only (and depending on the purpose of the Queryon instance, no one).

* `SELECT_ANY`: user may select data from any relation

* `VALIDATE_ANY`: user may validate any SQL statement

* `EXPLAIN_ANY`: user may issue an "explain query" for any SQL statement

* `SQL_ANY`: user may run any DDL / DML

* `MANAGE`: user may manage the Queryon instance (like re-igniting the instance). Sub-permissions:
  * `diffmodel` user may *diff* between in-memory schema model and database (Sub-sub-actions/permissions: `show`, `applydiff`)
  * `reload`


wildcards
-----

Wildcards may be used to select any or a group of objects in a single statement, like `*:STATUS`, `*:SELECT,INSERT` or `PROCESSOR:JAXBSchemaXMLSerializer,JSONSchemaSerializer`. This is a [Shiro feature](https://shiro.apache.org/permissions.html).


examples
-----

* `TABLE:*:QON_TABLES` - user may SELECT/INSERT/UPDATE/DELETE data from `QON_TABLES` table

* `VIEW:SELECT:*` - user may SELECT data from any view

* `PROCEDURE:EXECUTE` - user may execute any procedure

* `*:STATUS` - user may grab metadata from any object

* `*:SELECT` - user may SELECT data from any relation

* `*:SHOW` - user may query the definition/DDL of any object

* `MANAGE:diffmodel:show` - user may see what differences & DDL changes are between the in-memory schema model and the database
