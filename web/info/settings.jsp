<%@page import="tbrugz.sqldump.util.StringDecorator.StringQuoterDecorator"%>
<%@page import="tbrugz.queryon.processor.QOnTables"%>
<%@page import="java.util.*, tbrugz.queryon.QueryOn"%>
{
<%
	StringQuoterDecorator sqd = new StringQuoterDecorator("\"");
	String[] exposedKeys = {"queryon.models","queryon.models.default","queryon.qon-tables.table"};
	String[] defaultValues = {null, null, QOnTables.DEFAULT_TABLES_TABLE};
	
	Properties prop = (Properties) application.getAttribute(QueryOn.ATTR_PROP);
	for(int i=0;i<exposedKeys.length;i++) {
		String k = exposedKeys[i];
		if(i>0) { out.write(",\n"); }
		out.write(sqd.get(k)+": "+sqd.get(prop.getProperty(k, defaultValues[i])));
	}
%>
}