package tbrugz.queryon.diff;

import java.util.Properties;

public interface ApplyHook {
	
	// message, date?, username, object-type, object-schema, object-name ;; model-apply, model-base
	public class ApplyMessage {
		public final String message;
		public final String username;
		
		public final String objectType;
		public final String objectSchema;
		public final String objectName;
		
		public final String modelBase;
		public final String modelApply;
		
		public ApplyMessage(String message, String username, String objectType, String objectSchema, String objectName, String modelBase, String modelApply) {
			this.message = message;
			this.username = username;
			this.objectType = objectType;
			this.objectSchema = objectSchema;
			this.objectName = objectName;
			this.modelBase = modelBase;
			this.modelApply = modelApply;
		}
	}

	public void setProperties(Properties prop); // equals to AbstractProcessor's

	public void setId(String id);
	
	public String getPropPrefix();
	
	public String run(ApplyMessage am);
	
}
