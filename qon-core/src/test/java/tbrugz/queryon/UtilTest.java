package tbrugz.queryon;

import org.junit.Assert;
import org.junit.Test;

import tbrugz.queryon.util.MiscUtils;

public class UtilTest {

	@Test
	public void testReplaceMultiSlash() {
		String s1 = "//a/b//ccc";
		String s2 = MiscUtils.removeMultiSlash(s1);
		//System.out.println("s1: "+s1+" ; s2: "+s2);
		Assert.assertEquals("/a/b/ccc", s2);
	}
	
}
