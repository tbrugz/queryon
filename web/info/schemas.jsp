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
	
	Map<String, List<String>> schemasByModel = (Map<String, List<String>>) application.getAttribute(QueryOn.ATTR_SCHEMAS_MAP);
	if(schemasByModel==null) {
		schemasByModel = new HashMap<String, List<String>>();
		application.setAttribute(QueryOn.ATTR_SCHEMAS_MAP, schemasByModel);
	}
	
	List<String> schemas = schemasByModel.get(modelId);
	
	if(schemas==null) {
		Properties prop = (Properties) application.getAttribute(QueryOn.ATTR_PROP);
		//out.write(DBUtil.getDBConnPrefix(prop, modelId));
		if(prop!=null) {
			try {
				Connection conn = DBUtil.initDBConn(prop, modelId);
				schemas = SQLUtils.getSchemaNames(conn.getMetaData());
				conn.close();
				List<String> s2i = Utils.getStringListFromProp(prop, QueryOn.PROP_SCHEMAS_TO_IGNORE, ",");
				if(s2i!=null) {
					for(int i=schemas.size()-1;i>=0;i--) {
						String s = schemas.get(i);
						if(s2i.contains(s)) {
							schemas.remove(i);
						}
					}
				}
				schemasByModel.put(modelId, schemas);
			}
			catch(Exception e) {
				log.warn("Exception: "+e);
			}
		}
	}
%>{
	"modelschemas": [<%= String.valueOf(Utils.join(names, ", ", sqd)) %>],
	"schemas": [<%= String.valueOf(Utils.join(schemas, ", ", sqd)) %>]
}