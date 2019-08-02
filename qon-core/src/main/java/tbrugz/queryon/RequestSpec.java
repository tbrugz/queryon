package tbrugz.queryon;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.Gson;

import tbrugz.queryon.util.DumpSyntaxUtils;
import tbrugz.queryon.util.MiscUtils;
import tbrugz.queryon.util.SchemaModelUtils;
import tbrugz.queryon.util.WebUtils;
import tbrugz.sqldump.datadump.DumpSyntaxInt;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.resultset.pivot.PivotResultSet;
import tbrugz.sqldump.util.ParametrizedProperties;
import tbrugz.sqldump.util.StringUtils;
import tbrugz.sqldump.util.Utils;

/**
 * see: /web/doc/api.md
 */
/*
 * TODOne: Recspec: init new dumpsyntax if parameters are present
 * 
 * XXXxx do a select on the inserted/updated row (req.getParameter("doselect"))? maybe use st.getGeneratedKeys() instead...
 */
public class RequestSpec {
	
	static final Log log = LogFactory.getLog(RequestSpec.class);
	
	/*public class Filter {
		final String id;
		final boolean multiple;
		final String prefixAccept;
		final Map<String, String[]> map;
		
		public Filter(String id, boolean multiple, Map map) {
			this.id = id;
			this.multiple = multiple;
			this.prefixAccept = id+":";
			this.map = map;
		}
		
		boolean accept(String s) {
			return s.startsWith(prefixAccept);
		}
	}*/
	
	public static final String SYNTAXES_INFO_RESOURCE = "/tbrugz/queryon/syntaxes/syntaxinfo.properties";
	
	static final String MULTIPART = "multipart/form-data";
	
	public static final String PARAM_METHOD = "_method";
	
	public static final String HEADER_ACCEPT = "Accept";
	public static final String HEADER_CONTENT_DISPOSITION = "content-disposition";
	public static final String HEADER_PARAM_ENCODING = "X-ParamEncoding";
	public static final String PARAM_ENCODING_URLENCODE = "url";
	
	public static final String ATTR_CONTENTLOCATION = "content-location";
	public static final String ATTR_SQL_INDEX_OF_INITIAL = "sql.index-of-initial";
	public static final String ATTR_SQL_LINE_OF_INITIAL = "sql.line-of-initial";
	public static final String ATTR_GENERATED_KEYS = "generated-keys";
	public static final String ATTR_RETURN_LIST = "return-list";
	
	// select parameters
	public static final String PARAM_FIELDS = "fields";
	public static final String PARAM_DISTINCT = "distinct";
	public static final String PARAM_ORDER = "order";
	public static final String PARAM_LIMIT = "limit";
	public static final String PARAM_OFFSET = "offset";
	public static final String PARAM_COUNT = "count";
	public static final String PARAM_LO_STRATEGY = "lostrategy";
	public static final String PARAM_FIELD_ALIASES = "aliases";
	public static final String PARAM_GROUP_BY = "groupby";
	public static final String PARAM_GROUP_BY_DIMS = "groupbydims";
	
	// "blob" parameters
	public static final String PARAM_VALUEFIELD = "valuefield";
	public static final String PARAM_MIMETYPE = "mimetype";
	public static final String PARAM_MIMETYPE_FIELD = "mimetypefield"; //TODOne: mimefield -> mimetypefield
	public static final String PARAM_FILENAME = "filename";
	public static final String PARAM_FILENAME_FIELD = "filenamefield";

	// positional parameters
	public static final String PARAM_BODY_PARAM_INDEX = "bodyparamindex";
	protected static final Pattern positionalParamPattern = Pattern.compile("p([1-9]+[0-9]*)", Pattern.DOTALL);
	
	// pivot parameters
	public static final String PARAM_ONCOLS = "oncols";
	public static final String PARAM_ONROWS = "onrows";
	public static final String PARAM_MEASURES = "measures";
	public static final String PARAM_PIVOTFLAGS = "pivotflags";
	public static final int DEFAULT_PIVOTFLAGS_WITH_MEASURES = PivotResultSet.SHOW_MEASURES_ALLWAYS | PivotResultSet.FLAG_NON_EMPTY_COLS;
	public static final int DEFAULT_PIVOTFLAGS_WITHOUT_MEASURES = PivotResultSet.SHOW_MEASURES_ALLWAYS;
	
	// update parameters
	public static final String PARAM_UPDATE_MIN = "updatemin";
	public static final String PARAM_UPDATE_MAX = "updatemax";
	public static final String PARAM_BODY_PARAM_NAME = "bodyparamname"; //body(update)paramname?
	public static final String PARAM_OPTIMISTICLOCK = "optimisticlock";
	
	public static final String ORDER_ASC = "ASC";
	public static final String ORDER_DESC = "DESC";
	
	protected final HttpServletRequest request; //XXX: private & add getAttribute/setAttribute??

	protected final String httpMethod;
	protected final String modelId;
	protected String object;
	protected int offset;
	protected Integer limit;
	final String loStrategy;
	final String contentType;
	final String utf8;
	
	// data manipulation (DML) properties
	final Integer minUpdates, maxUpdates;
	
	protected final List<String> columns = new ArrayList<String>();
	protected final List<String> aliases = new ArrayList<String>();
	protected final List<Object> params = new ArrayList<Object>();
	protected final List<String> groupby = new ArrayList<String>();
	protected final Map<String, String[]> aggregate = new TreeMap<String, String[]>();
	
	final String outputTypeStr;
	protected final DumpSyntaxInt outputSyntax;
	final Properties syntaxSpecificProperties = new Properties();
	protected boolean distinct;
	final boolean count;
	final String headerParamEncoding;
	
	final List<String> oncols = new ArrayList<String>();
	final List<String> onrows = new ArrayList<String>();
	final Integer pivotflags;
	
	// 'eq', 'ne', 'gt', 'lt', 'ge', 'le'? see: http://en.wikipedia.org/wiki/Relational_operator
	// 'in', 'nin - not in', 'null', 'nnull - not null', 'like', 'not like', 'between' - see: http://en.wikipedia.org/wiki/SQL#Operators
	
	// unique value filters
	protected final Map<String, String> filterEquals = new HashMap<String, String>();
	protected final Map<String, String> filterNotEquals = new HashMap<String, String>();
	protected final Map<String, String> filterGreaterThan = new HashMap<String, String>();
	protected final Map<String, String> filterGreaterOrEqual = new HashMap<String, String>();
	protected final Map<String, String> filterLessThan = new HashMap<String, String>();
	protected final Map<String, String> filterLessOrEqual = new HashMap<String, String>();
	
	// multiple value filters
	protected final Map<String, String[]> filterIn = new HashMap<String, String[]>();
	protected final Map<String, String[]> filterNotIn = new HashMap<String, String[]>(); // not in (fnin)
	protected final Map<String, String[]> filterLike = new HashMap<String, String[]>();
	protected final Map<String, String[]> filterNotLike = new HashMap<String, String[]>();

	// boolean valued filters
	protected final Set<String> filterNull = new HashSet<String>();
	protected final Set<String> filterNotNull = new HashSet<String>();
	
	public static final String[] FILTERS_UNIPARAM = { "feq", "fne", "fgt", "fge", "flt", "fle" };
	public static final String[] FILTERS_MULTIPARAM = { "fin", "fnin", "flk", "fnlk" };
	public static final String[] FILTERS_MULTIPARAM_STRONLY = { "flk", "fnlk" };
	public static final String[] FILTERS_BOOL = { "fnull", "fnotnull" };

	public static final String[] FILTERS_MULTIPARAM_STRONLY_ORDERED;
	static {
		FILTERS_MULTIPARAM_STRONLY_ORDERED = Arrays.copyOf(FILTERS_MULTIPARAM_STRONLY, FILTERS_MULTIPARAM_STRONLY.length);
		Arrays.sort(FILTERS_MULTIPARAM_STRONLY_ORDERED);
	}
	
	public static final String PROP_FILTERS_ALLOWED = "queryon.filter.allowed"; //feq, fne, fgt, fge, flt, fle, fin, fnin, flk, fnlk, fnull, fnotnull
	public static final String PROP_GROUPBY_ALLOW = "queryon.groupby.allow";
	public static final String PROP_DISTINCT_ALLOW = "queryon.distinct.allow";
	
	//XXXdone: add filters: is null (fnull), is not null (fnn/fnnull/fnotnull), 
	//XXX: add filter: between (btwn)?
	
	protected final Map<String, String> keyValues = new LinkedHashMap<String, String>();
	protected final Map<String, String> updateValues = new HashMap<String, String>();
	final Map<String, Part> updatePartValues = new HashMap<String, Part>();
	final String optimisticLock;

	protected final List<String> orderCols = new ArrayList<String>();
	protected final List<String> orderAscDesc = new ArrayList<String>();
	
	final boolean showDebugInfo = false;

	// syntax properties resource
	static final Properties syntaxProperties = new Properties();
	static {
		try {
			syntaxProperties.load(RequestSpec.class.getResourceAsStream(SYNTAXES_INFO_RESOURCE));
		} catch (Exception e) {
			log.warn("Error loading resource '"+SYNTAXES_INFO_RESOURCE+"'", e);
			e.printStackTrace();
		}
	}
	
	// blob/download fields
	protected String uniValueCol;
	final String uniValueMimetype;
	final String uniValueMimetypeCol;
	final String uniValueFilename;
	final String uniValueFilenameCol;
	
	public final static int DEFAULT_LIMIT = 100;
	final static int DEFAULT_MAX_UPDATES = 1;
	final static int DEFAULT_MIN_UPDATES = 1;
	
	// file extensions to be ignored by RequestSpec
	final static String[] standardFileExt = { "blob" };
	
	//final String defaultOutputSyntax;
	//final boolean allowGetDumpSyntaxByAccept;
	
	public RequestSpec(DumpSyntaxUtils dsutils, HttpServletRequest req, Properties prop) throws ServletException, IOException {
		this(dsutils, req, prop, 0);
	}

	public RequestSpec(DumpSyntaxUtils dsutils, HttpServletRequest req, Properties prop, int prefixesToIgnore) throws ServletException, IOException {
		this(dsutils, req, prop, prefixesToIgnore, QueryOn.DEFAULT_OUTPUT_SYNTAX, true, 1, null);
	}
	
	public RequestSpec(final DumpSyntaxUtils dsutils, final HttpServletRequest req, final Properties prop, final int prefixesToIgnore,
			final String defaultOutputSyntax, final boolean allowGetDumpSyntaxByAccept, final int minUrlParts, final String defaultObject) throws ServletException, IOException {
		this.request = req;
		//this.allowGetDumpSyntaxByAccept = allowGetDumpSyntaxByAccept;
		String defaultOutputSyntaxTmp = getDefaultOutputSyntax(prop, defaultOutputSyntax);
		
		contentType = req.getContentType();
		
		/* 
		 * http://stackoverflow.com/a/37125568/616413 - using-put-method-in-html-form
		 * http://programmers.stackexchange.com/questions/114156/why-are-there-are-no-put-and-delete-methods-on-html-forms
		 * http://laraveldaily.com/theres-no-putpatchdelete-method-or-how-to-build-a-laravel-form-manually/
		 * http://symfony.com/doc/current/cookbook/routing/method_parameters.html
		 */
		httpMethod = getMethod(req);
		
		this.utf8 = req.getParameter(WebUtils.PARAM_UTF8);
		boolean convertLatin1ToUtf8 = false;
		if(utf8!=null) {
			if(utf8.equals(WebUtils.UTF8_CHECK)) {
				log.debug("[ok] utf8: "+utf8);
			}
			else {
				// assume url encoding as latin1 (iso-8859-1)
				log.warn("[err] utf8: ["+MiscUtils.toIntArrayAsString(utf8)+"] [expected="+WebUtils.UTF8_CHECK+"] - will 'convertLatin1ToUtf8'");
				convertLatin1ToUtf8 = true;
				//req.setCharacterEncoding("xxx");
				// 226 156 147 ? http://www.utf8-chartable.de/unicode-utf8-table.pl?start=9984&number=128&names=-&utf8=dec
				// https://stackoverflow.com/questions/10517268/how-to-pass-unicode-characters-as-jsp-servlet-request-getparameter
				// https://stackoverflow.com/questions/27338154/why-do-some-websites-have-utf8-in-their-title
			}
		}
		
		this.modelId = getModelId(req);
		//TODO test if model with this id exists
		
		String varUrl = req.getPathInfo();
		if(varUrl==null) {
			varUrl = "";
			//throw new BadRequestException("URL (path-info) must not be null");
		}
		
		//String[] URIparts = varUrl!=null ? varUrl.split("/") : new String[]{} ;
		/*String[] URIparts = varUrl.split("/");
		List<String> URIpartz = new ArrayList<String>( Arrays.asList(URIparts) );
		if(!URIpartz.isEmpty() && "".equals(URIpartz.get(0))) { URIpartz.remove(0); }*/
		List<String> URIpartz = getUrlParts(varUrl);
		
		//log.info("urlparts: "+URIpartz+" [#"+URIpartz.size()+"][minparts="+minUrlParts+"]");
		if(URIpartz.size()<minUrlParts) {
			throw new BadRequestException("URL must have at least "+minUrlParts+" part"+(minUrlParts>1?"s":""));
		}

		/*
		String lastURIPart = URIpartz.remove(URIpartz.size()-1);
		int lastDotIndex = lastURIPart.lastIndexOf('.');
		if(lastDotIndex > -1) {
			String outputTypeStrTmp = lastURIPart.substring(lastDotIndex+1);
			// test for known syntax
			if(dsutils.getDumpSyntax(outputTypeStrTmp, prop) != null) {
				outputTypeStr = outputTypeStrTmp;
				lastURIPart = lastURIPart.substring(0, lastDotIndex);
			}
			else {
				outputTypeStr = null;
				List<String> ignoreFileExt = Arrays.asList(standardFileExt);
				if(ignoreFileExt.contains(outputTypeStrTmp)) {
					lastURIPart = lastURIPart.substring(0, lastDotIndex);
				}
			}
		}
		else {
			outputTypeStr = null;
		}
		*/
		String lastURIPart = URIpartz.size()>0 ? URIpartz.remove(URIpartz.size()-1) : null;
		outputTypeStr = getSyntax(lastURIPart, dsutils, prop);
		
		if(outputTypeStr!=null) {
			lastURIPart = lastURIPart.substring(0, lastURIPart.length()-1-outputTypeStr.length());
		}

		if(lastURIPart!=null) {
			URIpartz.add( lastURIPart );
		}
		//log.info("varUrl="+varUrl+" / URIparts="+Arrays.asList(URIparts)+" / URIpartz="+URIpartz+" / URIpartz.size="+URIpartz.size()+ " / lastURIPart = "+lastURIPart);
		//log.info("output-type: "+outputTypeStr+"; new urlparts: "+URIpartz);
		
		String objectTmp = getObject(URIpartz, prefixesToIgnore);
		if(convertLatin1ToUtf8) {
			objectTmp = MiscUtils.latin1ToUtf8(objectTmp);
		}
		if(objectTmp==null) {
			if(defaultObject!=null) {
				objectTmp = defaultObject;
			}
			else {
				objectTmp = "";
			}
		}
		object = objectTmp;
		log.debug("object: "+object+"; output-type: "+outputTypeStr+"; xtra URIpartz: "+URIpartz);
		
		processParams(URIpartz);
		
		outputSyntax = getOutputSyntax(req, dsutils, allowGetDumpSyntaxByAccept, defaultOutputSyntaxTmp);
		
		SchemaModel sm = SchemaModelUtils.getModel(req.getServletContext(), this.modelId);
		if(sm==null) {
			log.warn("null SchemaModel [modelId="+this.modelId+"]");
		}
		else {
			DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(sm.getSqlDialect());
			setSyntaxProps(req, feat, prop);
		}

		//log.debug("outputSyntax: "+outputSyntax);
		
		//---------------------
		
		offset = getFinalOffset(req);

		limit = getFinalLimit(req);
		
		// max & min updates
		String updateMaxStr = req.getParameter(PARAM_UPDATE_MAX);
		String updateMinStr = req.getParameter(PARAM_UPDATE_MIN);
		maxUpdates = (updateMaxStr!=null)?Integer.parseInt(updateMaxStr):DEFAULT_MAX_UPDATES;
		minUpdates = (updateMinStr!=null)?Integer.parseInt(updateMinStr):DEFAULT_MIN_UPDATES;
		
		String fields = getFields(req);
		if(fields!=null) {
			//columns.addAll(Arrays.asList(fields.split(",")));
			addAllWithTrim(columns, fields);
		}
		
		String aliasesParameter = getFieldAliases(req);
		if(aliasesParameter!=null) {
			addAllWithTrim(aliases, aliasesParameter);
		}

		boolean allowGroupBy = Utils.getPropBool(prop, PROP_GROUPBY_ALLOW, true);
		String groupByStr = req.getParameter(PARAM_GROUP_BY);
		if(groupByStr!=null) {
			if(!allowGroupBy) {
				throw new BadRequestException("groupby not allowed");
			}
			addAllWithTrim(groupby, groupByStr);
		}
		
		String onColsPar = req.getParameter(PARAM_ONCOLS);
		if(onColsPar!=null) {
			addAllWithTrim(oncols, onColsPar);
		}
		String onRowsPar = req.getParameter(PARAM_ONROWS);
		if(onRowsPar!=null) {
			addAllWithTrim(onrows, onRowsPar);
		}
		String measuresPar = req.getParameter(PARAM_MEASURES);
		if(measuresPar!=null) {
			if(fields!=null) {
				throw new BadRequestException("can't define both 'fields' & 'measures' parameters");
			}
			if(onrows.size()==0 && oncols.size()==0) {
				throw new BadRequestException("can't define 'measures' without 'onrows' or 'oncols' parameters");
			}
			columns.addAll(onrows);
			columns.addAll(oncols);
			addAllWithTrim(columns, measuresPar);
		}
		boolean groupByDims = getBoolValue(req.getParameter(PARAM_GROUP_BY_DIMS));
		if(groupByDims) {
			if(onrows.size()==0 && oncols.size()==0) {
				throw new BadRequestException("can't define '"+PARAM_GROUP_BY_DIMS+"' without 'onrows' or 'oncols' parameters");
			}
			if(groupby.size()>0) {
				throw new BadRequestException("can't define both '"+PARAM_GROUP_BY_DIMS+"' & '"+PARAM_GROUP_BY+"' parameters");
			}
			if(!allowGroupBy) {
				throw new BadRequestException("groupby/groupbydims not allowed");
			}
			
			groupby.addAll(onrows);
			groupby.addAll(oncols);
		}
		String pivotStr = req.getParameter(PARAM_PIVOTFLAGS);
		if(pivotStr!=null) { pivotflags = Integer.parseInt(pivotStr); }
		else { pivotflags = null; }
		
		distinct = isDistinct(req, Utils.getPropBool(prop, PROP_DISTINCT_ALLOW, true));
		count = isCountRequest(req);
		headerParamEncoding = req.getHeader(HEADER_PARAM_ENCODING);

		processOrder(req);
		
		loStrategy = req.getParameter(PARAM_LO_STRATEGY);
		
		uniValueCol = getValueField(req);
		uniValueMimetype = req.getParameter(PARAM_MIMETYPE);
		uniValueMimetypeCol = req.getParameter(PARAM_MIMETYPE_FIELD);
		uniValueFilename = req.getParameter(PARAM_FILENAME);
		uniValueFilenameCol = req.getParameter(PARAM_FILENAME_FIELD);
		
		//Enumeration<String> en = (Enumeration<String>) req.getParameterNames();
		//while(en.hasMoreElements()) {
		//	String key = en.nextElement();
		//	String[] value = req.getParameterValues(key);
		
		optimisticLock = request.getParameter(PARAM_OPTIMISTICLOCK);
		
		processBody(req);

		// set filters, xtras...
		Set<String> allowedFilters = null;
		List<String> allowedFiltersList = Utils.getStringListFromProp(prop, PROP_FILTERS_ALLOWED, ",");
		if(allowedFiltersList!=null) {
			allowedFilters = new HashSet<String>();
			allowedFilters.addAll(allowedFiltersList);
		}
		processFilters(allowedFilters);
		processXtra();
	}
	
	public static List<String> getUrlParts(String url) {
		if(url==null) { url = ""; }
		String[] URIparts = url.split("/");
		List<String> URIpartz = new ArrayList<String>( Arrays.asList(URIparts) );
		if(!URIpartz.isEmpty() && "".equals(URIpartz.get(0))) { URIpartz.remove(0); }
		return URIpartz;
	}
	
	protected void processBody(HttpServletRequest req) throws NumberFormatException, IOException, ServletException {
		Map<Integer, Object> postionalParamsMap = new TreeMap<Integer, Object>();

		Map<String,String[]> reqParams = getParameterMap();
		for(Map.Entry<String,String[]> entry: reqParams.entrySet()) {
			Matcher m = positionalParamPattern.matcher(entry.getKey());
			if(m.matches()) {
				int pos = Integer.parseInt( m.group(1) );
				String[] values = entry.getValue();
				if(values==null || values.length==0) {
					log.warn("null (or empty) 'values': "+entry.getKey()+" / "+values);
				}
				else {
					if(values.length>1) {
						log.warn("values.length>1 ["+values.length+"]: "+entry.getKey());
					}
					postionalParamsMap.put(pos, values[0]);
				}
			}
		}
		if(showDebugInfo) {
			showDebugInfo(reqParams);
		}
		
		/*for(int i=1;;i++) {
			String value = req.getParameter("p"+i);
			if(value==null) break;
			//params.add(value);
			paramMap.put(i, value);
		}*/
		
		String bodyParamIndex = req.getParameter(PARAM_BODY_PARAM_INDEX);
		String bodyParamName = req.getParameter(PARAM_BODY_PARAM_NAME);
		
		// http://stackoverflow.com/questions/2422468/how-to-upload-files-to-server-using-jsp-servlet
		if(isContentTypeMultiPart()) {
			int i=0;
			for(Part p: req.getParts()) {
				String name = p.getName();
				String fileName = getSubmittedFileName(p);
				//log.debug("part["+i+"]: " + name + (fileName!=null?" / filename="+fileName:"") + (" / content-type="+p.getContentType()) );
				if(p.getContentType()!=null) { //XXX: really needed?
					boolean added = setUniParam("v:", name, p, updatePartValues); // XXX: updating 'updatePartValues' but 'updateValues' may already have value...
					boolean partParamAdded = false;
					if(!added) {
						//TODOne: mixed part & non-part parameters are probably not working...
						Matcher m = positionalParamPattern.matcher(name);
						if(m.matches()) {
							int pos = Integer.parseInt( m.group(1) );
							if(postionalParamsMap.get(pos)!=null) {
								log.warn("parameter p"+pos+" already setted"); // maybe with req.getParameterMap()
							}
							else {
								postionalParamsMap.put(pos, p);
								partParamAdded = true;
								//log.info("part["+i+"]: pos==" + pos);
							}
						}
					}
					log.debug("part["+i+";added="+added+";partParamAdded="+partParamAdded+"]: " + name + "; content-type="+p.getContentType() + " ;  size=" + p.getSize() + " ; " + (fileName!=null?" / filename="+fileName:"") );
				}
				i++;
			}
			log.debug("multipart-content: length="+i);
		}
		else if(isContentTypeJson()) {
			Gson gson = new Gson();
			@SuppressWarnings("unchecked")
			Map<String, Object> map = gson.fromJson(request.getReader(), Map.class);
			if(map!=null) {
				for(Map.Entry<String, Object> e: map.entrySet()) {
					String value = getValueForUpdate( e.getValue() );
					//log.debug("json body: "+e.getKey()+" / "+e.getValue()+" / "+value+" ["+e.getValue().getClass()+"]");
					//XXX if matches 'p[1-9][0-9]*', add to params (POST/doExecute)?
					updateValues.put(e.getKey(), value);
				}
			}
		}
		else if(bodyParamIndex!=null) {
			// EXEC (POST)
			try {
				int pos = Integer.parseInt( bodyParamIndex );
				if(postionalParamsMap.get(pos)!=null) {
					log.warn("positional parameter #"+pos+" already setted");
				}
				else {
					String value = getRequestBody(req);
					postionalParamsMap.put(pos, value);
					//System.out.println("setting parameter #"+pos+": "+value);
				}
			} catch (NumberFormatException e) {
				log.warn("error parsing parameter index [bodyParamIndex="+bodyParamIndex+"]: "+e);
			} catch (IOException e) {
				log.warn("error decoding http message body [bodyParamIndex="+bodyParamIndex+"]: "+e);
			}
		}
		else if(bodyParamName!=null) {
			// INSERT (POST) / UPDATE (PATCH)
			try {
				String value = getRequestBody(req);
				updateValues.put(bodyParamName, value);
				//System.out.println("updating parameter "+bodyParamName+": "+value);
			} catch (IOException e) {
				log.warn("error decoding http message body [bodyParamName="+bodyParamName+"]: "+e);
			}
		}

		// seting positional params
		{
			int pCount = 1;
			for(Map.Entry<Integer, Object> e: postionalParamsMap.entrySet()) {
				int i = e.getKey();
				params.add(e.getValue());
				if(i!=pCount) {
					String message = "parameter #"+i+" present but previous parameter isn't [pCount="+pCount+"]";
					//System.out.println(message);
					log.warn(message);
					//XXX: executables may have OUT parameters, so some parameters may be missing from input
					//throw new BadRequestException(message);
				}
				pCount++;
			}
		}
		
	}
	
	protected String getMethod(HttpServletRequest req) {
		String switchMethod = req.getParameter(PARAM_METHOD);
		if(switchMethod==null) {
			return req.getMethod();
		}
		String origMethod = req.getMethod();
		if(origMethod.equalsIgnoreCase(QueryOn.METHOD_POST) && switchMethod.equals(QueryOn.METHOD_PATCH)) {
			return QueryOn.METHOD_PATCH;
		}
		//XXX: may method be changed? property?
		throw new BadRequestException("Method switch not allowed [method: "+req.getMethod()+"/_method="+switchMethod+"]");
	}
	
	protected String getModelId(HttpServletRequest req) {
		return SchemaModelUtils.getModelId(req);
	}
	
	protected DumpSyntaxInt getOutputSyntax(HttpServletRequest req, DumpSyntaxUtils dsutils, boolean allowGetDumpSyntaxByAccept, String defaultOutputSyntax) {
		DumpSyntaxInt outputSyntaxTmp = null;
		// http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
		// accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
		String acceptHeader = req.getHeader(HEADER_ACCEPT);
		log.debug("accept: "+acceptHeader+" ; modelId: "+this.modelId);
		
		if(outputTypeStr != null) {
			outputSyntaxTmp = dsutils.getDumpSyntax(outputTypeStr);
			if(outputSyntaxTmp==null) {
				// will never happen?
				throw new BadRequestException("Unknown output syntax: "+outputTypeStr);
			}
		}
		else {
			if(allowGetDumpSyntaxByAccept) {
				outputSyntaxTmp = dsutils.getDumpSyntaxByAccept(acceptHeader);
			}
			if(outputSyntaxTmp==null) {
				log.debug("no syntax match, using default ["+defaultOutputSyntax+"]");
				outputSyntaxTmp = dsutils.getDumpSyntax(defaultOutputSyntax);
				if(outputSyntaxTmp==null) {
					//log.warn("null outputSyntax ["+defaultOutputSyntax+"]");
					throw new BadRequestException("Unknown default output syntax: "+defaultOutputSyntax);
				}
			}
			else {
				log.debug("syntax defined by accept: syntax: "+outputSyntaxTmp.getSyntaxId()+" ; mime: "+outputSyntaxTmp.getMimeType()+" ; accept: "+acceptHeader);
			}
		}
		return outputSyntaxTmp;
	}
	
	protected String getDefaultOutputSyntax(Properties prop, String defaultSyntax) {
		return prop.getProperty(QueryOn.PROP_SYNTAX_DEFAULT, defaultSyntax);
	}
	
	protected void processFilters(Set<String> allowedFilters) throws UnsupportedEncodingException {
		for(Map.Entry<String,String[]> entry: getParameterMap().entrySet()) {
			String key = entry.getKey();
			String[] value = entry.getValue();
			
			if( PARAM_ENCODING_URLENCODE.equals(headerParamEncoding) ) {
				for(int i=0;i<value.length;i++) {
					value[i] = URLDecoder.decode(value[i], QueryOn.UTF8);
					//value[i] = new String(value[i].getBytes("iso-8859-1"), QueryOn.UTF8);
				}
			}
			else if(headerParamEncoding!=null) {
				log.warn("unknown "+PARAM_ENCODING_URLENCODE+":"+headerParamEncoding);
			}
			
			try {
				setFilterUniParam("feq", key, value[0], filterEquals, allowedFilters);
				setFilterUniParam("fne", key, value[0], filterNotEquals, allowedFilters);
				setFilterUniParam("fgt", key, value[0], filterGreaterThan, allowedFilters);
				setFilterUniParam("fge", key, value[0], filterGreaterOrEqual, allowedFilters);
				setFilterUniParam("flt", key, value[0], filterLessThan, allowedFilters);
				setFilterUniParam("fle", key, value[0], filterLessOrEqual, allowedFilters);
				
				setFilterMultiParam("fin", key, value, filterIn, allowedFilters);
				setFilterMultiParam("fnin", key, value, filterNotIn, allowedFilters);
				setFilterMultiParam("flk", key, value, filterLike, allowedFilters);
				setFilterMultiParam("fnlk", key, value, filterNotLike, allowedFilters);
				
				setFilterBooleanParam("fnull", key, filterNull, allowedFilters);
				setFilterBooleanParam("fnotnull", key, filterNotNull, allowedFilters);
				
				setMultiParam("agg:", key, value, aggregate);
				
				setUniParam("v:", key, value[0], updateValues);
				
				setUniParam("k:", key, value[0], keyValues);
			}
			catch(BadRequestException e) {
				throw e;
			}
			catch(RuntimeException e) {
				//log.warn("encoding error [e: "+entry+"]: "+e);
				log.warn("setParam exception [k:"+key+"; v:"+Arrays.toString(value)+"]: "+e);
			}
		}

		/*
		for(String param: params.keySet()) {
			if(param.startsWith("feq:")) {
				String col = param.substring(4);
				String value = params.get(param)[0];
				filterEquals.put(col, value);
			}
			else if(param.startsWith("fne:")) {
				String col = param.substring(4);
				String value = params.get(param)[0];
				filterNotEquals.put(col, value);
			}
			
			else if(param.startsWith("fgt:")) {
				String col = param.substring(4);
				String value = params.get(param)[0];
				filterGreaterThan.put(col, value);
			}
			else if(param.startsWith("fge:")) {
				String col = param.substring(4);
				String value = params.get(param)[0];
				filterGreaterOrEqual.put(col, value);
			}
			else if(param.startsWith("flt:")) {
				String col = param.substring(4);
				String value = params.get(param)[0];
				filterLessThan.put(col, value);
			}
			else if(param.startsWith("fle:")) {
				String col = param.substring(4);
				String value = params.get(param)[0];
				filterLessOrEqual.put(col, value);
			}
			
			else if(param.startsWith("fin:")) {
				String col = param.substring(4);
				String[] values = params.get(param);
				filterIn.put(col, values);
			}
			else if(param.startsWith("fnin:")) {
				String col = param.substring(5);
				String[] values = params.get(param);
				filterNotIn.put(col, values);
			}
			else if(param.startsWith("flk:")) {
				String col = param.substring(4);
				String[] values = params.get(param);
				filterLike.put(col, values);
			}
			else if(param.startsWith("fnlk:")) {
				String col = param.substring(5);
				String[] values = params.get(param);
				filterNotLike.put(col, values);
			}
			
			else if(param.startsWith("v:")) {
				String col = param.substring(2);
				String value = params.get(param)[0];
				updateValues.put(col, value);
			}
			/*else {
				for(Filter f: filters) {
					if(f.accept(param)) {
						
					}
				}
			}* /
			else {
				//log.info("param "+param+" : "+params.get(param)[0]);
			}

			//XXX: warn unknown parameters
		}
		*/
	}

	protected void processXtra() {}
	
	protected int getFinalOffset(HttpServletRequest req) {
		String offsetStr = req.getParameter(PARAM_OFFSET);
		return offsetStr!=null? Integer.parseInt(offsetStr) : 0;
	}
	
	protected Integer getFinalLimit(HttpServletRequest req) {
		String limitStr = req.getParameter(PARAM_LIMIT);
		return limitStr!=null ? Integer.parseInt(limitStr) : null;
	}
	
	protected void processOrder(HttpServletRequest req) {
		String order = req.getParameter(PARAM_ORDER);
		if(order!=null) {
			List<String> orderColz = Arrays.asList(order.split(","));
			for(String ocol: orderColz) {
				ocol = ocol.trim();
				if(ocol.startsWith("-")) {
					ocol = ocol.substring(1);
					orderAscDesc.add(ORDER_DESC);
				}
				else {
					orderAscDesc.add(ORDER_ASC);
				}
				orderCols.add(ocol);
			}
		}
	}
	
	protected String getObject(List<String> parts, int prefixesToIgnore) {
		String objectTmp = null;
		if(parts.size()>0) {
			objectTmp = parts.remove(0);
		}
		/*if(objectTmp == null || objectTmp.equals("")) {
			//first part may be empty
			if(URIpartz.size()>0) {
				objectTmp = URIpartz.remove(0);
			}
		}*/
		for(int i=0;i<prefixesToIgnore;i++) {
			if(parts.size()>0) {
				objectTmp = parts.remove(0);
			}
		}
		return objectTmp;
	}
	
	//XXX rename to processUrlParts
	protected void processParams(List<String> parts) {
		for(int i=0;i<parts.size();i++) {
			params.add(parts.get(i));
		}
	}
	
	protected String getFields(HttpServletRequest req) {
		return req.getParameter(PARAM_FIELDS);
	}
	
	protected String getFieldAliases(HttpServletRequest req) {
		return req.getParameter(PARAM_FIELD_ALIASES);
	}
	
	protected String getValueField(HttpServletRequest req) {
		return req.getParameter(PARAM_VALUEFIELD);
	}
	
	protected boolean isCountRequest(HttpServletRequest req) {
		return getBoolValue(req.getParameter(PARAM_COUNT));
	}
	
	protected boolean isDistinct(HttpServletRequest req, boolean allowDistinct) {
		boolean isDistinct = getBoolValue(req.getParameter(PARAM_DISTINCT));
		if(isDistinct && !allowDistinct) {
			throw new BadRequestException("distinct not allowed");
		}
		return isDistinct;
	}
	
	/*boolean setUniParam(String prefix, String key, String[] values, Map<String, String> uniFilter) {
		//String key = entry.getKey();
		//String[] value = entry.getValue();
		if(key.startsWith(prefix)) {
			String col = key.substring(prefix.length());
			//String value1 = values[0];
			//String value2 = URLDecoder.decode(value1, "UTF-8");
			//String value2 = value1;
			uniFilter.put(col, values[0]);
			//log.info("setUniParam: old="+value+" ; new="+value2);
			return true;
		}
		return false;
	}*/

	public static <T> boolean setFilterUniParam(String prefix, String key, T value, Map<String, T> uniFilter, Set<String> allowedFilters) {
		boolean set = setUniParam(prefix+":", key, value, uniFilter);
		if(set && allowedFilters!=null && !allowedFilters.contains(prefix)) {
			throw new BadRequestException("filter '"+prefix+"' not allowed");
		}
		return set;
	}

	public static boolean setFilterMultiParam(String prefix, String key, String[] values, Map<String, String[]> multiFilter, Set<String> allowedFilters) {
		boolean set = setMultiParam(prefix+":", key, values, multiFilter);
		if(set && allowedFilters!=null &&!allowedFilters.contains(prefix)) {
			throw new BadRequestException("filter '"+prefix+"' not allowed");
		}
		return set;
	}
	
	public static boolean setFilterBooleanParam(String prefix, String key, Set<String> uniFilter, Set<String> allowedFilters) {
		boolean set = setBooleanParam(prefix+":", key, uniFilter);
		if(set && allowedFilters!=null &&!allowedFilters.contains(prefix)) {
			throw new BadRequestException("filter '"+prefix+"' not allowed");
		}
		return set;
	}
	
	public static <T> boolean setUniParam(String prefix, String key, T value, Map<String, T> uniFilter) {
		if(key.startsWith(prefix)) {
			String col = key.substring(prefix.length());
			uniFilter.put(col, value);
			/*T prevValue = uniFilter.put(col, value);
			if(prevValue!=null) {
				log.warn("setUniParam: param '"+col+"' already had a value: "+prevValue);
			}*/
			return true;
		}
		return false;
	}
	
	public static boolean setMultiParam(String prefix, String key, String[] values, Map<String, String[]> multiFilter) {
		if(key.startsWith(prefix)) {
			String col = key.substring(prefix.length());
			//String[] value1 = value;
			//for(int i=0;i<values.length;i++) {
				//String value2 = URLDecoder.decode(value[i], "UTF-8");
				//String value2 = values[i];
				//log.info("setMultiParam: old="+value[i]+" ; new="+value2); 
				//values[i] = value2;
			//}
			multiFilter.put(col, values);
			return true;
		}
		return false;
	}

	public static boolean setBooleanParam(String prefix, String key, Set<String> uniFilter) {
		if(key.startsWith(prefix)) {
			String col = key.substring(prefix.length());
			uniFilter.add(col);
			return true;
		}
		return false;
	}
	
	public static Properties getSyntaxProps(DumpSyntaxInt ds, HttpServletRequest req, DBMSFeatures feat, Properties initProps) {
		List<String> pkeys = Utils.getStringListFromProp(syntaxProperties, ds.getSyntaxId()+".allowed-parameters", ",");
		//<syntax>.parameter@callback.prop=sqldump.datadump.<syntax>.zzz
		//<syntax>.parameter@callback.regex=[a-zA-Z_][a-zA-Z_0-9]*
		if(pkeys==null) { return null; }
		
		Properties newProps = new Properties();
		for(String key: pkeys) {
			String val = req.getParameter(key);
			if(val!=null) {
				String regexKey = ds.getSyntaxId()+".parameter@"+key+".regex";
				String propKey = ds.getSyntaxId()+".parameter@"+key+".prop";
				String regex = syntaxProperties.getProperty(regexKey);
				String prop = syntaxProperties.getProperty(propKey);
				if(regex==null || prop==null) {
					log.warn("["+ds.getSyntaxId()+"] syntax properties '"+regexKey+"' & '"+propKey+"' must be set in '"+SYNTAXES_INFO_RESOURCE+"'");
				}
				else {
					if(Pattern.matches(regex, val)) {
						newProps.put(prop, val);
					}
					else {
						log.warn("["+ds.getSyntaxId()+"] parameter '"+key+"' does not match pattern '"+regex+"': "+val);
					}
				}
			}
		}
		
		return newProps;
	}

	public void setSyntaxProps(HttpServletRequest req, DBMSFeatures feat, Properties initProps) {
		DumpSyntaxInt ds = outputSyntax;
		
		if(ds.needsDBMSFeatures()) { ds.setFeatures(feat); }
		
		Properties newProps = getSyntaxProps(ds, req, feat, initProps);
		
		if(newProps==null || newProps.size()==0) {
			ds.procProperties(initProps);
			return;
		}
		
		syntaxSpecificProperties.putAll(newProps);
		
		Properties modifiedProps = new ParametrizedProperties();
		modifiedProps.putAll(initProps);
		//if(newProps.size()>0) {
			log.debug("["+ds.getSyntaxId()+"] parameter props: "+newProps);
			modifiedProps.putAll(newProps);
		/*}
		else {
			//log.info("["+ds.getSyntaxId()+"] no parameter props");
			return;
		}*/
		ds.procProperties(modifiedProps);
	}

	@Deprecated // only used by DataDiffServlet
	public static void setSyntaxProps(DumpSyntaxInt ds, HttpServletRequest req, DBMSFeatures feat, Properties initProps) {
		if(ds.needsDBMSFeatures()) { ds.setFeatures(feat); }
		
		Properties newProps = getSyntaxProps(ds, req, feat, initProps);
		
		if(newProps==null || newProps.size()==0) {
			ds.procProperties(initProps);
			return;
		}
		
		Properties modifiedProps = new ParametrizedProperties();
		modifiedProps.putAll(initProps);
		//if(newProps.size()>0) {
			log.debug("["+ds.getSyntaxId()+"] parameter props: "+newProps);
			modifiedProps.putAll(newProps);
		/*}
		else {
			//log.info("["+ds.getSyntaxId()+"] no parameter props");
			return;
		}*/
		ds.procProperties(modifiedProps);
	}
	
	/*
	 * reconstructs URL passed on RequestSpec
	 * 
	 * will not use: output syntax, method?, updateValues
	 * XXX may use: columns, offset, limit, filters, order??
	 */
	public String getCanonicalUrl(Properties prop) {
		String base = prop.getProperty(QueryOn.PROP_BASE_URL, "");
		if(!base.endsWith("/")) { base += "/"; }
		String url = base + object + "/";
		//url = url.replaceAll("\\/+", "\\/");
		String params = Utils.join(this.params, "/");
		return url+params;
	}
	
	public List<Object> getParams() {
		return params;
	}
	
	public Map<String, String> getUpdateValues() {
		return updateValues;
	}
	
	static String getSyntax(String lastUriPart, DumpSyntaxUtils dsutils, Properties prop) {
		String outputTypeStr = null;
		
		int lastDotIndex = lastUriPart!=null ? lastUriPart.lastIndexOf('.') : -1;
		String outputTypeStrTmp = null;
		DumpSyntaxInt ds = null;
		if(lastDotIndex > -1) {
			int last2DotIndex = lastUriPart.lastIndexOf('.', lastDotIndex-1);
			outputTypeStrTmp = lastUriPart.substring(lastDotIndex+1);
			log.debug("lastUriPart: "+lastUriPart+" ; outputTypeStrTmp: "+outputTypeStrTmp+"; lastDotIndex="+lastDotIndex+"; last2DotIndex="+last2DotIndex);
			
			// test for known syntax
			if(last2DotIndex > -1) {
				String output2TypeStrTmp = lastUriPart.substring(last2DotIndex+1);
				//log.debug("output2TypeStrTmp: "+output2TypeStrTmp);
				ds = dsutils.getDumpSyntax(output2TypeStrTmp);
				if(ds != null) {
					outputTypeStr = output2TypeStrTmp;
				}
			}
			
			if(ds==null) {
				ds = dsutils.getDumpSyntax(outputTypeStrTmp);
				if(ds != null) {
					outputTypeStr = outputTypeStrTmp;
				}
			}
		}
		else {
			List<String> ignoreFileExt = Arrays.asList(standardFileExt);
			if(ignoreFileExt.contains(outputTypeStrTmp)) {
				outputTypeStr = outputTypeStrTmp;
			}
		}
		return outputTypeStr;
	}

	static String getRequestBody(HttpServletRequest req) throws IOException {
		InputStream is = req.getInputStream();
		return StringUtils.readInputStream(is, 8192);
	}
	
	boolean isContentTypeMultiPart() {
		return contentType!=null && contentType.startsWith(MULTIPART);
	}

	boolean isContentTypeJson() {
		return contentType!=null && contentType.startsWith(ResponseSpec.MIME_TYPE_JSON);
	}
	
	// http://stackoverflow.com/a/2424824/616413
	static String getSubmittedFileName(Part part) {
		for (String cd : part.getHeader(HEADER_CONTENT_DISPOSITION).split(";")) {
			if (cd.trim().startsWith("filename")) {
				String fileName = cd.substring(cd.indexOf('=') + 1).trim().replace("\"", "");
				return fileName.substring(fileName.lastIndexOf('/') + 1).substring(fileName.lastIndexOf('\\') + 1);
			}
		}
		return null;
	}
	
	void showDebugInfo(Map<String,String[]> reqParams) {
		//log.info("debug: object: "+object+" / partz: "+URIpartz);
		log.info("debug: limit/offset: "+limit+"/"+offset);
		//final String loStrategy;
		log.info("debug: contentType: "+contentType);
		log.info("debug: minUpdates/maxUpdates: "+minUpdates+"/"+maxUpdates);
		log.info("debug: params: "+params);
		for(String par: reqParams.keySet()) {
			log.info("debug: reqParams["+par+"]: "+Arrays.toString(reqParams.get(par)));
		}
		log.info("debug: keyValues: "+keyValues);
		log.info("debug: updateValues: "+updateValues);
		log.info("debug: updatePartValues: "+updatePartValues);
	}
	
	@Override
	public String toString() {
		return this.getClass().getSimpleName()+"[object="+object+";params="+params+";model="+modelId+"]";
	}
	
	public String getRemoteInfo() {
		if(request!=null) {
			return request.getRemoteUser() + "@" +
				request.getRemoteHost()+"/"+request.getRemoteAddr();
		}
		return "?";
	}
	
	protected void addAllWithTrim(List<String> list, String values) {
		String[] varr = values.split(",");
		for(String v: varr) {
			list.add(v.trim());
		}
	}
	
	public static boolean getBoolValue(String s) {
		return s!=null && (s.equalsIgnoreCase("t") || s.equals("true") || s.equals("TRUE") || s.equals("1"));
	}
	
	public void setNamedParameters(SQL sql) {
		//log.debug("setNamedParameters: "+sql.namedParameters);
		if(sql.namedParameters!=null) {
			int originalParamsCount = this.params.size();
			int namedParametersBound = 0;
			Map<String, String> parameterMap = getParameterMapUniqueValues();
			for(int i=0;i<sql.namedParameters.size();i++) {
				String param = sql.namedParameters.get(i);
				String value = parameterMap.get(param);
				if(value!=null) {
					//log.debug("- param: "+param+" ; value: "+value);
					int i2 = i+1;
					if(originalParamsCount >= i2) {
						String message = "named parameter '"+param+"' present but #"+i2+" positional parameter already exists [originalParamsCount="+originalParamsCount+"]";
						log.warn(message);
						throw new BadRequestException(message);
					}
					else {
						//XXX sql.bindParameterValues.add(value) ? this.namedParameters.add(value) ?
						this.params.add(value);
						namedParametersBound++;
					}
				}
				else if(sql.bindNullOnMissingParameters!=null && sql.bindNullOnMissingParameters[i]) {
					if(this.params.size()<i) {
						throw new BadRequestException("can't bind null on parameter '"+param+"' ["+i+"] if previous parameters are not bound [size=="+this.params.size()+"]");
					}
					this.params.add(null);
					//boundParameters++;
					//log.info("binding null on parameter '"+param+"' ["+i+"]");
				}
				else if(namedParametersBound>0) {
					throw new BadRequestException("named parameter '"+param+"' not present but "+namedParametersBound+" named parameters already bound");
				}
			}
		}
	}
	
	public Map<String, Map<String, ? extends Object>> getMapFilters() {
		Map<String, Map<String, ? extends Object>> ret = new LinkedHashMap<String, Map<String, ? extends Object>>();
		ret.put("feq", filterEquals);
		ret.put("fne", filterNotEquals);
		ret.put("fgt", filterGreaterThan);
		ret.put("fge", filterGreaterOrEqual);
		ret.put("flt", filterLessThan);
		ret.put("fle", filterLessOrEqual);
		ret.put("fin", filterIn);
		ret.put("fnin", filterLike);
		ret.put("flk", filterLike);
		ret.put("fnlk", filterNotLike);
		return ret;
	}
	
	public Map<String, Set<String>> getSetFilters() {
		Map<String, Set<String>> ret = new LinkedHashMap<String, Set<String>>();
		ret.put("fnull", filterNull);
		ret.put("fnotnull", filterNotNull);
		return ret;
	}
	
	public Integer getUpdateSucessStatus() {
		return null;
	}

	public Integer getDeleteSucessStatus() {
		return null;
	}

	boolean isHeadMethod() {
		return QueryOn.METHOD_HEAD.equals(httpMethod);
	}
	
	/*public Map<String, String[]> getParameterMap() {
		return request.getParameterMap();
	}*/

	public Map<String, String[]> getParameterMap() {
		return request.getParameterMap();
	}
	
	public Map<String, String> getParameterMapUniqueValues() {
		Map<String, String> ret = new HashMap<String, String>();
		for(Map.Entry<String, String[]> e: getParameterMap().entrySet()) {
			String[] vals = e.getValue();
			ret.put(e.getKey(), vals!=null && vals.length>0 ? vals[0] : null);
		}
		return ret;
	}
	
	public Object getAttribute(String name) {
		return request.getAttribute(name);
	}

	public void setAttribute(String name, Object o) {
		request.setAttribute(name, o);
	}
	
	public String getRequestFullContext() {
		return WebUtils.getRequestFullContext(request);
	}
	
	public String getObject() {
		return object;
	}

	public String getModelId() {
		return modelId;
	}
	
	protected String getValueForUpdate(Object o) {
		if(o instanceof Double) {
			Double d = (Double) o;
			if(d.doubleValue() % 1 == 0) { return String.valueOf(d.longValue()); }
		}
		return String.valueOf(o);
	}

	public DumpSyntaxInt getOutputSyntax() {
		return outputSyntax;
	}
}
