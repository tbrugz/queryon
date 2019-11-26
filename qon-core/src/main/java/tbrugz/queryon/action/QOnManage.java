package tbrugz.queryon.action;

import java.io.IOException;
import java.sql.Connection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.ResponseSpec;
import tbrugz.queryon.diff.DiffManyServlet;
import tbrugz.sqldiff.SchemaDiffer;
import tbrugz.sqldiff.model.ChangeType;
import tbrugz.sqldiff.model.ColumnDiff;
import tbrugz.sqldiff.model.SchemaDiff;
import tbrugz.sqldiff.model.TableDiff;
import tbrugz.sqldump.JDBCSchemaGrabber;
import tbrugz.sqldump.dbmd.DBMSFeatures;
import tbrugz.sqldump.dbmodel.SchemaModel;
import tbrugz.sqldump.dbmodel.Table;
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.def.Processor;
import tbrugz.sqldump.processors.SQLDialectTransformer;
import tbrugz.sqldump.util.CategorizedOut;
import tbrugz.sqldump.util.Utils;

public class QOnManage {

	private static final Log log = LogFactory.getLog(QOnManage.class);
	
	public static final String ACTION_DIFF = "diffmodel";
	public static final String ACTION_RELOAD = "reload";
	
	public void diffModel(SchemaModel model, Connection conn, HttpServletResponse resp) throws IOException {
		//XXX param: schemas, validate?
		
		Properties grabProps = new Properties();
		grabProps.put("sqldump.schemagrab.proceduresandfunctions", "false");
		//grabProps.put("sqldump.schemagrab.db-specific-features", "false");
		//grabProps.put(AbstractDBMSFeatures.PROP_GRAB_CONSTRAINTS_XTRA, "false"); //XXX: add xtra-constraints?
		//grabProps.put("sqldump.schemagrab.schemas", "public");
		grabProps.put("sqldump.schemagrab.schemas", Utils.join(getModelSchemas(model), ", "));
		List<String> typesList = Arrays.asList(new String[]{"TABLE"});
		DiffManyServlet.setPropForTypes(grabProps, typesList);
		
		JDBCSchemaGrabber jsg = new JDBCSchemaGrabber();
		jsg.setConnection(conn);
		jsg.setProperties(grabProps);
		SchemaModel dbModel = jsg.grabSchema();
		String dialect = dbModel.getSqlDialect();
		
		Processor transf = new SQLDialectTransformer();
		transf.setSchemaModel(dbModel);
		
		{
			Properties transformProps = new Properties();
			transformProps.setProperty("sqldump.schematransform.toansi", "true");
			transf.setProperties(transformProps);
			transf.process();
		}

		{
			Properties transformProps = new Properties();
			transformProps.setProperty("sqldump.schematransform.todbid", dialect);
			transf.setProperties(transformProps);
			transf.process();
		}
		
		SchemaDiffer differ = new SchemaDiffer();
		DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(dialect);
		ColumnDiff.updateFeatures(feat);
		log.info("feats: "+feat);
		
		differ.setTypesForDiff("TABLE");
		SchemaDiff diff = differ.diffSchemas(dbModel, model);
		diff.getGrantDiffs().clear(); //do not dump Grant diffs

		// remove Table & Column remarks diff
		int removed = 0;
		for(TableDiff td: diff.getTableDiffs()) {
			if(td.getChangeType().equals(ChangeType.REMARKS)) {
				diff.getTableDiffs().remove(td);
				removed++;
			}
		}
		for(ColumnDiff cd: diff.getColumnDiffs()) {
			if(cd.getChangeType().equals(ChangeType.REMARKS)) {
				diff.getColumnDiffs().remove(cd);
				removed++;
			}
		}
		log.info("diff: removed "+removed+" diffs");
		
		SchemaDiff.logInfo(diff);
		diff.compact();
		SchemaDiff.logInfo(diff);
		
		int diffcount = diff.getDiffListSize();
		resp.setIntHeader(ResponseSpec.HEADER_DIFFCOUNT, diffcount);
		if(diffcount==0) {
			//resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			resp.getWriter().write("-- no diffs found");
		}
		else {
			CategorizedOut cout = new CategorizedOut(resp.getWriter(), null);
			diff.outDiffs(cout);
		}
	}
	
	static Set<String> getModelSchemas(SchemaModel model) {
		Set<String> names = new HashSet<String>();
		for(Table t: model.getTables()) {
			names.add( t.getSchemaName() );
		}
		return names;
	}
	
}
