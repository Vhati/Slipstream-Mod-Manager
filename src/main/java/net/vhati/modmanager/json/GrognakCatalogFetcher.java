package net.vhati.modmanager.json;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class GrognakCatalogFetcher {

	private static final Logger log = LogManager.getLogger(GrognakCatalogFetcher.class);

	public static final String CATALOG_URL = "https://raw.github.com/Grognak/Grognaks-Mod-Manager/master/backup/current_catalog.json";


	/**
	 * Downloads the latest mod catalog.
	 *
	 * @return true if the catalog successfully downloaded, false otherwise
	 */
	public static boolean fetchCatalog( String catalogURL, File catalogFile, File eTagFile ) {
		String localETag = null;

		log.debug( "Attempting to download a newer catalog..." );
		if ( eTagFile.exists() ) {
			// Load the old eTag.
			InputStream etagIn = null;
			try {
				etagIn = new FileInputStream( eTagFile );
				BufferedReader br = new BufferedReader( new InputStreamReader( etagIn, "UTF-8" ) );
				String line = br.readLine();
				if ( line.length() > 0 )
					localETag = line;
			}
			catch ( IOException e ) {
				// Not serious enough to be a real error.
				log.debug( String.format( "Error reading catalog eTag from \"%s\".", eTagFile.getName() ), e );
			}
			finally {
				try {if ( etagIn != null ) etagIn.close();}
				catch ( IOException e ) {}
			}
		}

		String remoteETag = null;
		InputStream urlIn = null;
		OutputStream catalogOut = null;
		try {
			URL url = new URL( catalogURL );
			URLConnection conn = url.openConnection();

			if ( conn instanceof HttpURLConnection == false ) {
				log.error( String.format( "Non-Http(s) URL given for catalog fetching: %s", catalogURL ) );
				return false;
			}
			HttpURLConnection httpConn = (HttpURLConnection)conn;

			httpConn.setReadTimeout( 10000 );
			if ( localETag != null )
				httpConn.setRequestProperty( "If-None-Match", localETag );
			httpConn.connect();

			int responseCode = httpConn.getResponseCode();

			if ( responseCode == HttpURLConnection.HTTP_NOT_MODIFIED ) {
				log.debug( "The server's catalog has not been modified since the previous check." );

				// Update the catalog file's timestamp as if it had downloaded.
				catalogFile.setLastModified( new Date().getTime() );

				return false;
			}
			else if ( responseCode == HttpURLConnection.HTTP_OK ) {
				Map<String, List<String>> headerMap = httpConn.getHeaderFields();
				List<String> eTagValues = headerMap.get( "ETag" );
				if ( eTagValues != null && eTagValues.size() > 0 )
					remoteETag = eTagValues.get( 0 );

				urlIn = httpConn.getInputStream();
				catalogOut = new FileOutputStream( catalogFile );
				byte[] buf = new byte[4096];
				int len;
				while ( (len = urlIn.read(buf)) >= 0 ) {
					catalogOut.write( buf, 0, len );
				}
			}
			else {
				log.error( String.format( "Catalog download request failed: HTTP Code %d (%s).", responseCode, httpConn.getResponseMessage() ) );
				return false;
			}
		}
		catch ( MalformedURLException e ) {
			log.error( "Error fetching latest catalog.", e );
		}
		catch ( IOException e ) {
			log.error( "Error fetching latest catalog.", e );
		}
		finally {
			try {if ( urlIn != null ) urlIn.close();}
			catch ( IOException e ) {}

			try {if ( catalogOut != null ) catalogOut.close();}
			catch ( IOException e ) {}
		}

		if ( remoteETag != null ) {
			// Save the new eTag.
			OutputStream etagOut = null;
			try {
				etagOut = new FileOutputStream( eTagFile );
				BufferedWriter bw = new BufferedWriter( new OutputStreamWriter( etagOut, "UTF-8" ) );
				bw.append( remoteETag );
				bw.flush();
			}
			catch ( IOException e ) {
				log.error( String.format( "Error writing catalog eTag to \"%s\".", eTagFile.getName() ), e );
			}
			finally {
				try {if ( etagOut != null ) etagOut.close();}
				catch ( IOException e ) {}
			}
		}

		return true;
	}
}
