package net.vhati.modmanager.core;

import java.io.File;


public class ModFileInfo implements Comparable<ModFileInfo> {
	private File file;
	private String name;


	public ModFileInfo( File f ) {
		this.file = f;
		this.name = f.getName().replaceAll( "[.][^.]+$", "" );
	}

	public File getFile() { return this.file; }
	public String getName() { return this.name; }


	@Override
	public String toString() {
		return getName();
	}

	@Override
	public int compareTo( ModFileInfo other ) {
		if ( other == null ) return -1;
		if ( other == this ) return 0;

		return getName().compareTo( other.getName() );
	}

	@Override
	public boolean equals( Object o ) {
		if ( o == null ) return false;
		if ( o == this ) return true;
		if ( o instanceof ModFileInfo == false ) return false;

		ModFileInfo other = (ModFileInfo)o;
		if ( !getFile().equals( other.getFile() ) ) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = 89;
		int salt = 36;

		result = salt * result + getFile().hashCode();

		return result;
	}
}
