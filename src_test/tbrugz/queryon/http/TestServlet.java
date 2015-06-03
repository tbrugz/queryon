package tbrugz.queryon.http;

import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import tbrugz.queryon.ShiroUtils;

public class TestServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		Set<String> roles = new HashSet<String>();
		roles.add("test");
		ShiroUtils.setUserRoles("anonymous", roles);
	}
}
