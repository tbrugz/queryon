package tbrugz.queryon.util;

import org.junit.Assert;
import org.junit.Test;


import tbrugz.queryon.processor.QOnTables;
import tbrugz.sqldump.util.StringDecorator;

public class StringUtils {

	@Test
	public void testStringQuoterEscaperDecorator() {
		StringDecorator sd = new QOnTables.StringQuoterEscaperDecorator("'");
		Assert.assertEquals("'lala'", sd.get("la'la"));
	}
	
}
