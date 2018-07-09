package tbrugz.queryon.graphql;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import tbrugz.sqldump.datadump.AbstractDumpSyntax;
import tbrugz.sqldump.datadump.DumpSyntaxBuilder;
import tbrugz.sqldump.util.SQLUtils;

/*
 * https://graphql.org/learn/serving-over-http/#post-request
 * 
 * mime-type: application/json ? application/graphql ?
 */
public class GqlMapBufferSyntax extends AbstractDumpSyntax implements DumpSyntaxBuilder, Cloneable /* implements WebSyntax */ {

	public static final String GRAPHQL_ID = "graphql-buffer";
	
	public static final String MIME_TYPE = "application/json";
	
	List<Map<String, Object>> maplist = new ArrayList<>();
	
	@Override
	public void procProperties(Properties prop) {
	}

	@Override
	public String getSyntaxId() {
		return GRAPHQL_ID;
	}

	@Override
	public String getMimeType() {
		return MIME_TYPE;
	}

	@Override
	public void dumpHeader(Writer fos) throws IOException {
		//System.out.println("GqlMapBufferSyntax: dumpHeader...");
	}

	@Override
	public void dumpRow(ResultSet rs, long count, Writer fos) throws IOException, SQLException {
		Map<String, Object> m = new HashMap<>();
		List<Object> vals = SQLUtils.getRowObjectListFromRS(rs, lsColTypes, numCol);
		for(int i=0;i<vals.size();i++) {
			m.put(lsColNames.get(i), vals.get(i));
		}
		maplist.add(m);
	}

	@Override
	public void dumpFooter(long count, boolean hasMoreRows, Writer fos) throws IOException {
	}
	
	public List<Map<String, Object>> getBuffer() {
		return maplist;
	}

	/*
	@Override
	public void setBaseHref(String href) {
	}

	@Override
	public void setLimit(long limit) {
	}

	@Override
	public void setOffset(long offset) {
	}
	*/

}
