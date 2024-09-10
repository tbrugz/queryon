package tbrugz.queryon.util;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class OutputStreamDebugger extends OutputStream {

	private static final Log log = LogFactory.getLog(OutputStreamDebugger.class);

	final OutputStream os;
	int count = 0;
	
	public OutputStreamDebugger(OutputStream os) {
		this.os = os;
	}
	
	@Override
	public void write(int b) throws IOException {
		os.write(b);
		count++;
	}

	@Override
	public void close() throws IOException {
		log.info("written "+count+" bytes");
		super.close();
	}

}
