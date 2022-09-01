package tbrugz.queryon;

/*
 * http://stackoverflow.com/questions/3561381/custom-http-headers-naming-conventions
 */
public class ResponseSpec {

	public static final String MIME_TYPE_TEXT_PLAIN = "text/plain";
	public static final String MIME_TYPE_OCTET_SREAM = "application/octet-stream"; // https://www.iana.org/assignments/media-types/application/octet-stream
	
	public static final String MIME_TYPE_JSON = "application/json";
	public static final String MIME_TYPE_XML = "application/xml";
	
	// --- standard HTTP response headers ---
	public static final String HEADER_ALLOW = "Allow";
	public static final String HEADER_ACCEPT_RANGES = "Accept-Ranges";
	public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
	public static final String HEADER_CONTENT_LOCATION = "Content-Location";
	public static final String HEADER_CONTENT_RANGE = "Content-Range";
	public static final String HEADER_LOCATION = "Location";
	//public static final String HEADER_CONTENT_TYPE = "Content-Type"; // not needed... use HttpServletResponse.setContentType()
	
	//https://en.wikipedia.org/wiki/List_of_HTTP_header_fields ; https://en.wikipedia.org/wiki/Chunked_transfer_encoding
	//see: Content-Encoding: gzip? , Content-Length, Trailer, Warning, X-XSS-Protection
	
	// --- queryon response headers ---
	public static final String HEADER_WARNING = "X-Warning"; //XXX: remove the "X-"?
	public static final String HEADER_WARNING_UNKNOWN_COLUMN = "X-Warning-UnknownColumn";
	public static final String HEADER_WARNING_SQL_POSITION = "X-Warning-SQL-Position";
	public static final String HEADER_WARNING_SQL_LINE = "X-Warning-SQL-Line";
	
	public static final String HEADER_RELATION_UK_VALUES = "X-Relation-UK-Values";
	public static final String HEADER_RELATION_UK = "X-Relation-UK";
	public static final String HEADER_RELATION_COLUMNS = "X-Relation-Columns";
	
	public static final String HEADER_RESULTSET_LIMIT = "X-ResultSet-Limit";

	public static final String HEADER_UPDATECOUNT = "X-UpdateCount";
	
	public static final String HEADER_EXECUTE_RETURNCOUNT = "X-Execute-ReturnCount";
	
	public static final String HEADER_VALIDATE_PARAMCOUNT = "X-Validate-ParameterCount";
	public static final String HEADER_VALIDATE_PARAMTYPES = "X-Validate-ParameterTypes";
	public static final String HEADER_VALIDATE_NAMED_PARAMETER_NAMES = "X-Validate-NamedParameterNames";
	public static final String HEADER_VALIDATE_OPTIONAL_PARAMS = "X-Validate-OptionalParameters";
	
	public static final String HEADER_DIFFCOUNT = "X-DiffCount";

	//---
	
	public static final String HEADERVALUE_CONTENT_DISPOSITION_INLINE = "inline";
	public static final String HEADERVALUE_ACCEPT_RANGES_BYTES = "bytes";
	
}
