<%@page import="org.apache.commons.logging.*"
%><%@page import="java.sql.*"
%><%@page import="java.util.*"
%><%@page import="tbrugz.sqldump.util.*"
%><%@page import="tbrugz.sqldump.dbmodel.*"
%><%@page import="tbrugz.sqldump.util.StringDecorator.StringQuoterDecorator"
%><%@page import="tbrugz.queryon.*"
%><%@page import="tbrugz.queryon.SchemaModelUtils"
%><%!
public String normalize(String s) {
	if(s==null) return "";
	return s;
}
%><%
	final Log log = LogFactory.getLog(this.getClass());

	StringQuoterDecorator sqd = new StringQuoterDecorator("\"");
	String modelId = SchemaModelUtils.getModelId(request);
	
	SchemaModel sm = SchemaModelUtils.getModel(application, modelId);
	Set<String> names = new TreeSet<String>();
	if(sm!=null) {
	Set<Table> ts = sm.getTables();
	for(Table t: ts) { names.add(normalize(t.getSchemaName())); }
	Set<View> vs = sm.getViews();
	for(View v: vs) { names.add(normalize(v.getSchemaName())); }
	Set<ExecutableObject> eos = sm.getExecutables();
	for(ExecutableObject eo: eos) { names.add(normalize(eo.getSchemaName())); }
	//XXX: add FKs, indexes, sequences, synonyms, triggers ??
	}
	
	List<String> schemas = new ArrayList<String>();
	Properties prop = (Properties) request.getSession().getServletContext().getAttribute(QueryOn.ATTR_PROP);
	//out.write(DBUtil.getDBConnPrefix(prop, modelId));
	if(prop!=null) {
		try {
			Connection conn = DBUtil.initDBConn(prop, modelId);
			schemas = SQLUtils.getSchemaNames(conn.getMetaData());
			conn.close();
		}
		catch(Exception e) {
			log.warn("Exception: "+e);
		}
	}
%>{
	"modelschemas": [<%= String.valueOf(Utils.join(names, ", ", sqd)) %>],
	"schemas": [<%= String.valueOf(Utils.join(schemas, ", ", sqd)) %>]
}