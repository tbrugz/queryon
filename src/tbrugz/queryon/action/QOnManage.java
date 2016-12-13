package tbrugz.queryon.action;

import java.io.IOException;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

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
import tbrugz.sqldump.def.DBMSResources;
import tbrugz.sqldump.def.Processor;
import tbrugz.sqldump.processors.SQLDialectTransformer;
import tbrugz.sqldump.util.CategorizedOut;

public class QOnManage {

	private static final Log log = LogFactory.getLog(QOnManage.class);
	
	public void diffModel(SchemaModel model, Connection conn, HttpServletResponse resp) throws IOException {
		Properties pp = new Properties();
		pp.put("sqldump.schemagrab.proceduresandfunctions", "false");
		//pp.put("sqldump.schemagrab.db-specific-features", "false");
		//pp.put(AbstractDBMSFeatures.PROP_GRAB_CONSTRAINTS_XTRA, "false"); //XXX: add xtra-constraints?
		
		//sqldump properties...
		//pp.put("sqldump.schemagrab.schemas", "public"); //XXX param: schemas, validate?
		List<String> typesList = Arrays.asList(new String[]{"TABLE"});
		DiffManyServlet.setPropForTypes(pp, typesList);
		
		JDBCSchemaGrabber jsg = new JDBCSchemaGrabber();
		jsg.setConnection(conn);
		jsg.setProperties(pp);
		SchemaModel dbModel = jsg.grabSchema();
		String dialect = dbModel.getSqlDialect();
		
		Processor transf = new SQLDialectTransformer();
		transf.setSchemaModel(dbModel);
		Properties p = new Properties();
		p.setProperty("sqldump.schematransform.toansi", "true");
		transf.setProperties(p);
		transf.process();

		Properties p2 = new Properties();
		p2.setProperty("sqldump.schematransform.todbid", dialect);
		transf.setProperties(p2);
		transf.process();
		
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
		int diffcount = diff.getChildren().size();
		resp.setIntHeader(ResponseSpec.HEADER_DIFFCOUNT, diffcount);
		/*if(diffcount==0) {
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		}*/
		CategorizedOut cout = new CategorizedOut(resp.getWriter(), null);
		diff.outDiffs(cout);
	}
	
}
