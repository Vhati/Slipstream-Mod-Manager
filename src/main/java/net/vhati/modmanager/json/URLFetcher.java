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


public class URLFetcher {

	private static final Logger log = LogManager.getLogger(URLFetcher.class);


	/**
	 * Downloads content from a url into a file, if the remote content has changed.
	 *
	 * @return true if successfully downloaded, false otherwise
	 */
	public static boolean refetchURL( String url, File localFile, File eTagFile ) {
		String localETag = null;

		log.debug( String.format( "Attempting to download the latest \"%s\".", localFile.getName() ) );
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
				log.debug( String.format( "Error reading eTag from \"%s\".", eTagFile.getName() ), e );
			}
			finally {
				try {if ( etagIn != null ) etagIn.close();}
				catch ( IOException e ) {}
			}
		}

		String remoteETag = null;
		InputStream urlIn = null;
		OutputStream localOut = null;
		try {
			URLConnection conn = new URL( url ).openConnection();

			if ( conn instanceof HttpURLConnection == false ) {
				log.error( String.format( "Non-Http(s) URL given for fetching: %s", url ) );
				return false;
			}
			HttpURLConnection httpConn = (HttpURLConnection)conn;

			httpConn.setReadTimeout( 10000 );
			if ( localETag != null )
				httpConn.setRequestProperty( "If-None-Match", localETag );
			httpConn.connect();

			int responseCode = httpConn.getResponseCode();

			if ( responseCode == HttpURLConnection.HTTP_NOT_MODIFIED ) {
				log.debug( String.format( "The server's \"%s\" has not been modified since the previous check.", httpConn.getURL().getFile() ) );

				// Update the local file's timestamp as if it had downloaded.
				localFile.setLastModified( new Date().getTime() );

				return false;
			}
			else if ( responseCode == HttpURLConnection.HTTP_OK ) {
				Map<String, List<String>> headerMap = httpConn.getHeaderFields();
				List<String> eTagValues = headerMap.get( "ETag" );
				if ( eTagValues != null && eTagValues.size() > 0 )
					remoteETag = eTagValues.get( 0 );

				urlIn = httpConn.getInputStream();
				localOut = new FileOutputStream( localFile );
				byte[] buf = new byte[4096];
				int len;
				while ( (len = urlIn.read(buf)) >= 0 ) {
					localOut.write( buf, 0, len );
				}
			}
			else {
				log.error( String.format( "Download request failed: HTTP Code %d (%s).", responseCode, httpConn.getResponseMessage() ) );
				return false;
			}
		}
		catch ( IOException e ) {
			log.error( String.format( "Error downloading the latest \"%s\".", localFile.getName() ), e );
		}
		finally {
			try {if ( urlIn != null ) urlIn.close();}
			catch ( IOException e ) {}

			try {if ( localOut != null ) localOut.close();}
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
				log.error( String.format( "Error writing eTag to \"%s\".", eTagFile.getName() ), e );
			}
			finally {
				try {if ( etagOut != null ) etagOut.close();}
				catch ( IOException e ) {}
			}
		}

		return true;
	}
}
