package tbrugz.queryon.webdav;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

public class WebDavResource {
	
	static final String PADDING = "  ";
	
	private String baseHref;
	final String href;
	//final String displayName;
	final Integer contentLength;
	final String contenttype;
	final String resourcetype; //collection, ...
	final Integer status;
	
	//final Date creationDate;
	//final Date lastModified;

	// creator-displayname: http://www.webdav.org/specs/rfc3253.html#PROPERTY_creator-displayname (Versioning Extensions to WebDAV)
	// owner: http://www.webdav.org/specs/rfc3744.html#PROPERTY_owner (Access Control Protocol)
	//String author;
	//String name;
	//String path;
	//boolean directory; //or collection

	public WebDavResource(String baseHref, String name, String contentType, Integer contentLength, boolean collection) {
		this.baseHref = baseHref;
		//this.href = (baseHref!=null?baseHref:"") + name;
		this.href = name;
		this.contenttype = contentType;
		if(collection) {
			resourcetype = "<D:collection/>";
		}
		else {
			resourcetype = null;
		}
		this.contentLength = contentLength;
		this.status = HttpServletResponse.SC_OK;
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
	
	public void setBaseHref(String baseHref) {
		this.baseHref = baseHref
			+ (baseHref.endsWith("/") ? "" : "/");
	}
	
	String serialize(String prefix) {
		return "<"+prefix+":response>\n" +
				PADDING + "<"+prefix+":href>"+encode( (baseHref!=null?baseHref:"") + href)+"</"+prefix+":href>\n" +
				PADDING + "<"+prefix+":propstat>\n" +
				PADDING + PADDING + "<"+prefix+":prop>\n" +
				PADDING + PADDING + PADDING + (resourcetype!=null ? "<"+prefix+":resourcetype>"+resourcetype+"</"+prefix+":resourcetype>\n" : "<"+prefix+":resourcetype/>\n") +
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
