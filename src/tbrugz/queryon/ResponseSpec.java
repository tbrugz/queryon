package tbrugz.queryon;

public class ResponseSpec {

	// --- standard HTTP response headers ---
	public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
	public static final String HEADER_CONTENT_LOCATION = "Content-Location";
	//public static final String HEADER_CONTENT_TYPE = "Content-Type"; // not needed... use HttpServletResponse.setContentType()
	
	//https://en.wikipedia.org/wiki/List_of_HTTP_header_fields ; https://en.wikipedia.org/wiki/Chunked_transfer_encoding
	//see: Content-Encoding: gzip? , Content-Length, Trailer, Warning, X-XSS-Protection
	
	// --- queryon response headers ---
	public static final String HEADER_WARNING = "X-Warning"; //XXX: remove the "X-"?
	public static final String HEADER_WARNING_UNKNOWN_COLUMN = "X-Warning-UnknownColumn";
	
	public static final String HEADER_RELATION_UK_VALUES = "X-Relation-UK-Values";
	public static final String HEADER_RELATION_UK = "X-Relation-UK";
	
	public static final String HEADER_RESULTSET_LIMIT = "X-ResultSet-Limit";
	
	public static final String HEADER_EXECUTE_RETURNCOUNT = "X-Execute-ReturnCount";
	
	public static final String HEADER_VALIDATE_PARAMCOUNT = "X-Validate-ParameterCount";
	public static final String HEADER_VALIDATE_PARAMTYPES = "X-Validate-ParameterTypes";
	
}
