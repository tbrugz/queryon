package tbrugz.queryon.diff.hook;

import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import tbrugz.queryon.diff.ApplyHook.ApplyMessage;

public class ShellHookTest {

	@Test
	public void testNormalizeName() {
		String n1 = "abc def";
		Assert.assertEquals("abcdef", ShellHook.normalizeName(n1));

		n1 = "-abcdef";
		Assert.assertEquals("abcdef", ShellHook.normalizeName(n1));
	}

	@Test
	public void testNormalizeMessage() {
		String m1 = "abc \"def";
		Assert.assertEquals("abc def", ShellHook.normalizeMessage(m1));
	}

	@Test
	public void testHook() {
		Properties p = new Properties();
		String cmd = "git commit -m=\"[message]\" -u=[username] -ot=[object-type] -os=[object-schema] -on=[object-name] -mb=[model-base] -ma=[model-apply]";
		p.setProperty("queryon.diff.apply.hook.sh.cmd", cmd);
		ShellHook sh = new ShellHook();
		sh.setProperties(p);
		ApplyMessage am = new ApplyMessage("change\"s in modul-e x123", "myuser$", "-PROCEDURE", "myschema#", "procedure_x", "dev", "staging");
		String script = sh.getScriptString(am);
		System.out.println("script: "+script);
		Assert.assertEquals("git commit -m=\"changes in modul-e x123\" -u=myuser -ot=PROCEDURE -os=myschema -on=procedure_x -mb=dev -ma=staging", script);
	}
}
