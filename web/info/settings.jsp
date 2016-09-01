<%@page import="tbrugz.queryon.PagesServlet"%>
<%@page import="tbrugz.queryon.processor.QOnExecs"%>
<%@page import="tbrugz.sqldump.util.StringDecorator.StringQuoterDecorator"%>
<%@page import="tbrugz.queryon.processor.QOnTables"%>
<%@page import="java.util.*, tbrugz.queryon.QueryOn"%>
{
<%
	StringQuoterDecorator sqd = new StringQuoterDecorator("\"");
	String[] exposedKeys = {
			"queryon.models",
			"queryon.models.default",
			"queryon.qon-tables.table",
			"queryon.qon-execs.table",
			"queryon.qon-pages.table", //PagesServlet.PROP_PREFIX+PagesServlet.SUFFIX_TABLE
			"queryon.web.auth-required",
			"queryon.web.appname",
		};
	//XXX: test if 'queryon.update-plugins' contains qon-tables and/or qon-execs
	String[] defaultValues = { null, null, QOnTables.DEFAULT_TABLES_TABLE, QOnExecs.DEFAULT_EXECS_TABLE, PagesServlet.DEFAULT_PAGES_TABLE,
			null, null
			};
	
	int i = 0;
	Properties prop = (Properties) application.getAttribute(QueryOn.ATTR_PROP);
	if(prop!=null) {
	for(;i<exposedKeys.length;) {
		String k = exposedKeys[i];
		if(i>0) { out.write(",\n"); }
		out.write(sqd.get(k)+": "+sqd.get(prop.getProperty(k, defaultValues[i])));
		i++;
	}
	}
	
	/*InputStream inputStream = application.getResourceAsStream("/META-INF/MANIFEST.MF");
	Manifest mf = new Manifest(inputStream);
	Attributes atts = mf.getMainAttributes();
	out.write("Implementation-Version: " + atts.getValue("Implementation-Version"));
	out.write("Implementation-Build: " + atts.getValue("Implementation-Build"));*/

	Properties p2 = new Properties();
	p2.load(application.getResourceAsStream("/WEB-INF/classes/queryon-version.properties"));
	//p2.load(application.getResourceAsStream("/queryon-version.properties"));
	for(Map.Entry<Object, Object> entry: p2.entrySet()) {
		if(i>0) { out.write(",\n"); }
		out.write(sqd.get((String)entry.getKey())+": "+sqd.get((String)entry.getValue()));
		i++;
	}
%>
}
