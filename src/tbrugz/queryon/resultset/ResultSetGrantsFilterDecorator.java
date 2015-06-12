package tbrugz.queryon.resultset;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.sqldump.dbmodel.Grant;
import tbrugz.sqldump.dbmodel.PrivilegeType;
import tbrugz.sqldump.util.Utils;

public class ResultSetGrantsFilterDecorator extends AbstractResultSetFilterDecorator {
	
	static final Log log = LogFactory.getLog(ResultSetGrantsFilterDecorator.class);

	final Set<String> roles;
	final PrivilegeType privilege;
	final String idColumn;
	final String grantsColumn;
	//final int maxCol;
	
	public ResultSetGrantsFilterDecorator(ResultSet rs, Set<String> roles, PrivilegeType privilege, String idColumn, String grantsColumn) throws SQLException {
		super(rs);
		this.roles = roles;
		this.privilege = privilege;
		this.idColumn = idColumn;
		this.grantsColumn = grantsColumn;
		//maxCol = rs.getMetaData().getColumnCount();
	}

	@Override
	boolean matchesValues() throws SQLException {
		//String id = rs.getString(idColumn);
		String grantsStr = rs.getString(grantsColumn);
		if(grantsStr==null || grantsStr.length()<2) {
			//log.info("RSGrantsFilter:matchesValues ["+id+"]:: "+grantsStr+" [roles:"+roles+"]:: true [1]");
			return true;
		}
		grantsStr = grantsStr.substring(1, grantsStr.length()-1); //removing array braquets "[]"
		
		List<String> grants = Utils.getStringList(grantsStr, ",");
		//log.info("RSGrantsFilter:matchesValues ["+id+"]:: "+grantsStr+" [roles:"+roles+"]");
		//XXX: what if columns does not exists???
		if(grants==null || grants.size()==0 || (grants.size()==1 && grants.get(0).equals("")) ) {
			//log.info("RSGrantsFilter:matchesValues ["+id+"]:: "+grantsStr+" [roles:"+roles+"]:: true [2]");
			return true;
		}
		//log.info("grants.size() ["+id+"]: "+grants.size()+" ;; "+grants); 
		
		boolean containsRowWithPrivilege = false; //if row has no privilege of 'this.privilege' type, it should match (not restricted)
		for(int i=0;i<grants.size();i++) {
			Grant gr = Grant.parseGrant(grants.get(i));
			//log.info("..RSGrantsFilter: grantee=="+gr.getGrantee()+" <<"+grants.get(i)+">> [?="+roles.contains(gr.getGrantee())+"]");
			if(gr==null) { continue; }
			if(!containsRowWithPrivilege && gr.getPrivilege().equals(privilege)) {
				containsRowWithPrivilege = true;
			}
			if(roles.contains(gr.getGrantee()) && privilege.equals(gr.getPrivilege())) {
				//log.info("RSGrantsFilter:matchesValues ["+id+"]:: "+grantsStr+" [roles:"+roles+"]:: true [ok]");
				return true;
			}
		}
		boolean ret = containsRowWithPrivilege?false:true;
		//log.info("RSGrantsFilter:matchesValues ["+id+"]:: "+grantsStr+" [roles:"+roles+"]:: "+ret);
		return ret;
	}

}
