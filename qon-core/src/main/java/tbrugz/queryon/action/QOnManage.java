package tbrugz.queryon.action;

import java.io.IOException;
import java.sql.Connection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import tbrugz.queryon.ResponseSpec;
import tbrugz.queryon.diff.DiffManyServlet;
import tbrugz.queryon.diff.DiffUtilQon;
import tbrugz.sqldiff.SQLDiff;
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
import tbrugz.sqldump.def.Defs;
import tbrugz.sqldump.def.Processor;
import tbrugz.sqldump.processors.SQLDialectTransformer;
import tbrugz.sqldump.util.CategorizedOut;
import tbrugz.sqldump.util.Utils;

public class QOnManage {

	private static final Log log = LogFactory.getLog(QOnManage.class);
	
	public static final String ACTION_DIFF = "diffmodel";
	public static final String ACTION_RELOAD = "reload";
	
	public static final String DIFF_SUBACTION_SHOW = "show";
	public static final String DIFF_SUBACTION_APPLY = "applydiff";

	static final boolean transformDialectBackAndForth = false;
	
	public void diffModel(SchemaModel model, Connection conn, boolean apply, HttpServletResponse resp) throws IOException {
		//XXX param: schemas, validate?
		//log.info("starting diff");
		
		Properties grabProps = new Properties();
		grabProps.put(JDBCSchemaGrabber.PROP_SCHEMAGRAB_PROCEDURESANDFUNCTIONS, "false");
		grabProps.put(JDBCSchemaGrabber.PROP_SCHEMAGRAB_DBSPECIFIC, "true");
		//grabProps.put(AbstractDBMSFeatures.PROP_GRAB_CONSTRAINTS_XTRA, "false"); //XXX: add xtra-constraints?
		//grabProps.put(Defs.PROP_SCHEMAGRAB_SCHEMANAMES, "public");
		grabProps.put(Defs.PROP_SCHEMAGRAB_SCHEMANAMES, Utils.join(getModelSchemas(model), ", "));
		List<String> typesList = Arrays.asList(new String[]{"TABLE", "FK", "CONSTRAINT"});
		DiffManyServlet.setPropForTypes(grabProps, typesList);
		//log.debug("grab props: "+grabProps);

		JDBCSchemaGrabber jsg = new JDBCSchemaGrabber();
		jsg.setConnection(conn);
		jsg.setProperties(grabProps);
		SchemaModel dbModel = jsg.grabSchema();
		String dialect = dbModel.getSqlDialect();
		
		if(transformDialectBackAndForth) {
			log.info("diffModel: SQLDialectTransformer: todbid dialect="+dialect);
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
		}
		
		SchemaDiffer differ = new SchemaDiffer();
		DBMSFeatures feat = DBMSResources.instance().getSpecificFeatures(dialect);
		ColumnDiff.updateFeatures(feat);
		log.debug("dialect: "+dialect+" ; feats: "+feat);
		
		differ.setTypesForDiff("TABLE");
		SchemaDiff diff = differ.diffSchemas(dbModel, model);
		diff.getGrantDiffs().clear(); //do not dump Grant diffs

		// remove Table & Column remarks diff
		int removed = 0;
		{
			Iterator<TableDiff> it = diff.getTableDiffs().iterator();
			while(it.hasNext()) {
				TableDiff td = it.next();
				if(td.getChangeType().equals(ChangeType.REMARKS)) {
					log.debug("will not generate diff for "+td);
					it.remove();
					removed++;
				}
				if(td.getChangeType().equals(ChangeType.DROP)) {
					log.debug("will not generate diff for "+td);
					it.remove();
					removed++;
				}
			}
		}
		{
			Iterator<ColumnDiff> it = diff.getColumnDiffs().iterator();
			while(it.hasNext()) {
				ColumnDiff cd = it.next();
				if(cd.getChangeType().equals(ChangeType.REMARKS)) {
					it.remove();
					removed++;
				}
				if(cd.getChangeType().equals(ChangeType.DROP)) {
					it.remove();
					removed++;
				}
			}
		}
		log.info("diff: removed "+removed+" diffs");
		
		SchemaDiff.logInfo(diff);
		diff.compact();
		SchemaDiff.logInfo(diff);
		
		int diffcount = diff.getDiffListSize();
		// output diff
		if(!apply && resp!=null) {
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
		// apply...
		if(apply) {
			log.info("diff: will apply "+diff.getChildren().size()+" diffs");
			boolean addComments = true;
			DiffUtilQon.applyDiffs(diff.getChildren(), conn, model.getModelId(), addComments, resp);
			log.info("diff: applyed diffs");
		}
		//log.info("diff finished");
	}
	
	static Set<String> getModelSchemas(SchemaModel model) {
		Set<String> names = new HashSet<String>();
		for(Table t: model.getTables()) {
			names.add( t.getSchemaName() );
		}
		return names;
	}

	public static boolean isSubactionValid(String subaction) {
		return
			QOnManage.DIFF_SUBACTION_SHOW.equals(subaction) ||
			QOnManage.DIFF_SUBACTION_APPLY.equals(subaction)
			;
	}
	
}
