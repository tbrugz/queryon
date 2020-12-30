<%@page import="tbrugz.queryon.util.QOnContextUtils"%>
<%@page import="java.util.regex.Pattern"%>
<%@page import="tbrugz.queryon.exception.InternalServerException"%>
<%@page import="tbrugz.queryon.util.DumpSyntaxUtils"%>
<%@page import="tbrugz.queryon.processor.QOnQueriesProcessor"%>
<%@page import="tbrugz.queryon.processor.QOnQueries"%>
<%@page import="tbrugz.queryon.ResponseSpec"%>
<%@page import="tbrugz.queryon.PagesServlet"%>
<%@page import="tbrugz.queryon.processor.QOnExecs"%>
<%@page import="tbrugz.sqldump.util.StringDecorator.StringQuoterDecorator"%>
<%@page import="tbrugz.queryon.processor.QOnTables"%>
<%@page import="java.util.*, tbrugz.queryon.QueryOn"%>
<%@page import="com.google.gson.*"%>
{
<%
	response.setContentType(ResponseSpec.MIME_TYPE_JSON);
	Gson gson = new Gson();

	StringQuoterDecorator sqd = new StringQuoterDecorator("\"");
	String[] exposedKeys = {
			"queryon.models",
			"queryon.models.default",
			
			"queryon.qon-queries.table",
			"queryon.qon-tables.table",
			"queryon.qon-execs.table",
			"queryon.qon-pages.table", //PagesServlet.PROP_PREFIX+PagesServlet.SUFFIX_TABLE

			"queryon.filter.allowed",
			"queryon.groupby.allow",
			
			"queryon.web.auth-required",
			"queryon.web.appname",
			"queryon.web.login.show",
			"queryon.web.login-message",

			//"sqldump.datadump.htmlx.dateformat",
			//"sqldump.datadump.json.dateformat",
	};
	
	Pattern[] exposedPatterns = {
		Pattern.compile("queryon\\.qon-(?:queries|tables|execs|pages)@[\\w]+\\.table"),
	};

	//XXX: test if 'queryon.update-plugins' contains qon-tables and/or qon-execs
	String[] defaultValues = { null, null,
			QOnQueriesProcessor.DEFAULT_QUERIES_TABLE, QOnTables.DEFAULT_TABLES_TABLE, QOnExecs.DEFAULT_EXECS_TABLE, PagesServlet.DEFAULT_PAGES_TABLE,
			null, null,
			null, null, "true", null,
			//, null, null
			};
	
	if(exposedKeys.length!=defaultValues.length) {
		throw new InternalServerException("exposedKeys.length ["+exposedKeys.length+"] != defaultValues.length ["+defaultValues.length+"]");
	}
	
	int i = 0;
	Properties prop = QOnContextUtils.getProperties(application);
	if(prop!=null) {
		for(;i<exposedKeys.length;) {
			String k = exposedKeys[i];
			if(i>0) { out.write(",\n"); }
			out.write(sqd.get(k)+": "+gson.toJson(prop.getProperty(k, defaultValues[i])));
			i++;
		}
		for(int j=0;j<exposedPatterns.length;j++) {
			Pattern p = exposedPatterns[j];
			for(Object k: prop.keySet()) {
				String key = (String) k;
				if(p.matcher(key).matches()) {
					if(i>0) { out.write(",\n"); }
					out.write(sqd.get(key)+": "+gson.toJson(prop.getProperty(key)));
					i++;
				}
			}
		}
	}
	
	//syntaxes
	DumpSyntaxUtils dsutils = QOnContextUtils.getDumpSyntaxUtils(application);
	if(dsutils!=null) {
		if(i>0) { out.write(",\n"); }
		out.write(sqd.get("syntax.fileextensions") + ": " + gson.toJson( dsutils.syntaxExtensions ) );
		i++;
	}
	
	/*InputStream inputStream = application.getResourceAsStream("/META-INF/MANIFEST.MF");
	Manifest mf = new Manifest(inputStream);
	Attributes atts = mf.getMainAttributes();
	out.write("Implementation-Version: " + atts.getValue("Implementation-Version"));
	out.write("Implementation-Build: " + atts.getValue("Implementation-Build"));*/

	Map<String, String> p2 = new TreeMap<String, String>();
	//p2.load(application.getResourceAsStream("/WEB-INF/classes/queryon-version.properties"));

	try {
		Properties pqon = new Properties();
		pqon.load(QueryOn.class.getResourceAsStream("/queryon-version.properties"));
		for(Map.Entry<Object, Object> entry: pqon.entrySet()) {
			p2.put("queryon."+entry.getKey(), String.valueOf(entry.getValue()) );
		}
	}
	catch(RuntimeException e) {} 
	
	try {
		Properties psqld = new Properties();
		psqld.load(QueryOn.class.getResourceAsStream("/sqldump-version.properties"));
		for(Map.Entry<Object, Object> entry: psqld.entrySet()) {
			p2.put("sqldump."+entry.getKey(), String.valueOf(entry.getValue()) );
		}
	}
	catch(RuntimeException e) {} 
	
	//p2.load(application.getResourceAsStream("/queryon-version.properties"));
	for(Map.Entry<String, String> entry: p2.entrySet()) {
		if(i>0) { out.write(",\n"); }
		out.write(sqd.get((String)entry.getKey())+": "+sqd.get((String)entry.getValue()));
		i++;
	}
%>
}
