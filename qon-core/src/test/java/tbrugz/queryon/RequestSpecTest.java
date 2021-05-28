package tbrugz.queryon;

import org.junit.Assert;
import org.junit.Test;

public class RequestSpecTest {

	@Test
	public void testRangeOk() {
		long[] range = RequestSpec.parseRange("bytes=1-200");
		Assert.assertEquals(2, range.length);
		Assert.assertEquals(1, range[0]);
		Assert.assertEquals(200, range[1]);
	}

	@Test
	public void testRangeOk2() {
		long[] range = RequestSpec.parseRange("bytes=234-518484");
		Assert.assertEquals(2, range.length);
		Assert.assertEquals(234, range[0]);
		Assert.assertEquals(518484, range[1]);
	}

	@Test
	public void testRangeOpenEnded() {
		long[] range = RequestSpec.parseRange("bytes=1-");
		Assert.assertEquals(2, range.length);
		Assert.assertEquals(1, range[0]);
		Assert.assertEquals(-1, range[1]);
	}

	@Test(expected = BadRequestException.class)
	public void testRangeError() {
		RequestSpec.parseRange("ytes=1-10");
	}

	@Test(expected = BadRequestException.class)
	public void testRangeErrorBiggerFirst() {
		RequestSpec.parseRange("bytes=10-2");
	}
	
}
