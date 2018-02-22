package tbrugz.queryon.api;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ODataFilterParser {
	
	static final Log log = LogFactory.getLog(ODataFilterParser.class);
	
	//static String[] stringFunctionsArr = { "contains", "endswith", "startswith" };

	// eq, ne, gt, ge, lt, le,
	// and
	// contains, endswith, startswith
	
	public static class Filter {
		String var;
		String operator;
		String literal;
		
		public Filter(String var, String operator, String literal) {
			this.var = var;
			this.operator = operator;
			this.literal = literal;
		}
		
		@Override
		public String toString() {
			return "["+var+" "+operator+" "+literal+"]";
		}
	}
	
	public enum State { BEGIN_EXPR, ON_EXPR, WAIT_OPER, ON_OPER, WAIT_LITERAL, ON_LITERAL, ON_STRING,
		WAIT_CONNECTOR, ON_CONNECTOR }
	
	// TODO: like, not like
	public static List<Filter> parse(String line) {
		State state = State.BEGIN_EXPR;
		int length = line.length();
		
		List<Filter> filters = new ArrayList<Filter>();
		StringBuffer b = new StringBuffer();
		String var = null;
		String oper = null;
		String literal = null;
		String connector = null;
		
		for(int i=0;i<length;i++) {
			char c = line.charAt(i);
			
			switch(state) {
			case BEGIN_EXPR:
				if(c==' ') {}
				else if(c>='0' && c<='9' || c>='A' && c<='Z' || c>='a' && c<='z') { b.append(c); state = State.ON_EXPR; }
				else throw new IllegalArgumentException("token '"+c+"' [pos "+i+"] error ["+line+"]");
				if("and".equals(connector)) {
					filters.add( new Filter(var, oper, literal) );
				}
				else if(connector!=null) { throw new IllegalArgumentException("connector '"+connector+"' not allowed"); }
				break;
			case ON_EXPR:
				if(c==' ') { var = b.toString(); b.setLength(0); state = State.WAIT_OPER; }
				else if(c>='0' && c<='9' || c>='A' && c<='Z' || c>='a' && c<='z') { b.append(c); }
				else if(c=='(') { throw new IllegalArgumentException("token '"+c+"' [pos "+i+"] error: functions not allowed yet ["+line+"]"); }
				else throw new IllegalArgumentException("token '"+c+"' [pos "+i+"] error ["+line+"] state ON_EXPR");
				break;
			case WAIT_OPER:
				if(c==' ') {}
				else if(c>='A' && c<='Z' || c>='a' && c<='z') { b.append(c); state = State.ON_OPER; }
				else throw new IllegalArgumentException("token '"+c+"' [pos "+i+"] error ["+line+"] state WAIT_OPER");
				break;
			case ON_OPER:
				if(c==' ') { oper = b.toString(); b.setLength(0); state = State.WAIT_LITERAL; }
				else if(c>='A' && c<='Z' || c>='a' && c<='z') { b.append(c); }
				else throw new IllegalArgumentException("token '"+c+"' [pos "+i+"] error ["+line+"] state ON_OPER");
				break;
			case WAIT_LITERAL:
				if(c==' ') {}
				else if(c>='1' && c<='9' || c>='A' && c<='Z' || c>='a' && c<='z') { b.append(c); state = State.ON_LITERAL; }
				else if(c=='\'') { state = State.ON_STRING; }
				else throw new IllegalArgumentException("token '"+c+"' [pos "+i+"] error ["+line+"] state WAIT_LITERAL");
				break;
			case ON_LITERAL:
				if(c==' ') { literal = b.toString(); b.setLength(0); state = State.WAIT_CONNECTOR; }
				else if(c>='0' && c<='9' || c>='A' && c<='Z' || c>='a' && c<='z') { b.append(c); }
				else throw new IllegalArgumentException("token '"+c+"' [pos "+i+"] error ["+line+"] state ON_LITERAL");
				break;
			case ON_STRING:
				if(c=='\'') { literal = b.toString(); b.setLength(0); state = State.WAIT_CONNECTOR;}
				else { b.append(c); }
				break;
			case WAIT_CONNECTOR:
				if(c==' ') {}
				else if(c>='A' && c<='Z' || c>='a' && c<='z') { b.append(c); state = State.ON_CONNECTOR; }
				else throw new IllegalArgumentException("token '"+c+"' [pos "+i+"] error ["+line+"] state WAIT_CONNECTOR");
				break;
			case ON_CONNECTOR:
				if(c==' ') { connector = b.toString(); b.setLength(0); state = State.BEGIN_EXPR; }
				else if(c>='A' && c<='Z' || c>='a' && c<='z') { b.append(c); }
				else throw new IllegalArgumentException("token '"+c+"' [pos "+i+"] error ["+line+"] state ON_CONNECTOR");
				break;
			}
		}
		if(state==State.ON_EXPR || state==State.WAIT_OPER || state==State.ON_OPER || state==State.WAIT_LITERAL || state==State.ON_STRING) {
			throw new IllegalArgumentException("cannot parse filter: '"+line+"' [state="+state+"]");
		}
		if(state==State.ON_LITERAL) {
			literal = b.toString(); b.setLength(0); state = State.WAIT_CONNECTOR;
		}
		if(state==State.WAIT_CONNECTOR) {
			filters.add( new Filter(var, oper, literal) );
		}
		
		//log.info("line["+line+"] var="+var+" oper="+oper+" literal="+literal+" connector="+connector+" / filters: "+filters);
		
		return filters;
	}
	
}
