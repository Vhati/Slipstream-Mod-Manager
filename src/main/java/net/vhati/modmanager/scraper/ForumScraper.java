/*
 * Ignore this package.
 * It's for Slipstream/GMM catalog maintenance.
 */

package net.vhati.modmanager.scraper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.ftldat.PackUtilities;
import net.vhati.modmanager.core.ModDB;
import net.vhati.modmanager.core.ModInfo;
import net.vhati.modmanager.core.ModsInfo;
import net.vhati.modmanager.json.JacksonCatalogReader;
import net.vhati.modmanager.json.JacksonCatalogWriter;


public class ForumScraper {

	private static final Logger log = LoggerFactory.getLogger( ForumScraper.class );

	private static final String MASTER_LIST_URL = "https://subsetgames.com/forum/viewtopic.php?f=11&t=2645";
	private static final String FORUM_URL_FRAGMENT = "https://subsetgames.com/forum/viewtopic.php";


	public static void main( String[] args ) {

		List<String> ignoredURLs = new ArrayList<String>();
		ignoredURLs.add( "https://subsetgames.com/forum/viewtopic.php?f=11&t=11561" );
		ignoredURLs.add( "https://subsetgames.com/forum/viewtopic.php?f=12&t=11083" );
		ignoredURLs.add( "https://subsetgames.com/forum/viewtopic.php?f=4&t=2938" );
		ignoredURLs.add( "http://www.moddb.com/mods/better-planets-and-backgrounds/downloads/better-asteroids" );
		ignoredURLs.add( "https://subsetgames.com/forum/viewtopic.php?f=4&t=2947" );
		ignoredURLs.add( "https://subsetgames.com/forum/viewtopic.php?f=12&t=11604" );
		// Hissatsu's post on "Advanced Battle Systems".
		ignoredURLs.add( "https://subsetgames.com/forum/viewtopic.php?f=11&t=11469&start=60#p55171" );
		// SpaceDock is an app.
		ignoredURLs.add( "https://subsetgames.com/forum/viewtopic.php?f=11&t=16842" );
		// Beginning Scrap Advantage is bundled in GMM.
		ignoredURLs.add( "https://subsetgames.com/forum/viewtopic.php?f=4&t=2464" );
		// Engi Scrap Advantage is bundled in SMM.
		ignoredURLs.add( "https://subsetgames.com/forum/viewtopic.php?f=12&t=17102" );


		BasicParser parser = new BasicParser();

		Options options = new Options();
		options.addOption( OptionBuilder.withLongOpt( "load-json" )
		                                .withDescription( "load moddb from a json catalog" )
		                                .hasArg()
		                                .withArgName("FILE")
		                                .create() );
		options.addOption( OptionBuilder.withLongOpt( "load-xml" )
		                                .withDescription( "load moddb from an xml file" )
		                                .hasArg()
		                                .withArgName("FILE")
		                                .create() );
		options.addOption( OptionBuilder.withLongOpt( "scrape" )
		                                .withDescription( "write changed forum posts to an xml file" )
		                                .hasArg()
		                                .withArgName("FILE")
		                                .create() );
		options.addOption( OptionBuilder.withLongOpt( "dump-json" )
		                                .withDescription( "write the moddb to a json file" )
		                                .hasArg()
		                                .withArgName("FILE")
		                                .create() );
		options.addOption( OptionBuilder.withLongOpt( "dump-xml" )
		                                .withDescription( "write the moddb to an xml file" )
		                                .hasArg()
		                                .withArgName("FILE")
		                                .create() );
		options.addOption( OptionBuilder.withLongOpt( "hash-thread" )
		                                .withDescription( "print the hash of a specific thread" )
		                                .hasArg()
		                                .withArgName("URL")
		                                .create() );
		options.addOption( OptionBuilder.withLongOpt( "first-post" )
		                                .withDescription( "print the first post of a thread (debugging)" )
		                                .hasArg()
		                                .withArgName("URL")
		                                .create() );
		options.addOption( "h", "help", false, "display this help and exit" );

		CommandLine cmdline = null;
		try {
			cmdline = parser.parse( options, args, true );
		}
		catch( ParseException e ) {
			System.err.println( "Error parsing commandline: "+ e.getMessage() );
			System.exit( 1 );
		}

		if ( cmdline.hasOption( "h" ) ) {
			HelpFormatter formatter = new HelpFormatter();

			String syntax = ForumScraper.class.getCanonicalName() +" [OPTIONS]";

			String helpHeader = "Load an existing catalog as the moddb, and scrape."+ formatter.getNewLine();
			helpHeader += "Edit the catalog by copy/pasting scrape snippets."+ formatter.getNewLine();
			helpHeader += "Load the edited catalog and dump json."+ formatter.getNewLine();

			PrintWriter pw = new PrintWriter( System.out );
			formatter.printUsage( pw, formatter.getWidth(), syntax );
			pw.write( helpHeader );
			pw.write( formatter.getNewLine() );
			formatter.printOptions( pw, formatter.getWidth(), options, formatter.getLeftPadding(), formatter.getDescPadding() );
			pw.flush();

			System.exit( 0 );
		}

		ModDB modDB = new ModDB();

		try {
			if ( cmdline.hasOption( "load-json" ) ) {
				log.info( "Loading json catalog..." );

				File srcFile = new File( cmdline.getOptionValue( "load-json" ) );
				ModDB newDB = JacksonCatalogReader.parse( srcFile );
				if ( newDB != null ) modDB = newDB;
			}

			if ( cmdline.hasOption( "load-xml" ) ) {
				log.info( "Loading xml catalog..." );

				File srcFile = new File( cmdline.getOptionValue( "load-xml" ) );
				ModDB newDB = parseCatalogXML( srcFile );
				if ( newDB != null ) modDB = newDB;
			}

			if ( cmdline.hasOption( "scrape" ) ) {
				log.info( "Scraping..." );

				File dstFile = new File( cmdline.getOptionValue( "scrape" ) );
				List<ModsInfo> data = scrape( modDB, MASTER_LIST_URL, ignoredURLs );
				if ( data.size() > 0 ) writeXML( data, dstFile );
			}

			if ( cmdline.hasOption( "dump-json" ) ) {
				log.info( "Dumping json..." );

				File dstFile = new File( cmdline.getOptionValue( "dump-json" ) );
				List<ModsInfo> data = modDB.getCollatedModInfo();
				if ( data.size() > 0 ) JacksonCatalogWriter.write( data, dstFile );
			}

			if ( cmdline.hasOption( "dump-xml" ) ) {
				log.info( "Dumping xml..." );

				File dstFile = new File( cmdline.getOptionValue( "dump-xml" ) );
				List<ModsInfo> data = modDB.getCollatedModInfo();
				if ( data.size() > 0 ) writeXML( data, dstFile );
			}

			if ( cmdline.hasOption( "hash-thread" ) ) {
				log.info( "Hashing thread..." );

				String threadURL = cmdline.getOptionValue( "hash-thread" );
				System.out.println( hashThread( threadURL ) );
			}

			if ( cmdline.hasOption( "first-post" ) ) {
				log.info( "Getting thread's first post..." );

				String threadURL = cmdline.getOptionValue( "first-post" );
				System.out.println( getFirstPost( threadURL ) );
			}
		}
		catch ( Exception e ) {
			log.error( "An error occurred.", e );
		}
	}


	/**
	 * Scrapes the forum for changed posts and returns info from updated mods.
	 */
	private static List<ModsInfo> scrape( ModDB knownDB, String masterListURL, List<String> ignoredURLs ) throws IOException, NoSuchAlgorithmException {
		List<ModsInfo> results = new ArrayList<ModsInfo>();

		List<ScrapeResult> scrapeList = scrapeMasterList( knownDB, masterListURL, ignoredURLs );

		for ( ScrapeResult scrapedInfo : scrapeList ) {
			ModsInfo modsInfo = new ModsInfo();
			modsInfo.setTitle( scrapedInfo.title );
			modsInfo.setAuthor( scrapedInfo.author );
			modsInfo.setThreadURL( scrapedInfo.threadURL );
			modsInfo.setThreadHash( scrapedInfo.threadHash );
			modsInfo.setDescription( scrapedInfo.rawDesc );
			modsInfo.putVersion( "???", "???"+ (scrapedInfo.wip ? " WIP" : "") );
			results.add( modsInfo );
		}

		return results;
	}


	/**
	 * Scrape the Master Mod List on the FTL forum.
	 *
	 * If an existing ModDB is provided, its thread urls will be checked too.
	 *
	 * @param knownDB a ModDB with mods to ignore if threadHash is unchanged
	 * @param ignoredUrls a list of uninteresting threadURLs to ignore
	 */
	private static List<ScrapeResult> scrapeMasterList( ModDB knownDB, String masterListURL, List<String> ignoredURLs ) throws IOException, NoSuchAlgorithmException {
		if ( ignoredURLs == null ) ignoredURLs = new ArrayList<String>();

		Pattern modsHeaderPtn = Pattern.compile( Pattern.quote("<span style=\"font-weight: bold\"><span style=\"text-decoration: underline\"><span style=\"font-size: 150%; line-height: 116%;\">Mods</span></span></span>") );
		Pattern modPtn = Pattern.compile( "^(?:\\[[A-Za-z0-9 ]+ *\\])?<a href=\"([^\"]+)\"[^>]*>([^>]+)</a> *((?:\\[[A-Za-z0-9 ]+\\])?)(?: (?:.*?))? - Author: <a href=\"[^\"]+\"[^>]*>([^<]+?)</a>" );

		HashSet<String> boringHashes = new HashSet<String>();
		if ( knownDB != null ) {
			for ( ModInfo modInfo : knownDB.getCatalog() ) {
				String threadHash = knownDB.getThreadHash( modInfo.getURL() );
				if ( threadHash == null ) {
					log.debug( "No thread hash for modInfo: "+ modInfo.getTitle() );
				}
				if ( threadHash != null && !threadHash.equals("???") )
					boringHashes.add( threadHash );
			}
		}

		String postContent = getFirstPost( masterListURL );
		postContent = postContent.replaceAll( "<br */>", "\n" );

		String[] lines = postContent.split("\n");
		List<ScrapeResult> results = new ArrayList<ScrapeResult>();
		List<String> pendingURLs = new ArrayList<String>();
		boolean inMods = false;
		Matcher m = null;

		for ( String line : lines ) {
			if ( modsHeaderPtn.matcher(line).find() ) {
				inMods = true;
				continue;
			}
			if ( !inMods ) continue;

			m = modPtn.matcher(line);
			if ( m.find() ) {
				ScrapeResult result = new ScrapeResult();
				result.threadURL = m.group(1);
				result.title = m.group(2);
				result.author = m.group(4);
				result.wip = m.group(3).equals("[WIP]");
				result.rawDesc = "";
				result.threadHash = "???";

				result.title = result.title.replaceAll( "&amp;", "&" );
				result.threadURL = result.threadURL.replaceAll( "&amp;", "&" );
				results.add( result );
			}
		}
		if ( knownDB != null ) {
			for ( ScrapeResult result : results ) {
				pendingURLs.add( result.threadURL );
			}
			for ( ModInfo modInfo : knownDB.getCatalog() ) {
				if ( !modInfo.getURL().equals("???") && !pendingURLs.contains(modInfo.getURL()) ) {
					pendingURLs.add( modInfo.getURL() );
					ScrapeResult result = new ScrapeResult();
					result.threadURL = modInfo.getURL();
					result.title = modInfo.getTitle();
					result.author = modInfo.getAuthor();
					result.wip = false;  // *shrug*
					result.rawDesc = modInfo.getDescription();
					result.threadHash = knownDB.getThreadHash( modInfo.getURL() );
					results.add( result );
				}
			}
		}

		// Prune results with boring urls.
		for ( Iterator<ScrapeResult> it=results.iterator(); it.hasNext(); ) {
			ScrapeResult result = it.next();
			if ( ignoredURLs.contains( result.threadURL ) )
				it.remove();
		}

		// Fetch and hash each thread url.
		for ( int i=0; i < results.size(); i++ ) {
			ScrapeResult result = results.get(i);
			if ( result.threadURL.startsWith( FORUM_URL_FRAGMENT ) == false )
				continue;  // Don't bother scraping and hashing non-forum urls.

			try {Thread.sleep( 2000 );}
			catch ( InterruptedException e ) {log.info( "Inter-fetch sleep interrupted." );}

			log.info( "" );
			log.info( String.format( "Scraping mod %03d/%03d (%s)...", (i+1), results.size(), result.title ) );
			while( true ) {
				try {
					result.rawDesc = getFirstPost( result.threadURL );
					result.threadHash = PackUtilities.calcStreamMD5( new ByteArrayInputStream( result.rawDesc.getBytes( Charset.forName("UTF-8") ) ) );
					break;
				}
				catch ( IOException e ) {
					log.error( "Request failed: "+ e.getMessage() );
				}
				try {Thread.sleep( 5000 );}
				catch ( InterruptedException e ) {log.error( "Re-fetch sleep interrupted.", e );}
			}
		}

		// Ignore threads whose hashes haven't changed.
		for ( Iterator<ScrapeResult> it=results.iterator(); it.hasNext(); ) {
			ScrapeResult result = it.next();
			if ( boringHashes.contains( result.threadHash ) )
				it.remove();
		}

		// Scrub html out of descriptions and scrape download links.
		for ( ScrapeResult result : results ) {
			postContent = result.rawDesc;
			postContent = postContent.replaceAll( "<br */>", "\n" );
			postContent = postContent.replaceAll( "<img [^>]*/>", "" );
			postContent = postContent.replaceAll( "<span [^>]*>", "" );
			postContent = postContent.replaceAll( "</span>", "" );
			postContent = postContent.replaceAll( "&quot;", "\"" );
			postContent = postContent.replaceAll( "\u2018|\u2019", "'" );
			postContent = postContent.replaceAll( "\u2022", "-" );
			postContent = postContent.replaceAll( "\u2013", "-" );
			postContent = postContent.replaceAll( "\u00a9", "()" );
			postContent = postContent.replaceAll( "&amp;", "&" );
			postContent = postContent.replaceAll( "<a (?:[^>]+ )?href=\"([^\"]+)\"[^>]*>", "<a href=\"$1\">" );
			postContent = postContent.replaceAll( "<a href=\"[^\"]+/forum/memberlist.php[^\"]+\"[^>]*>([^<]+)</a>", "$1" );
			postContent = postContent.replaceAll( "<a href=\"http://(?:i.imgur.com/|[^\"]*photobucket.com/|[^\"]*deviantart.com/|www.mediafire.com/view/[?])[^\"]+\"[^>]*>([^<]+)</a>", "$1" );
			postContent = postContent.replaceAll( "<a href=\"([^\"]+)\"[^>]*>(?:\\1|[^<]+ [.][.][.] [^<]+)</a>", "<a href=\"$1\">Link</a>" );
			postContent = postContent.replaceAll( "<a href=\"[^\"]+[.](?:jpg|png)(?:[.]html)?\"[^>]*>([^<]*)</a>", "$1" );
			postContent = postContent.replaceAll( "</li><li>", "</li>\n<li>" );
			postContent = postContent.replaceAll( "<li>(.*?)</li>", " - $1" );
			postContent = postContent.replaceAll( "</li>", "" );
			postContent = postContent.replaceAll( "</?ul>", "" );
			postContent = postContent.replaceAll( "(?s)<blockquote [^>]+><div>(.*?)</div></blockquote>", "<blockquote>$1</blockquote>" );
			postContent = postContent.replaceAll( "<!-- [^>]+ -->", "" );

			// Link to GMM Thread.
			postContent = postContent.replaceAll( "<a href=\"[^\"]+/forum/viewtopic.php?(?:[^&]+&)*t=2464\"[^>]*>([^<]+)</a>", "$1" );
			// Link to Superluminal Thread.
			postContent = postContent.replaceAll( "<a href=\"[^\"]+/forum/viewtopic.php?(?:[^&]+&)*t=11251\"[^>]*>([^<]+)</a>", "$1" );
			// Link to FTLEdit Thread.
			postContent = postContent.replaceAll( "<a href=\"[^\"]+/forum/viewtopic.php?(?:[^&]+&)*t=2959\"[^>]*>([^<]+)</a>", "$1" );

			postContent = postContent.replaceAll( "\\A\\s+", "" );
			postContent = postContent.replaceAll( "\\s+\\Z", "" );
			result.rawDesc = postContent +"\n";  // Raw quoting looks better with a newline.
		}

		return results;
	}


	/**
	 * Extracts the html content of the first post in a forum thread.
	 */
	private static String getFirstPost( String url ) throws IOException {
		String htmlSrc = fetchWebPage( url );

		Pattern firstPostPtn = Pattern.compile( "(?s)<div class=\"postbody\"[^>]*>.*?<div class=\"content\"[^>]*>(.*?)</div>\\s*<dl class=\"postprofile\"[^>]*>" );
		Matcher m = null;

		String postContent = "";
		m = firstPostPtn.matcher( htmlSrc );
		if ( m.find() ) {
			postContent = m.group( 1 );
			postContent = postContent.replaceAll( "\r?\n", "" );

			// Within content, but it counts clicks/views, which throws off hashing.
			postContent = postContent.replaceAll( "(?s)<div class=\"inline-attachment\">.*?</div>", "" );

			// Footer junk.
			//postContent = postContent.replaceAll( "(?s)<dl class=\"attachbox\">.*?<dl class=\"file\">.*?</dl>.*?</dl>", "" );
			postContent = postContent.replaceAll( "(?s)<dl class=\"file\">.*?</dl>", "" );
			postContent = postContent.replaceAll( "(?s)<dd>\\s*?</dd>", "" );
			postContent = postContent.replaceAll( "(?s)<dl class=\"attachbox\">.*?</dl>", "" );
			postContent = postContent.replaceAll( "(?s)<div (?:[^>]+ )?class=\"notice\">.*?</div>", "" );
			postContent = postContent.replaceAll( "(?s)<div (?:[^>]+ )?class=\"signature\">.*?</div>", "" );
			postContent = postContent.replaceAll( "</div>\\s*\\Z", "" );
			postContent = postContent.replaceAll( "\\A\\s+", "" );
			postContent = postContent.replaceAll( "\\s+\\Z", "" );
		}

		return postContent;
	}


	/**
	 * Calculates an MD5 hash of the first post in a thread.
	 */
	private static String hashThread( String url ) throws IOException, NoSuchAlgorithmException {
		String rawDesc = getFirstPost( url );
		return PackUtilities.calcStreamMD5( new ByteArrayInputStream( rawDesc.getBytes( Charset.forName("UTF-8") ) ) );
	}


	/**
	 * Downloads a URL and returns the string content, decoded as UTF-8.
	 */
	private static String fetchWebPage( String url ) throws IOException {
		String result = null;
		InputStream urlIn = null;
		ByteArrayOutputStream bytesOut = null;

		try {
			URLConnection conn = new URL( url ).openConnection();

			if ( conn instanceof HttpURLConnection == false ) {
				throw new MalformedURLException( String.format( "Non-Http(s) URL given to fetch: %s", url ) );
			}
			HttpURLConnection httpConn = (HttpURLConnection)conn;

			httpConn.setReadTimeout( 10000 );
			httpConn.connect();

			int responseCode = httpConn.getResponseCode();

			if ( responseCode == HttpURLConnection.HTTP_OK ) {
				int contentLength = conn.getContentLength();
				urlIn = httpConn.getInputStream();
				bytesOut = new ByteArrayOutputStream( contentLength>0 ? contentLength : 4096 );

				byte[] buf = new byte[4096];
				int len;
				while ( (len = urlIn.read(buf)) >= 0 ) {
					bytesOut.write( buf, 0, len );
				}

				byte[] allBytes = bytesOut.toByteArray();
				CharsetDecoder decoder = Charset.forName( "UTF-8" ).newDecoder();
				ByteBuffer byteBuffer = ByteBuffer.wrap( allBytes, 0, allBytes.length );
				result = decoder.decode( byteBuffer ).toString();
			}
		}
		finally {
			try {if ( urlIn != null ) urlIn.close();}
			catch ( IOException e ) {}

			// No need to close an array stream.
		}

		return result;
	}


	/**
	 * Writes collated catalog entries to a file, as human-editable xml.
	 */
	private static void writeXML( List<ModsInfo> data, File dstFile ) throws IOException, NoSuchAlgorithmException {
		OutputStream os = null;
		try {
			os = new FileOutputStream( dstFile );
			OutputStreamWriter writer = new OutputStreamWriter( os, Charset.forName("US-ASCII") );
			writeXML( data, writer );
			writer.flush();
		}
		finally {
			try {if ( os != null ) os.close();}
			catch ( IOException e ) {}
		}
	}

	private static void writeXML( List<ModsInfo> data, OutputStreamWriter dst ) throws IOException {
		boolean first = true;
		dst.append( "<?xml version=\"1.0\" encoding=\""+ dst.getEncoding() +"\"?>\n" );
		dst.append( "<modsinfoList>\n" );
		for ( ModsInfo modsInfo : data ) {
			if ( !first ) dst.append( "\n" );

			writeXML( modsInfo, dst, "  ", 1 );
			first = false;
		}
		dst.append( "</modsinfoList>" );
	}

	private static void writeXML( ModsInfo modsInfo, OutputStreamWriter dst, String indent, int depth ) throws IOException {
		Format xmlFormat = Format.getPrettyFormat();
		xmlFormat.setEncoding( dst.getEncoding() );
		XMLOutputter xmlOut = new XMLOutputter( xmlFormat );

		writeIndent( dst, indent, depth++ ).append( "<modsinfo>\n" );
		writeIndent( dst, indent, depth ); dst.append("<title>").append( xmlOut.escapeElementEntities( modsInfo.getTitle() ) ).append( "</title>\n" );
		writeIndent( dst, indent, depth ); dst.append("<author>").append( xmlOut.escapeElementEntities( modsInfo.getAuthor() ) ).append( "</author>\n" );
		writeIndent( dst, indent, depth ); dst.append("<threadUrl><![CDATA[ ").append( modsInfo.getThreadURL() ).append( " ]]></threadUrl>\n" );

		writeIndent( dst, indent, depth++ ).append( "<versions>\n" );
		for ( Map.Entry<String,String> entry : modsInfo.getVersionsMap().entrySet() ) {
			String versionFileHash = entry.getKey();
			String versionString = entry.getValue();

			writeIndent( dst, indent, depth );
			dst.append( "<version hash=\"" ).append( xmlOut.escapeAttributeEntities( versionFileHash ) ).append( "\">" );
			dst.append( xmlOut.escapeElementEntities( versionString ) );
			dst.append( "</version>" ).append( "\n" );
		}
		writeIndent( dst, indent, --depth ).append( "</versions>\n" );
		writeIndent( dst, indent, depth ); dst.append("<threadHash>").append( modsInfo.getThreadHash() ).append( "</threadHash>\n" );
		dst.append( "\n" );

		writeIndent( dst, indent, depth ); dst.append( "<description>" ).append( "<![CDATA[" );
		dst.append( modsInfo.getDescription() );
		dst.append( "]]>\n" );
		writeIndent( dst, indent, depth ); dst.append( "</description>\n" );

		writeIndent( dst, indent, --depth ).append( "</modsinfo>\n" );
	}

	/**
	 * Adds indentation to a given depth.
	 */
	private static Appendable writeIndent( Appendable dst, String indent, int depth ) throws IOException {
		for ( int i=0; i < depth; i++ ) dst.append( indent );
		return dst;
	}


	/**
	 * Parses dumped xml and returns a new catalog.
	 */
	private static ModDB parseCatalogXML( File srcFile ) throws IOException, JDOMException {
		ModDB modDB = new ModDB();
		SAXBuilder builder = new SAXBuilder();
		InputStream is = null;

		try {
			is = new FileInputStream( srcFile );
			Document doc = builder.build( is );
			Element rootNode = doc.getRootElement();


			for ( Element infoNode : rootNode.getChildren( "modsinfo" ) ) {
				String threadURL = infoNode.getChildTextTrim( "threadUrl" );
				String threadHash = infoNode.getChildTextTrim( "threadHash" );

				if ( !threadURL.equals( "???" ) && !threadHash.equals( "???" ) ) {
					String oldHash = modDB.getThreadHash( threadURL );
					if ( oldHash != null && !oldHash.equals( threadHash ) ) {
						log.warn( "Multiple thread hashes for url: "+ threadURL );
					}
					modDB.putThreadHash( threadURL, threadHash );
				}

				for ( Element versionNode : infoNode.getChild( "versions" ).getChildren( "version" ) ) {
					ModInfo modInfo = new ModInfo();
					modInfo.setTitle( infoNode.getChildTextTrim( "title" ) );
					modInfo.setAuthor( infoNode.getChildTextTrim( "author" ) );
					modInfo.setURL( threadURL );
					modInfo.setDescription( infoNode.getChildTextTrim( "description" ) );
					modInfo.setFileHash( versionNode.getAttributeValue( "hash" ) );
					modInfo.setVersion( versionNode.getTextTrim() );
					modDB.addMod( modInfo );
				}
			}
		}
		finally {
			try {if ( is != null ) is.close();}
			catch ( IOException e ) {}
		}

		return modDB;
	}


	/** Information gleaned from scraping the forum. */
	private static class ScrapeResult {
		public String threadURL = null;
		public String title = null;
		public String author = null;
		public boolean wip = false;
		public String rawDesc = null;
		public String threadHash = null;
	}
}
