package net.vhati.modmanager.json;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.vhati.modmanager.core.ModDB;
import net.vhati.modmanager.core.ModInfo;


public class JacksonCatalogReader {

	private static final Logger log = LogManager.getLogger( JacksonCatalogReader.class );


	public static ModDB parse( File jsonFile ) {
		ModDB modDB = new ModDB();

		Exception exception = null;
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure( JsonParser.Feature.ALLOW_SINGLE_QUOTES, true );
			mapper.setVisibility( PropertyAccessor.FIELD, Visibility.ANY );

			JsonNode rootNode = mapper.readTree( jsonFile );
			JsonNode catalogsNode = rootNode.get( "catalog_versions" );
			JsonNode catalogNode = catalogsNode.get( "1" );

			for ( JsonNode infoNode : catalogNode ) {
				String threadURL = infoNode.get( "url" ).textValue();
				String threadHash = infoNode.get( "thread_hash" ).textValue();
				if ( !"???".equals( threadURL ) && !"???".equals( threadHash ) )
					modDB.putThreadHash( threadURL, threadHash );

				JsonNode versionsNode = infoNode.get( "versions" );
				for ( JsonNode versionNode : versionsNode ) {
					ModInfo modInfo = new ModInfo();
					modInfo.setTitle( infoNode.get( "title" ).textValue() );
					modInfo.setAuthor( infoNode.get( "author" ).textValue() );
					modInfo.setURL( infoNode.get( "url" ).textValue() );
					modInfo.setDescription( infoNode.get( "desc" ).textValue() );
					modInfo.setFileHash( versionNode.get( "hash" ).textValue() );
					modInfo.setVersion( versionNode.get( "version" ).textValue() );
					modDB.addMod( modInfo );
				}
			}
		}
		catch ( JsonProcessingException e ) {
			exception = e;
		}
		catch ( IOException e ) {
			exception = e;
		}
		if ( exception != null ) {
			log.error( String.format( "While processing \"%s\", json parsing failed: %s", jsonFile.getName(), exception.getMessage() ), exception );
			return null;
		}

		return modDB;
	}
}
