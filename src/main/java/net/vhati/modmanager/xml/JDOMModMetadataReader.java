package net.vhati.modmanager.xml;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.vhati.modmanager.core.ModDB;
import net.vhati.modmanager.core.ModInfo;
import net.vhati.modmanager.core.ModUtilities;
import net.vhati.modmanager.core.ModUtilities.DecodeResult;


public class JDOMModMetadataReader {

	private static final Logger log = LogManager.getLogger( JDOMModMetadataReader.class );

	public static final String METADATA_INNERPATH = "mod-appendix/metadata.xml";


	/**
	 * Reads metadata.xml from a mod file and returns a ModInfo object.
	 *
	 * @return the read metadata, a blank ModInfo, or null if an error occurred
	 */
	public static ModInfo parseModFile( File modFile ) {
		ModInfo modInfo = null;

		InputStream fis = null;
		ZipInputStream zis = null;
		Exception exception = null;
		try {
			fis = new FileInputStream( modFile );
			zis = new ZipInputStream( new BufferedInputStream( fis ) );
			ZipEntry item;
			while ( (item = zis.getNextEntry()) != null ) {
				if ( item.isDirectory() ) {
					zis.closeEntry();
					continue;
				}

				String innerPath = item.getName();
				innerPath = innerPath.replace( '\\', '/' );  // Non-standard zips.

				if ( innerPath.equals( METADATA_INNERPATH ) ) {
					String metadataText = ModUtilities.decodeText( zis, modFile.getName()+":"+METADATA_INNERPATH ).text;
					modInfo = parse( metadataText );
					zis.closeEntry();
					break;
				}

				zis.closeEntry();
			}
		}
		catch ( JDOMException e ) {
			exception = e;
		}
		catch ( IOException e ) {
			exception = e;
		}
		finally {
			try {if ( zis != null ) zis.close();}
			catch ( IOException e ) {}

			try {if ( fis != null ) fis.close();}
			catch ( IOException e ) {}
		}
		if ( exception != null ) {
			log.error( String.format( "While processing \"%s:%s\", strict parsing failed: %s", modFile.getName(), METADATA_INNERPATH, exception.getMessage() ), exception );
			return null;
		}

		if ( modInfo == null ) modInfo = new ModInfo();
		return modInfo;
	}


	/**
	 * Reads a mod's metadata.xml and returns a ModInfo object.
	 */
	public static ModInfo parse( String metadataText ) throws IOException, JDOMException {
		ModInfo modInfo = new ModInfo();

		SAXBuilder strictParser = new SAXBuilder();
		Document doc = strictParser.build( new StringReader( metadataText ) );
		Element root = doc.getRootElement();

		String modTitle = root.getChildTextTrim( "title" );
		if ( modTitle != null && modTitle.length() > 0 )
			modInfo.setTitle( modTitle );
		else
			throw new JDOMException( "Missing title." );

		String modURL = root.getChildTextTrim( "threadUrl" );
		if ( modURL != null && modURL.length() > 0 )
			modInfo.setURL( modURL );
		else
			throw new JDOMException( "Missing threadUrl." );

		String modAuthor = root.getChildTextTrim( "author" );
		if ( modAuthor != null && modAuthor.length() > 0 )
			modInfo.setAuthor( modAuthor );
		else
			throw new JDOMException( "Missing author." );

		String modVersion = root.getChildTextTrim( "version" );
		if ( modVersion != null && modVersion.length() > 0 )
			modInfo.setVersion( modVersion );
		else
			throw new JDOMException( "Missing version." );

		String modDesc = root.getChildTextTrim( "description" );
		if ( modDesc != null && modDesc.length() > 0 )
			modInfo.setDescription( modDesc );
		else
			throw new JDOMException( "Missing description." );

		return modInfo;
	}
}
