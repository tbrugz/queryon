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
	
	@Test
	public void testLimitString() {
		String s0 = "aaaa5aaaa1aaaa5aaaa2";

		{
			String s1 = MiscUtils.limitString(s0, 40);
			//System.out.println("s0: "+s0+" ; s1: "+s1);
			Assert.assertEquals(s0, s1);
		}

		{
			String s2 = MiscUtils.limitString(s0, 6);
			//System.out.println("s0: "+s0+" ; s2: "+s2);
			Assert.assertEquals("aaaa5a".length(), s2.length());
			//Assert.assertEquals("aaa...", s2);
			Assert.assertEquals("aaaa5"+"\u2026", s2);
		}

		{
			String s3 = MiscUtils.limitString(s0, 20);
			//System.out.println("s0: "+s0+" ; s3: "+s3);
			Assert.assertEquals(s0, s3);
		}
	}
	
}
