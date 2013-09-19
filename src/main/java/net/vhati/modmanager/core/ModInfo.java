package net.vhati.modmanager.core;


public class ModInfo {
	private String title = "???";
	private String author = "???";
	private String url = "???";
	private String description = "???";
	private String fileHash = "???";
	private String version = "???";


	public void setTitle( String s ) { this.title = s; }
	public void setAuthor( String s ) { this.author = s; }
	public void setURL( String s ) { this.url = s; }
	public void setDescription( String s ) { this.description = s; }
	public void setFileHash( String s ) { this.fileHash = s; }
	public void setVersion( String s ) { this.version = s; }

	public String getTitle() { return this.title; }
	public String getAuthor() { return this.author; }
	public String getURL() { return this.url; }
	public String getDescription() { return this.description; }
	public String getFileHash() { return this.fileHash; }
	public String getVersion() { return this.version; }


	/**
	 * Returns true if all fields, aside from fileHash, are "???".
	 */
	public boolean isBlank() {
		if ( !getTitle().equals( "???" ) ) return false;
		if ( !getAuthor().equals( "???" ) ) return false;
		if ( !getURL().equals( "???" ) ) return false;
		if ( !getDescription().equals( "???" ) ) return false;
		if ( !getVersion().equals( "???" ) ) return false;
		return true;
	}


	@Override
	public String toString() {
		return getTitle();
	}

	@Override
	public boolean equals( Object o ) {
		if ( o == null ) return false;
		if ( o == this ) return true;
		if ( o instanceof ModInfo == false ) return false;

		ModInfo other = (ModInfo)o;
		if ( !getTitle().equals( other.getTitle() ) ) return false;
		if ( !getAuthor().equals( other.getAuthor() ) ) return false;
		if ( !getURL().equals( other.getURL() ) ) return false;
		if ( !getDescription().equals( other.getDescription() ) ) return false;
		if ( !getFileHash().equals( other.getFileHash() ) ) return false;
		if ( !getVersion().equals( other.getVersion() ) ) return false;
		return true;
	}

	@Override
	public int hashCode() {
		int result = 79;
		int salt = 35;

		result = salt * result + getTitle().hashCode();
		result = salt * result + getAuthor().hashCode();
		result = salt * result + getURL().hashCode();
		result = salt * result + getDescription().hashCode();
		result = salt * result + getFileHash().hashCode();
		result = salt * result + getVersion().hashCode();
		return result;
	}
}
