
demo-minimal
============

A minimal QueryOn demo using [H2 database](http://www.h2database.com/)


building
-------

* install queryon: 
```
cd ../..
mvn install -DskipTests
```

* build demo-minimal: 
```
cd demo/qon-demo-minimal
mvn package
```

* init h2 database:
```
ant run-sqlrun
ant run-sqldiff
```

running
-------

```
mvn jetty:run
```

navigate to `http://localhost:8888`


usage examples
--------------

Go to `menu > query editor` or click `new` to create a new query.

* Hello world - `select 'Hello World!' as message`

`validate` (, `explain`), `run` & `save` (with a name) the query. Then go back to index, select the query and run (`go!`).

* Hello world with param - `select 'Hello '||? as message`

Same as before, but now when you select the query the UI asks for a parameter.

* Metadata - `select * from INFORMATION_SCHEMA.TABLES`

Will show some metadata from H2.

* A row generator query with numeric functions 

```
WITH InfiniteRows (RowNumber) AS (
   -- Anchor member definition
   SELECT 1 AS RowNumber
   UNION ALL
   -- Recursive member definition
   SELECT a.RowNumber + 1 AS RowNumber
   FROM   InfiniteRows a
   WHERE  a.RowNumber < 100
)
-- Statement that executes the CTE
SELECT RowNumber*5 as row, RowNumber*(5+sin(cast(RowNumber AS DOUBLE)/5)) as row_sin,
    rownumber*5*(rand()+0.5) as row_rand
FROM InfiniteRows
```
ref: http://www.codeproject.com/Tips/811875/Generating-Desired-Amount-of-Rows-in-SQL-using-CTE

After creating the query, go to one of the **charts** pages then select the query and select the
rows to show: `ROW, ROW_SIN, ROW_RAND` ; then, `go!`

* A [random walk](https://en.wikipedia.org/wiki/Random_walk)

```
WITH cte (rnum,c1,c2,c3,c4,c5) AS (
   SELECT 1 as rnum, 1 AS c1, 1 AS c2, 1 AS c3, 1 as c4, 1 as c5
   UNION ALL
   SELECT rnum + 1 as rnum, a.c1 - 0.5 + rand() AS c1, a.c2 - 0.5 + rand() AS c2, a.c3 - 0.5 + rand() AS c3, a.c4 - 0.5 + rand() AS c4, a.c5 - 0.5 + rand() AS c5
   FROM   cte a
   WHERE  a.rnum < 100
)
SELECT rnum, c1, c2, c3, c4, c5
FROM cte
```

Then plot columns `C1, C2, C3, C4, C5`

* A graph with circle shape

```
WITH cte (rnum) AS (
   SELECT 0 as rnum
   UNION ALL
   SELECT rnum + 1 as rnum
   FROM   cte a
   WHERE  a.rnum < 9
)
SELECT cast(rnum as int) as source, (rnum+1)%10 as target,
    'Node '||rnum as source_label, 'Node '||(rnum+1)%10 as target_label,
    '#'||rnum||rnum||'0000' as source_color, '#'||(rnum+1)%10||(rnum+1)%10||'0000' as target_color,
    rnum%2+1 as source_size, (rnum+1)%2+1 as target_size
FROM cte
```

After creating the query, go to the **graphs** page then select the query and select the
rows to show: `ROW, ROW_SIN, ROW_RAND` ; then, `go!` ; then, to create the circle, click on `start force layout`
