package tbrugz.queryon.webdav;

public enum DavPrivilege {

	BIND, READ, WRITE_CONTENT;
	
	public String getPrivilege(String prefix) {
		switch (this) {
		case BIND:
			return "<"+prefix+":bind/>";
		case READ:
			return "<"+prefix+":read/>";
		case WRITE_CONTENT:
			return "<"+prefix+":write-content/>";
		default:
			return "<"+prefix+":unknown-privilege/>";
		}
	}
}
