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
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;


public class URLFetcher {

	private static final Logger log = LoggerFactory.getLogger( URLFetcher.class );


	/**
	 * Downloads content from a url to one file, and its ETag to another.
	 *
	 * If the ETag files exists, it will be read to inform the GET request.
	 *
	 * If the content has not changed, the destination file's modified date
	 * will be reset to the present time.
	 *
	 * If the content has changed, it will be written to the file, as will the
	 * new ETag.
	 *
	 * @return true if successfully downloaded, false otherwise
	 */
	public static boolean refetchURL( String url, File localFile, File eTagFile ) {
		String localETag = null;

		log.debug( String.format( "Attempting to download the latest \"%s\"", localFile.getName() ) );
		if ( eTagFile.exists() ) {
			// Load the old eTag.
			InputStream etagIn = null;
			BufferedReader etagReader = null;
			try {
				etagIn = new FileInputStream( eTagFile );
				etagReader = new BufferedReader( new InputStreamReader( etagIn, "UTF-8" ) );
				String line = etagReader.readLine();
				if ( line.length() > 0 ) {
					localETag = line;
				}
			}
			catch ( IOException e ) {
				// Not serious enough to be a real error.
				log.debug( String.format( "Error reading eTag from \"%s\"", eTagFile.getName() ), e );
			}
			finally {
				try {if ( etagReader != null ) etagReader.close();}
				catch ( IOException e ) {}

				try {if ( etagIn != null ) etagIn.close();}
				catch ( IOException e ) {}
			}
		}

		HttpGet request = null;
		OutputStream localOut = null;
		String remoteETag = null;

		RequestConfig requestConfig = RequestConfig.custom()
			.setConnectionRequestTimeout( 5000 )
			.setConnectTimeout( 5000 )
			.setSocketTimeout( 10000 )
			.setRedirectsEnabled( true )
			.build();

		CloseableHttpClient httpClient = HttpClientBuilder.create()
			.setDefaultRequestConfig( requestConfig )
			.disableAuthCaching()
			.disableAutomaticRetries()
			.disableConnectionState()
			.disableCookieManagement()
			//.setUserAgent( "" )
			.build();

		try {
			request = new HttpGet( url );

			HttpResponse response = httpClient.execute( request );

			int status = response.getStatusLine().getStatusCode();
			if ( status >= 200 && status < 300 ) {

				HttpEntity entity = response.getEntity();
				if ( entity != null ) {
					localOut = new FileOutputStream( localFile );
					entity.writeTo( localOut );
				}

				if ( response.containsHeader( "ETag" ) ) {
					remoteETag = response.getLastHeader( "ETag" ).getValue();
				}
			}
			else if ( status == 304 ) {  // Not modified.
				log.debug( String.format( "No need to download \"%s\", the server's copy has not been modified", localFile.getName() ) );

				// Update the local file's timestamp as if it had downloaded.
				localFile.setLastModified( new Date().getTime() );
				return false;
			}
			else {
				throw new ClientProtocolException( "Unexpected response status: "+ status );
			}
		}
		catch ( ClientProtocolException e ) {
			log.error( "GET request failed for url: "+ request.getURI().toString(), e );
			return false;
		}
		catch ( IOException e ) {
			log.error( "Download failed for url: "+ request.getURI().toString(), e );
			return false;
		}
		finally {
			try {if ( localOut != null ) localOut.close();}
			catch ( IOException e ) {}

			try {httpClient.close();}
			catch ( IOException e ) {}
		}

		if ( remoteETag != null ) {
			// Save the new eTag.
			OutputStream etagOut = null;
			BufferedWriter etagWriter = null;
			try {
				etagOut = new FileOutputStream( eTagFile );
				etagWriter = new BufferedWriter( new OutputStreamWriter( etagOut, "UTF-8" ) );
				etagWriter.append( remoteETag );
				etagWriter.flush();
			}
			catch ( IOException e ) {
				log.error( String.format( "Error writing eTag to \"%s\"", eTagFile.getName() ), e );
			}
			finally {
				try {if ( etagWriter != null ) etagWriter.close();}
				catch ( IOException e ) {}

				try {if ( etagOut != null ) etagOut.close();}
				catch ( IOException e ) {}
			}
		}

		return true;
	}
}
