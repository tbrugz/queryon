package tbrugz.queryon.resultset;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;

import tbrugz.sqldump.resultset.AbstractResultSet;
import tbrugz.sqldump.resultset.RSMetaDataTypedAdapter;

/*
 * http://docs.oracle.com/javase/7/docs/api/java/sql/DatabaseMetaData.html#getColumns(java.lang.String,%20java.lang.String,%20java.lang.String,%20java.lang.String)
 * http://docs.oracle.com/javase/7/docs/api/java/sql/ResultSetMetaData.html
 * 
x! TABLE_CAT String => table catalog (may be null)
  getCatalogName(int column)
x! TABLE_SCHEM String => table schema (may be null)
  getSchemaName(int column)
x! TABLE_NAME String => table name
  getTableName(int column)
! COLUMN_NAME String => column name
  getColumnName(int column)
->  "COLUMN_LABEL", "CLASS_NAME"
  getColumnLabel()
  getColumnClassName()
! DATA_TYPE int => SQL type from java.sql.Types
  getColumnType(int column)
! TYPE_NAME String => Data source dependent type name, for a UDT the type name is fully qualified
  getColumnTypeName(int column)
! COLUMN_SIZE int => column size.
  getPrecision(int column)
xx BUFFER_LENGTH is not used.
! DECIMAL_DIGITS int => the number of fractional digits. Null is returned for data types where DECIMAL_DIGITS is not applicable.
  getScale(int column)
? NUM_PREC_RADIX int => Radix (typically either 10 or 2)
! NULLABLE int => is NULL allowed.
  isNullable(int column)
  .columnNoNulls - might not allow NULL values
  .columnNullable - definitely allows NULL values
  .columnNullableUnknown - nullability unknown
x REMARKS String => comment describing column (may be null)
x COLUMN_DEF String => default value for the column, which should be interpreted as a string when the value is enclosed in single quotes (may be null)
xx SQL_DATA_TYPE int => unused
xx SQL_DATETIME_SUB int => unused
x CHAR_OCTET_LENGTH int => for char types the maximum number of bytes in the column
! ORDINAL_POSITION int => index of column in table (starting at 1)
? IS_NULLABLE String => ISO rules are used to determine the nullability for a column.
  .YES --- if the column can include NULLs
  .NO --- if the column cannot include NULLs
  .empty string --- if the nullability for the column is unknown
x SCOPE_CATALOG String => catalog of table that is the scope of a reference attribute (null if DATA_TYPE isn't REF)
x SCOPE_SCHEMA String => schema of table that is the scope of a reference attribute (null if the DATA_TYPE isn't REF)
x SCOPE_TABLE String => table name that this the scope of a reference attribute (null if the DATA_TYPE isn't REF)
x SOURCE_DATA_TYPE short => source type of a distinct type or user-generated Ref type, SQL type from java.sql.Types (null if DATA_TYPE isn't DISTINCT or user-generated REF)
x IS_AUTOINCREMENT String => Indicates whether this column is auto incremented
  .YES --- if the column is auto incremented
  .NO --- if the column is not auto incremented
  .empty string --- if it cannot be determined whether the column is auto incremented
x IS_GENERATEDCOLUMN String => Indicates whether this is a generated column
  .YES --- if this a generated column
  .NO --- if this not a generated column
  .empty string --- if it cannot be determined whether this is a generated column

  XXX: other ResultSetMetaData's methods ?
    getColumnDisplaySize(int column)
    isAutoIncrement(int column)
    isCaseSensitive(int column)
    isReadOnly(int column)
    isSearchable(int column)
    isSigned(int column)
    isWritable(int column)
 */
public class ResultSetMetadata2RsAdapter extends AbstractResultSet {

	//private static final Log log = LogFactory.getLog(ResultSetMetadata2RsAdapter.class);
	
	static final String[] columns = {
		"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "COLUMN_LABEL",
		"CLASS_NAME", "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE", "DECIMAL_DIGITS",
		"NULLABLE", "ORDINAL_POSITION"
		};
	static final Integer[] colTypes = {
		Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
		Types.VARCHAR, Types.INTEGER, Types.VARCHAR, Types.INTEGER, Types.INTEGER,
		Types.INTEGER /*NULLABLE ?*/, Types.INTEGER
		};
	static final List<String> columnsList = Arrays.asList(columns);
	
	final ResultSetMetaData rsmd;
	final ResultSetMetaData metadata;
	final int rowCount;

	int position = -1;
	
	/*
	 * XXX option to show or not "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME"
	 * XXX option to show or not "CLASS_NAME"
	 */
	public ResultSetMetadata2RsAdapter(ResultSetMetaData rsmd) throws SQLException {
		this.rsmd = rsmd;
		rowCount = rsmd.getColumnCount();
		metadata = new RSMetaDataTypedAdapter(null, null, columnsList, Arrays.asList(colTypes));
		//log.debug("cols["+columnsList.size()+"]: "+columnsList+" ; types["+colTypes.length+"]: "+ Arrays.asList(colTypes)+""); 
	}
	
	int getRowCount() {
		return rowCount;
	}
	
	void updateCurrentElement() {
	}
	
	//=============== RS methods - navigation ===============
	
	@Override
	public ResultSetMetaData getMetaData() throws SQLException {
		return metadata;
	}
	
	@Override
	public int getType() throws SQLException {
		return ResultSet.TYPE_SCROLL_SENSITIVE;
	}
	
	@Override
	public void beforeFirst() throws SQLException {
		resetPosition();
	}
	
	@Override
	public boolean first() throws SQLException {
		resetPosition();
		return next();
	}
	
	@Override
	public boolean absolute(int row) throws SQLException {
		if(rowCount>=row) {
			position = row-1;
			updateCurrentElement();
			return true;
		}
		return false;
	}
	
	@Override
	public boolean relative(int rows) throws SQLException {
		int newpos = position + rows + 1;
		if(newpos>0) { return absolute(newpos); }
		return false;
	}

	@Override
	public boolean next() throws SQLException {
		if(rowCount-1 > position) {
			position++;
			updateCurrentElement();
			return true;
		}
		return false;
	}
	
	void resetPosition() {
		position = -1;
		updateCurrentElement();
	}
	
	//=============== / RS methods - navigation ============
	
	@Override
	public Object getObject(String columnLabel) throws SQLException {
		int i = columnsList.indexOf(columnLabel);
		return getObject(i+1);
	}
	
	@Override
	public Object getObject(int columnIndex) throws SQLException {
		//"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "COLUMN_LABEL",
		//"CLASS_NAME", "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE", "DECIMAL_DIGITS",
		//"NULLABLE", "ORDINAL_POSITION"

		switch (columnIndex) {
		case 1:
			return rsmd.getCatalogName(position+1);
		case 2:
			return rsmd.getSchemaName(position+1);
		case 3:
			return rsmd.getTableName(position+1);
		case 4:
			return rsmd.getColumnName(position+1);
		case 5:
			return rsmd.getColumnLabel(position+1);
		case 6:
			return rsmd.getColumnClassName(position+1);
		case 7:
			return rsmd.getColumnType(position+1);
		case 8:
			return rsmd.getColumnTypeName(position+1);
		case 9:
			return rsmd.getPrecision(position+1);
		case 10:
			return rsmd.getScale(position+1);
		case 11:
			return rsmd.isNullable(position+1);
		case 12:
			return position+1;
		default:
			throw new IllegalArgumentException("column "+columnIndex+" does not exist");
		}
	}
	
	@Override
	public String getString(int columnIndex) throws SQLException {
		return getObject(columnIndex).toString();
	}
	
	@Override
	public String getString(String columnLabel) throws SQLException {
		return getObject(columnLabel).toString();
	}
	
	@Override
	public int getInt(int columnIndex) throws SQLException {
		return Integer.parseInt(getObject(columnIndex).toString());
	}
	
	@Override
	public int getInt(String columnLabel) throws SQLException {
		return Integer.parseInt(getObject(columnLabel).toString());
	}
	
	@Override
	public double getDouble(int columnIndex) throws SQLException {
		return Double.parseDouble(getObject(columnIndex).toString());
	}

	@Override
	public double getDouble(String columnLabel) throws SQLException {
		return Double.parseDouble(getObject(columnLabel).toString());
	}
	
	@Override
	public long getLong(int columnIndex) throws SQLException {
		return Long.parseLong(getObject(columnIndex).toString());
	}
	
	@Override
	public long getLong(String columnLabel) throws SQLException {
		return Long.parseLong(getObject(columnLabel).toString());
	}
}
