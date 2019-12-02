<%@page import="tbrugz.sqldump.util.StringUtils"%>
<%@page import="java.util.HashMap"%>
<%@page import="tbrugz.queryon.UpdatePlugin"%>
<%@page import="tbrugz.queryon.QueryOn"%>
<%@page import="java.util.TreeMap"%>
<%@page import="com.google.gson.Gson"%>
<%@page import="java.util.List"%>
<%@page import="java.util.Map"%>
<%@page import="tbrugz.queryon.ResponseSpec"%>
<%@page import="java.util.Set"
%><%@page import="java.util.Arrays"
%><%@page import="tbrugz.sqldump.dbmodel.DBObjectType"
%><%@page import="tbrugz.sqldump.util.StringDecorator.StringQuoterDecorator"
%><%@page import="tbrugz.sqldump.util.Utils"
%><%@page import="tbrugz.queryon.util.*"
%><%!
	StringQuoterDecorator sqd = new StringQuoterDecorator("\"");
	Gson gson = new Gson();
%><%
	String models = null;
	Set<String> modelSet = SchemaModelUtils.getModelIds(application);
	//System.out.println(modelSet);
	if(modelSet!=null && modelSet.size()>0 && modelSet.iterator().next()!=null) {
		models = Utils.join(modelSet, ", ", sqd);
	}
	response.setContentType(ResponseSpec.MIME_TYPE_JSON);
	
	// XXX add ServiceRegistry class?
	String[] endpoints = { "QueryOn", "Swagger", "OData", "GraphQL", "Soap" };
	String[] classNames = { "queryon.QueryOn", "SwaggerServlet", "ODataServlet", "GraphQlQonServlet", "QonSoapServlet" };
	Map<String, ? extends ServletRegistration> servletRegs = request.getServletContext().getServletRegistrations();

	Map<String, String> serviceEndpoints = new TreeMap<String, String>(); // new LinkedHashMap<String, String>();
	for(int i=0;i<endpoints.length;i++) {
		for(Map.Entry<String, ? extends ServletRegistration> sre: servletRegs.entrySet()) {
			ServletRegistration sr = sre.getValue();
			if(sr.getClassName().endsWith(classNames[i])) {
				String url = sr.getMappings().iterator().next();
				serviceEndpoints.put(endpoints[i], url);
				//System.out.println("- "+endpoints[i]+":: "+sre.getKey()+": "+sr.getClassName()+" / "+sr.getName()+" / "+url);
			}
		}
	}
	
	Map<String, List<String>> updatePluginsMap = new HashMap<String, List<String>>();
	Map<String, List<UpdatePlugin>> updatePlugins = (Map<String, List<UpdatePlugin>>) application.getAttribute(QueryOn.ATTR_UPDATE_PLUGINS);
	if(updatePlugins!=null) {
		for(Map.Entry<String, List<UpdatePlugin>> e: updatePlugins.entrySet()) {
			List<String> cnames = StringUtils.getClassSimpleNameListFromObjectList(e.getValue());
			updatePluginsMap.put(e.getKey(), cnames);
		}
	}
%>{
	"models": [<%= models %>]
	<% //, "types": [< %//= Utils.join(Arrays.asList(DBObjectType.values()), ", ", sqd) % >] %>
	,"services": <%= gson.toJson(serviceEndpoints) %>
	,"update-plugins": <%= gson.toJson(updatePluginsMap) %>
}
