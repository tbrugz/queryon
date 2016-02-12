package tbrugz.queryon;

public class ResponseSpec {

	// --- standard HTTP headers ---
	public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
	public static final String HEADER_CONTENT_LOCATION = "Content-Location";
	//public static final String HEADER_CONTENT_TYPE = "Content-Type"; // not needed... use HttpServletResponse.setContentType()
	
	// --- queryon headers ---
	public static final String HEADER_WARNING = "X-Warning";
	public static final String HEADER_WARNING_UNKNOWN_COLUMN = "X-Warning-UnknownColumn";
	
	public static final String HEADER_RESULTSET_LIMIT = "X-ResultSet-Limit";
	
	public static final String HEADER_EXECUTE_RETURNCOUNT = "X-Execute-ReturnCount";
	
	public static final String HEADER_VALIDATE_PARAMCOUNT = "X-Validate-ParameterCount";
	public static final String HEADER_VALIDATE_PARAMTYPES = "X-Validate-ParameterTypes";
	
}
