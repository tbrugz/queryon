package tbrugz.queryon.webdav;

import javax.servlet.http.HttpServletResponse;

public class WebDavResource {
	
	static final String PADDING = "  ";
	
	final String href;
	//final String displayName;
	final Integer contentLength;
	final String contenttype;
	final String resourcetype; //collection, ...
	final Integer status;
	
	//final Date creationDate;
	//final Date lastModified;

	//String author;
	//String name;
	//String path;
	//boolean directory; //or collection

	public WebDavResource(String baseHref, String name, String contentType, Integer contentLength, boolean collection) {
		this.href = (baseHref!=null?baseHref:"") + name;
		this.contenttype = contentType;
		if(collection) {
			resourcetype = "<D:collection/>";
		}
		else {
			resourcetype = null;
		}
		this.contentLength = contentLength;
		status = HttpServletResponse.SC_OK;
	}
	
	public WebDavResource(String baseHref, String name, String contentType, boolean collection) {
		this(baseHref, name, contentType, null, collection);
	}
	
	public WebDavResource(String baseHref, String name, boolean collection) {
		this(baseHref, name, null, null, collection);
	}
	
	public WebDavResource(String name, boolean collection) {
		this(null, name, collection);
	}
	
	String serialize(String prefix) {
		return "<"+prefix+":response>\n" +
				PADDING + "<"+prefix+":href>"+encode(href)+"</"+prefix+":href>\n" +
				PADDING + "<"+prefix+":propstat>\n" +
				PADDING + PADDING + "<"+prefix+":prop>\n" +
				(resourcetype!=null ? PADDING + PADDING + PADDING + "<"+prefix+":resourcetype>"+resourcetype+"</"+prefix+":resourcetype>\n" : "") +
				(contenttype!=null ? PADDING + PADDING + PADDING + "<"+prefix+":getcontenttype>"+contenttype+"</"+prefix+":getcontenttype>\n" : "") +
				(contentLength!=null ? PADDING + PADDING + PADDING + "<"+prefix+":getcontentlength>"+contentLength+"</"+prefix+":getcontentlength>\n" : "") +
				PADDING + PADDING + "</"+prefix+":prop>\n" +
				(status!=null ? PADDING + PADDING + "<"+prefix+":status>HTTP/1.1 "+status+"</"+prefix+":status>\n" : "") +
				PADDING + "</"+prefix+":propstat>\n" +
				"</"+prefix+":response>\n"
				;
	}
	
	// XXX xmlEncode..
	String encode(String str) {
		return str;
	}
	
	@Override
	public String toString() {
		return "WebDavResource[href="+href+"]";
	}
}
