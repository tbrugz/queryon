package tbrugz.queryon.resultset;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.sqldump.resultset.AbstractResultSetDecorator;
import tbrugz.sqldump.resultset.ResultSetProjectionDecorator;
import tbrugz.sqldump.util.SQLUtils;

public class UnionResultSet extends AbstractResultSetDecorator {//ResultSetProjectionDecorator {

	private static final Log log = LogFactory.getLog(UnionResultSet.class);
	
	final List<ResultSet> resultSets = new ArrayList<ResultSet>();
	final List<String> colNames = new ArrayList<String>();
	int currentResultSet = 0;
	
	public UnionResultSet(List<ResultSet> resultSets) throws SQLException {
		this(resultSets, false);
	}
	
	public UnionResultSet(List<ResultSet> resultSets, boolean intersectColumns) throws SQLException {
		super(resultSets.get(0));
		//super(resultSets.get(0), getColumns(resultSets, intersectColumns), true);
		//updateCurrentResultSet();
		log.debug("UnionResultSet ["+currentResultSet+"]: "+SQLUtils.getColumnNamesAsList(resultSets.get(0).getMetaData()));
		//this.resultSets = resultSets;
		//this.colNames.addAll(this.name2colMap.keySet());
		this.colNames.addAll(getColumns(resultSets, intersectColumns));
		for(ResultSet rs: resultSets) {
			this.resultSets.add(new ResultSetProjectionDecorator(rs, this.colNames, !intersectColumns));
		}
		rs = this.resultSets.get(currentResultSet);
	}
	
	//XXX: what about column types?
	static List<String> getColumns(List<ResultSet> resultSets, boolean intersectColumns) throws SQLException {
		Set<String> uniqueColNames = new LinkedHashSet<String>();
		for(int i=0;i<resultSets.size();i++) {
			ResultSet rs = resultSets.get(i);
			ResultSetMetaData rsmd = rs.getMetaData();
			List<String> colNames = SQLUtils.getColumnNamesAsList(rsmd);
			log.debug("rs"+i+": cols="+colNames);
			if(i==0 || !intersectColumns) {
				uniqueColNames.addAll(colNames);
			}
			else {
				uniqueColNames.retainAll(colNames);
			}
		}

		List<String> retColNames = new ArrayList<String>();
		retColNames.addAll(uniqueColNames);
		log.debug("cols: "+uniqueColNames);
		return retColNames;
	}
	
	void updateCurrentResultSet() throws SQLException {
		//ResultSetProjectionDecorator urs = new ResultSetProjectionDecorator(resultSets.get(currentResultSet), colNames, true);
		ResultSet urs = resultSets.get(currentResultSet);

		log.debug("updateCurrentResultSet ["+currentResultSet+"]: "+SQLUtils.getColumnNamesAsList(urs.getMetaData()));
		rs = urs;
	}
	
	@Override
	public boolean next() throws SQLException {
		boolean next = super.next();
		while(!next && (currentResultSet+1 < resultSets.size()) ) {
			//rs = resultSets.get(++currentResultSet);
			//rs = new ResultSetProjectionDecorator(resultSets.get(++currentResultSet), colNames, true);
			currentResultSet++;
			updateCurrentResultSet();
			next = super.next();
		}
		return next;
	}
	
	/*
	@Override
	public Object getObject(int columnIndex) throws SQLException {
		if(columnIndex<1) {
			log.info("getObject idx "+columnIndex);
			return null;
		}
		return super.getObject(columnIndex);
	}
	
	@Override
	public String getString(int columnIndex) throws SQLException {
		if(columnIndex<1) {
			log.info("getString idx "+columnIndex);
			return null;
		}
		return super.getString(columnIndex);
	}
	*/

}
