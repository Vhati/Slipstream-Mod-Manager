package net.vhati.modmanager.core;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import ar.com.hjg.pngj.PngReader;

import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.JDOMParseException;
import org.jdom2.input.SAXBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.modmanager.core.EmptyAwareSAXHandlerFactory;
import net.vhati.modmanager.core.EOLWriter;
import net.vhati.modmanager.core.Report;
import net.vhati.modmanager.core.Report.ReportMessage;
import net.vhati.modmanager.core.SloppyXMLParser;


public class ModUtilities {

	private static final Logger log = LoggerFactory.getLogger( ModUtilities.class );

	private static Pattern junkFilePtn = Pattern.compile( "[.]DS_Store$|(?:^|/)thumbs[.]db$|(?:^|/)[.]dropbox$|(?:^|/)~|~$|(?:^|/)#.+#$" );


	/**
	 * Encodes a string (throwing an exception on bad chars) to bytes in a stream.
	 * Line endings will not be normalized.
	 *
	 * @param text a String to encode
	 * @param encoding the name of a Charset
	 * @param description how error messages should refer to the string, or null
	 */
	public static InputStream encodeText( String text, String encoding, String description ) throws IOException {
		CharsetEncoder encoder = Charset.forName( encoding ).newEncoder();

		ByteArrayOutputStream tmpData = new ByteArrayOutputStream();
		Writer writer = new OutputStreamWriter( tmpData, encoder );
		writer.write( text );
		writer.flush();

		InputStream result = new ByteArrayInputStream( tmpData.toByteArray() );
		return result;
	}

	/**
	 * Determines text encoding for an InputStream and decodes its bytes as a string.
	 *
	 * CR and CR-LF line endings will be normalized to LF.
	 *
	 * @param is a stream to read
	 * @param description how error messages should refer to the stream, or null
	 */
	public static DecodeResult decodeText( InputStream is, String description ) throws IOException {
		String result = null;

		byte[] buf = new byte[4096];
		int len;
		ByteArrayOutputStream tmpData = new ByteArrayOutputStream();
		while ( (len = is.read( buf )) >= 0 ) {
			tmpData.write( buf, 0, len );
		}
		byte[] allBytes = tmpData.toByteArray();
		tmpData.reset();

		Map<byte[],String> boms = new LinkedHashMap<byte[],String>();
		boms.put( new byte[] {(byte)0xEF,(byte)0xBB,(byte)0xBF}, "UTF-8" );
		boms.put( new byte[] {(byte)0xFF,(byte)0xFE}, "UTF-16LE" );
		boms.put( new byte[] {(byte)0xFE,(byte)0xFF}, "UTF-16BE" );

		String encoding = null;
		byte[] bom = null;

		for ( Map.Entry<byte[],String> entry : boms.entrySet() ) {
			byte[] tmpBom = entry.getKey();
			byte[] firstBytes = Arrays.copyOfRange( allBytes, 0, tmpBom.length );
			if ( Arrays.equals( tmpBom, firstBytes ) ) {
				encoding = entry.getValue();
				bom = tmpBom;
				break;
			}
		}

		if ( encoding != null ) {
			// This may throw CharacterCodingException.
			CharsetDecoder decoder = Charset.forName( encoding ).newDecoder();
			ByteBuffer byteBuffer = ByteBuffer.wrap( allBytes, bom.length, allBytes.length-bom.length );
			result = decoder.decode( byteBuffer ).toString();
			allBytes = null;    // GC hint.
		}
		else {
			ByteBuffer byteBuffer = ByteBuffer.wrap( allBytes );

			Map<String,Exception> errorMap = new LinkedHashMap<String,Exception>();
			for ( String guess : new String[] {"UTF-8", "windows-1252"} ) {
				try {
					byteBuffer.rewind();
					byteBuffer.limit( allBytes.length );
					CharsetDecoder decoder = Charset.forName( guess ).newDecoder();
					result = decoder.decode( byteBuffer ).toString();
					encoding = guess;
					break;
				}
				catch ( CharacterCodingException e ) {
					errorMap.put( guess, e );
				}
			}
			if ( encoding == null ) {
				// All guesses failed!?
				String msg = String.format( "Could not guess encoding for %s.", (description!=null ? "\""+description+"\"" : "a file") );
				for ( Map.Entry<String,Exception> entry : errorMap.entrySet() ) {
					msg += String.format( "\nFailed to decode as %s: %s", entry.getKey(), entry.getValue() );
				}
				throw new IOException( msg );
			}
			allBytes = null;    // GC hint.
		}

		// Determine the original line endings.
		int eol = DecodeResult.EOL_NONE;
		Matcher m = Pattern.compile( "(\r(?!\n))|((?<!\r)\n)|(\r\n)" ).matcher( result );
		if ( m.find() ) {
			if ( m.group(3) != null ) eol = DecodeResult.EOL_CRLF;
			else if ( m.group(2) != null ) eol = DecodeResult.EOL_LF;
			else if ( m.group(1) != null ) eol = DecodeResult.EOL_CR;
		}

		result = result.replaceAll( "\r(?!\n)|\r\n", "\n" );
		return new DecodeResult( result, encoding, eol, bom );
	}


	/**
	 * Semi-intelligently appends mainStream, with content from appendStream.
	 * Note: This is similar to how patching used to work prior to SMM 1.2.
	 *
	 * XML parsing/tidying does NOT take place. The new content is basically
	 * tacked on as-is. Any xml declaration tags will be scrubbed from both
	 * streams, and a new one will be prepended.
	 *
	 * If the mainStream had &lt;FTL&gt; tags (introduced in FTL 1.6.1), they
	 * will be scrubbed, and new ones will be added after appending. If
	 * appendStream has those tags, they will be scrubbed.
	 *
	 * The two InputStreams are read, and the combined result is returned as a
	 * new third InputStream.
	 *
	 * The returned stream is a ByteArrayInputStream which doesn't need
	 * closing.
	 *
	 * The result will have CR-LF line endings and the desired encoding.
	 *
	 * The description arguments identify the streams for log messages.
	 *
	 * FTL 1.01-1.5.13 assumes all XML is in windows-1252 encoding, even on
	 * Linux.
	 *
	 * FTL 1.6.1 assumes all XML is in UTF-8 encoding.
	 *
	 * Note: SMM 1.5 changed the order of arguments (previous releases took
	 * the source of new content to append as the first argument).
	 */
	public static InputStream appendXMLFile( InputStream mainStream, InputStream appendStream, String encoding, String mainDescription, String appendDescription ) throws IOException {
		// XML declaration, or root FTL tags.
		Pattern comboPtn = Pattern.compile( "(<[?]xml [^>]*?[?]>\n*)|(</?FTL>)" );
		Matcher m = null;
		boolean mainHadRootTags = false;

		String mainText = decodeText( mainStream, mainDescription ).text;
		StringBuffer mainBuf = new StringBuffer( mainText.length() );
		m = comboPtn.matcher( mainText );
		while ( m.find() ) {
			if ( m.group( 2 ) != null ) mainHadRootTags = true;
			m.appendReplacement( mainBuf, "" );
		}
		m.appendTail( mainBuf );

		String appendText = decodeText( appendStream, appendDescription ).text;
		StringBuffer appendBuf = new StringBuffer( appendText.length() );
		m = comboPtn.matcher( appendText );
		while ( m.find() ) {
			m.appendReplacement( appendBuf, "" );
		}
		m.appendTail( appendBuf );

		// Concatenate, filtering the stream to standardize newlines and encode.
		//
		CharsetEncoder encoder = Charset.forName( encoding ).newEncoder();
		ByteArrayOutputStream tmpData = new ByteArrayOutputStream();
		Writer writer = new EOLWriter( new OutputStreamWriter( tmpData, encoder ), "\r\n" );

		writer.append( "<?xml version=\"1.0\" encoding=\""+ encoding +"\"?>\n" );
		if ( mainHadRootTags ) writer.append( "<FTL>\n" );
		writer.append( mainBuf );
		writer.append( "\n\n<!-- Appended by Slipstream -->\n\n" );
		writer.append( appendBuf );
		writer.append( "\n" );
		if ( mainHadRootTags ) writer.append( "</FTL>\n" );
		writer.flush();
		InputStream result = new ByteArrayInputStream( tmpData.toByteArray() );

		return result;
	}


	/**
	 * Appends and modifies mainStream, using content from appendStream.
	 *
	 * If the mainStream had &lt;FTL&gt; tags (introduced in FTL 1.6.1), they
	 * will be scrubbed, and new ones will be added after appending. If
	 * appendStream has those tags, they will be scrubbed.
	 *
	 * The two InputStreams are read, and the combined result
	 * is returned as a new third InputStream.
	 *
	 * The returned stream is a ByteArrayInputStream
	 * which doesn't need closing.
	 *
	 * The result will have CR-LF line endings and the desired encoding.
	 *
	 * FTL 1.01-1.5.13 assumes all XML is in windows-1252 encoding, even on
	 * Linux.
	 *
	 * FTL 1.6.1 assumes all XML is in UTF-8 encoding.
	 *
	 * The description arguments identify the streams for log messages.
	 *
	 * @see net.vhati.modmanager.core.XMLPatcher
	 * @see net.vhati.modmanager.core.SloppyXMLOutputProcessor
	 */
	public static InputStream patchXMLFile( InputStream mainStream, InputStream appendStream, String encoding, boolean globalPanic, String mainDescription, String appendDescription ) throws IOException, JDOMException {
		// XML declaration, or root FTL tags.
		Pattern comboPtn = Pattern.compile( "(<[?]xml [^>]*?[?]>\n*)|(</?FTL>)" );
		Matcher m = null;
		boolean mainHadRootTags = false;
		String wrapperOpenTag = "<wrapper xmlns:mod='mod' xmlns:mod-append='mod-append' xmlns:mod-overwrite='mod-overwrite'>";
		String wrapperCloseTag = "</wrapper>";
		StringBuffer buf = null;

		String mainText = decodeText( mainStream, mainDescription ).text;
		buf = new StringBuffer( wrapperOpenTag.length() + mainText.length() + wrapperCloseTag.length() );
		buf.append( wrapperOpenTag );
		m = comboPtn.matcher( mainText );
		while ( m.find() ) {
			if ( m.group( 2 ) != null ) mainHadRootTags = true;
			m.appendReplacement( buf, "" );
		}
		m.appendTail( buf );
		buf.append( wrapperCloseTag );
		mainText = null;
		Document mainDoc = parseStrictOrSloppyXML( buf, mainDescription+" (wrapped)" );
		buf.setLength( 0 );

		String appendText = decodeText( appendStream, appendDescription ).text;
		buf.ensureCapacity( wrapperOpenTag.length() + appendText.length() + wrapperCloseTag.length() );
		buf.append( wrapperOpenTag );
		m = comboPtn.matcher( appendText );
		while ( m.find() ) {
			m.appendReplacement( buf, "" );
		}
		m.appendTail( buf );
		buf.append( wrapperCloseTag );
		appendText = null;
		Document appendDoc = parseStrictOrSloppyXML( buf, appendDescription+" (wrapped)" );
		buf.setLength( 0 );

		buf.trimToSize();  // Free the buffer.
		buf = null;

		XMLPatcher patcher = new XMLPatcher();
		patcher.setGlobalPanic( globalPanic );
		Document mergedDoc = patcher.patch( mainDoc, appendDoc );
		mainDoc = null;
		appendDoc = null;

		// Add FTL tags and move all content inside them.
		// Collect live getContent() results in an Arraylist to avoid
		// ConcurrentModificationException when detaching in the loop.
		if ( mainHadRootTags ) {
			Element mergedRoot = mergedDoc.getRootElement();
			Element ftlNode = new Element( "FTL" );
			List<Content> mergedContentList = new ArrayList<Content>( mergedRoot.getContent() );
			for ( Content c : mergedContentList ) {  // 
				c.detach();
			}
			ftlNode.addContent( mergedContentList );
			mergedRoot.addContent( ftlNode );
		}

		// Bake XML into text, filtering the stream to standardize newlines and encode.

		CharsetEncoder encoder = Charset.forName( encoding ).newEncoder();
		ByteArrayOutputStream tmpData = new ByteArrayOutputStream();
		Writer writer = new EOLWriter( new OutputStreamWriter( tmpData, encoder ), "\r\n" );

		SloppyXMLOutputProcessor.sloppyPrint( mergedDoc, writer, encoding, false );
		writer.flush();
		InputStream result = new ByteArrayInputStream( tmpData.toByteArray() );

		return result;
	}

	/**
	 * Decodes, parses, sloppy prints, and reencodes an XML stream.
	 * This effectively repairs any XML problems that the sloppy parser
	 * can tolerate.
	 *
	 * The returned stream is a ByteArrayInputStream
	 * which doesn't need closing.
	 *
	 * The result will have CR-LF line endings and the desired encoding.
	 *
	 * The description argument identifies the stream for log messages.
	 *
	 * FTL 1.01-1.5.13 assumes all XML is in windows-1252 encoding, even on
	 * Linux.
	 *
	 * FTL 1.6.1 assumes all XML is in UTF-8 encoding.
	 *
	 * @see net.vhati.modmanager.core.XMLPatcher
	 * @see net.vhati.modmanager.core.SloppyXMLOutputProcessor
	 */
	public static InputStream rebuildXMLFile( InputStream srcStream, String encoding, String srcDescription ) throws IOException, JDOMException {
		Pattern xmlDeclPtn = Pattern.compile( "<[?]xml [^>]*?[?]>\n*" );

		String srcText = decodeText( srcStream, srcDescription ).text;
		srcText = xmlDeclPtn.matcher( srcText ).replaceFirst( "" );
		srcText = "<wrapper xmlns:mod='mod' xmlns:mod-append='mod-append' xmlns:mod-overwrite='mod-overwrite'>"+ srcText +"</wrapper>";
		Document doc = parseStrictOrSloppyXML( srcText, srcDescription+" (wrapped)" );
		srcText = null;

		// Bake XML into text, filtering the stream to standardize newlines and encode.

		CharsetEncoder encoder = Charset.forName( encoding ).newEncoder();
		ByteArrayOutputStream tmpData = new ByteArrayOutputStream();
		Writer writer = new EOLWriter( new OutputStreamWriter( tmpData, encoder ), "\r\n" );

		SloppyXMLOutputProcessor.sloppyPrint( doc, writer, encoding, false );
		writer.flush();
		InputStream result = new ByteArrayInputStream( tmpData.toByteArray() );

		return result;
	}


	/**
	 * Returns an XML Document, parsed strictly if possible, or sloppily.
	 * Exceptions during strict parsing will be ignored.
	 *
	 * This method does NOT strip the XML declaration and add a wrapper
	 * tag with namespaces. That must be done beforehand.
	 *
	 * @see net.vhati.modmanager.core.EmptyAwareSAXHandlerFactory
	 * @see net.vhati.modmanager.core.SloppyXMLParser
	 */
	public static Document parseStrictOrSloppyXML( CharSequence srcSeq, String srcDescription ) throws IOException, JDOMException {
		Document doc = null;

		try {
			SAXBuilder strictParser = new SAXBuilder();
			strictParser.setSAXHandlerFactory( new EmptyAwareSAXHandlerFactory() );
			doc = strictParser.build( new StringReader( srcSeq.toString() ) );
		}
		catch ( JDOMParseException e ) {
			// Ignore the error, and do a sloppy parse instead.

			try {
				SloppyXMLParser sloppyParser = new SloppyXMLParser();
				doc = sloppyParser.build( srcSeq );
			}
			catch ( JDOMParseException f ) {
				throw new JDOMException( String.format( "While processing \"%s\", strict parsing failed, then sloppy parsing failed: %s", srcDescription, f.getMessage() ), f );
			}
		}

		return doc;
	}


	/**
	 * Returns true if a path matches known junk files, false otherwise.
	 *
	 * This includes:
	 *   *.DS_Store
	 *   thumbs.db
	 *   .dropbox
	 *   #*#
	 *   ~*
	 *   *~
	 *
	 * @param innerPath a path with forward slashes
	 */
	public static boolean isJunkFile( String innerPath ) {
		return junkFilePtn.matcher( innerPath ).find();
	}


	/**
	 * Checks a mod file for common problems.
	 *
	 * @param modFile an *.ftl file to check
	 */
	public static Report validateModFile( File modFile ) {

		List<ReportMessage> messages = new ArrayList<ReportMessage>();
		List<ReportMessage> pendingMsgs = new ArrayList<ReportMessage>();
		boolean modValid = true;
		boolean seenAppend = false;

		Pattern validRootDirPtn = Pattern.compile( "^(?:audio|data|fonts|img|mod-appendix)/" );
		List<String> seenJunkDirs = new ArrayList<String>();

		CharsetEncoder asciiEncoder = Charset.forName( "US-ASCII" ).newEncoder();
		CharsetEncoder win1252Encoder = Charset.forName( "windows-1252" ).newEncoder();

		ZipInputStream zis = null;
		try {
			zis = new ZipInputStream( new FileInputStream( modFile ) );
			ZipEntry item;
			while ( (item = zis.getNextEntry()) != null ) {
				String innerPath = item.getName();
				pendingMsgs.clear();

				if ( innerPath.indexOf( "\\" ) != -1 ) {
					pendingMsgs.add( new ReportMessage(
						ReportMessage.ERROR,
						String.format( "Backslashes in path. (Non-standard zip archive)" )
					) );
					modValid = false;
					innerPath = innerPath.replace( '\\', '/' );
				}

				if ( !asciiEncoder.reset().canEncode( innerPath ) ) {
					pendingMsgs.add( new ReportMessage(
						ReportMessage.ERROR,
						String.format( "Non-ASCII characters in path." )
					) );
					modValid = false;
				}

				if ( innerPath.indexOf( "/" ) == -1 ) {
					pendingMsgs.add( new ReportMessage(
						ReportMessage.WARNING,
						String.format( "Extraneous top-level file." )
					) );
					modValid = false;
				}
				else if ( !validRootDirPtn.matcher( innerPath ).find() ) {
					String junkDir = innerPath.replaceFirst( "/.*", "/" );
					if ( !seenJunkDirs.contains( junkDir ) ) {
						seenJunkDirs.add( junkDir );
						pendingMsgs.add( new ReportMessage(
							ReportMessage.ERROR,
							String.format( "Unsupported top-level folder: %s", junkDir )
						) );
					}
					modValid = false;
				}
				else if ( item.isDirectory() ) {
				}
				else if ( isJunkFile( innerPath ) ) {
					pendingMsgs.add( new ReportMessage(
						ReportMessage.ERROR,
						String.format( "Junk file" )
					) );
					modValid = false;
				}
				else if ( innerPath.matches( "^.*[.]font$" ) ) {
					pendingMsgs.add( new ReportMessage(
						ReportMessage.WARNING,
						String.format( "The *.font files introduced with FTL 1.6.1 won't work in FTL 1.01-1.5.13, which used TTF fonts." )
					) );
				}
				else if ( innerPath.matches( "^.*[.]mp3$" ) ) {
					pendingMsgs.add( new ReportMessage(
						ReportMessage.WARNING,
						String.format( "As of FTL 1.6.1, MP3 audio is not supported (Use OGG or WAV)" )
					) );
				}
				else if ( innerPath.matches( "^.*[.]png$" ) ) {
					try {
						PngReader pngr = new PngReader( zis );

						if ( pngr.interlaced ) {
							pendingMsgs.add( new ReportMessage(
								ReportMessage.WARNING,
								String.format( "As of FTL 1.6.1, interlaced PNG images are not supported (Re-save it as non-interlaced)" )
							) );
						}

						// Check for Truecolor+Alpha (32bit RGBA).
						if ( pngr.imgInfo.channels != 4 || pngr.imgInfo.bitDepth != 8 ) {

							String colorTypeString = "???";
							if ( pngr.imgInfo.channels == 4 )
								colorTypeString = String.format( "RGB+Alpha w/%dbit channels", pngr.imgInfo.bitDepth );
							else if ( pngr.imgInfo.channels == 3 )
								colorTypeString = String.format( "RGB w/%dbit channels", pngr.imgInfo.bitDepth );
							else if ( pngr.imgInfo.channels == 2 )
								colorTypeString = "Gray+Alpha";
							else if ( pngr.imgInfo.channels == 1 && !pngr.imgInfo.greyscale )
								colorTypeString = "Indexed Color";
							else if ( pngr.imgInfo.channels == 1 && pngr.imgInfo.greyscale )
								colorTypeString = "Gray";

							pendingMsgs.add( new ReportMessage(
								ReportMessage.WARNING,
								String.format( "Type: %s (Usually RGB+Alpha w/8bit channels)", colorTypeString )
							) );
						}
					}
					catch ( Exception e ) {
						log.error( String.format( "Error while validating \"%s:%s\"", modFile.getName(), innerPath ), e );
						pendingMsgs.add( new ReportMessage(
							ReportMessage.ERROR,
							"An error occurred. See log for details."
						) );
						modValid = false;
					}
				}
				else if ( innerPath.matches( "^.*[.]ttf$" ) ) {
					pendingMsgs.add( new ReportMessage(
						ReportMessage.WARNING,
						String.format( "FTL 1.01-1.5.13 had TTF fonts, but FTL 1.6.1 switched to *.font (A special bitmap format from the SIL library)" )
					) );
				}
				else if ( innerPath.matches( "^.*(?:[.]xml[.]append|[.]append[.]xml)$" ) ||
				          innerPath.matches( "^.*(?:[.]xml[.]rawappend|[.]rawappend[.]xml)$" ) ||
				          innerPath.matches( "^.*(?:[.]xml[.]rawclobber|[.]rawclobber[.]xml)$" ) ||
				          innerPath.matches( "^.*(?:[.]xml|[.]txt)$" ) ) {

					boolean isTxt = innerPath.matches( "^.*(?:[.]txt)$" );
					boolean isXML = innerPath.matches( "^.*(?:[.]xml[.]append|[.]append[.]xml|[.]xml)$" );

					if ( innerPath.matches( "^.*(?:[.]xml[.]append|[.]append[.]xml)$" ) ) {
						seenAppend = true;
					}
					else if ( innerPath.matches( "^.*(?:[.]xml[.]rawappend|[.]rawappend[.]xml)$" ) ) {
						isXML = false;  // Raw XML is exempt from normal processing.
						seenAppend = true;
					}
					else if ( innerPath.matches( "^.*(?:[.]xml[.]rawclobber|[.]rawclobber[.]xml)$" ) ) {
						isXML = false;
					}

					DecodeResult decodeResult = ModUtilities.decodeText( zis, modFile.getName()+":"+innerPath );

					if ( decodeResult.bom != null ) {
						pendingMsgs.add( new ReportMessage(
							ReportMessage.WARNING,
							String.format( "%s BOM detected. (Some tools may not handle BOMs)", decodeResult.encoding )
						) );
						modValid = false;
					}

					if ( decodeResult.eol != DecodeResult.EOL_CRLF &&
					     decodeResult.eol != DecodeResult.EOL_NONE ) {
						if ( isXML ) {
							pendingMsgs.add( new ReportMessage(
								ReportMessage.WARNING,
								String.format( "%s line endings (CR-LF is safest)", decodeResult.getEOLName() )
							) );
						}
						else if ( isTxt ) {
							pendingMsgs.add( new ReportMessage(
								ReportMessage.ERROR,
								String.format( "%s line endings (Non-CR-LF txt crashes FTL unless tools fix it)", decodeResult.getEOLName() )
							) );
						}
						modValid = false;
					}

					if ( decodeResult.encoding.equalsIgnoreCase( "windows-1252" ) ) {
						// Found non-ASCII chars unique to windows-1252.

						Set<CharSequence> uniqueGraphemes = new TreeSet<CharSequence>();
						getUniqueGraphemes( decodeResult.text, uniqueGraphemes, Locale.getDefault() );

						StringBuilder charBuf = new StringBuilder();
						for ( CharSequence grapheme : uniqueGraphemes ) {
							if ( !asciiEncoder.reset().canEncode( grapheme ) ) {
								if ( charBuf.length() > 0 ) charBuf.append( "," );

								charBuf.append( grapheme );
							}
						}

						pendingMsgs.add( new ReportMessage(
							ReportMessage.WARNING,
							String.format( "Windows-1252 encoding with fancy non-ASCII chars (UTF-8 is recommended for clarity): %s", charBuf.toString() )
						) );
					}
					else {
						// Not windows-1252.
						// Nag if there are chars that can't be converted to
						// windows-1252 (for FTL 1.01-1.5.13).

						Set<CharSequence> uniqueGraphemes = new TreeSet<CharSequence>();
						getUniqueGraphemes( decodeResult.text, uniqueGraphemes, Locale.getDefault() );

						StringBuilder charBuf = new StringBuilder();
						for ( CharSequence grapheme : uniqueGraphemes ) {
							if ( !win1252Encoder.reset().canEncode( grapheme ) ) {
								if ( charBuf.length() > 0 ) charBuf.append( "," );

								charBuf.append( grapheme ).append( " (" );
								appendGraphemeHex( grapheme, charBuf );
								charBuf.append( ")" );
							}
						}
						if ( charBuf.length() > 0 ) {
							pendingMsgs.add( new ReportMessage(
								ReportMessage.WARNING,
								String.format( "Characters that can't be re-encoded as windows-1252 will not work in FTL 1.5.13 and earlier: %s", charBuf.toString() )
							) );
						}
					}

					// Suggest replacements for odd characters.
					List<Pattern> oddCharPtns = new ArrayList<Pattern>();
					Map<Pattern,String> oddCharSuggestions = new HashMap<Pattern,String>();
					Map<Pattern,List<Character>> oddCharLists = new HashMap<Pattern,List<Character>>();

					oddCharPtns.add( Pattern.compile( "\\u0060|\\u201A|\\u2018|\\u2019" ) );
					oddCharSuggestions.put( oddCharPtns.get( oddCharPtns.size()-1 ), "'" );

					oddCharPtns.add( Pattern.compile( "\\u201E|\\u201C|\\u201D" ) );
					oddCharSuggestions.put( oddCharPtns.get( oddCharPtns.size()-1 ), "\"" );

					oddCharPtns.add( Pattern.compile( "\\u2013|\\u2014" ) );
					oddCharSuggestions.put( oddCharPtns.get( oddCharPtns.size()-1 ), "-" );

					oddCharPtns.add( Pattern.compile( "\\u2026" ) );
					oddCharSuggestions.put( oddCharPtns.get( oddCharPtns.size()-1 ), "..." );

					for ( Pattern ptn : oddCharPtns ) {
						Matcher m = ptn.matcher( decodeResult.text );
						List<Character> chars = null;
						while ( m.find() ) {
							if ( chars == null )
								chars = new ArrayList<Character>();

							Character cObj = new Character( m.group( 0 ).charAt( 0 ) );
							if ( !chars.contains( cObj ) )
								chars.add( cObj );
						}
						if ( chars != null )
							oddCharLists.put( ptn, chars );
					}
					for ( Pattern ptn : oddCharPtns ) {
						List<Character> chars = oddCharLists.get( ptn );
						if ( chars != null ) {
							String suggestion = oddCharSuggestions.get( ptn );
							StringBuilder charBuf = new StringBuilder( chars.size() );
							for ( Character cObj : chars )
								charBuf.append( cObj.charValue() );

							pendingMsgs.add( new ReportMessage(
								ReportMessage.WARNING,
								String.format( "Odd characters resembling %s : %s", suggestion, charBuf.toString() )
							) );
						}
					}

					if ( isXML ) {
						Report xmlReport = validateModXML( decodeResult.text );

						if ( xmlReport.messages.size() > 0 ) {
							List<ReportMessage> condensedList = new ArrayList<ReportMessage>();
							ReportMessage prevMessage = null;
							for ( ReportMessage message : xmlReport.messages ) {
								if ( !message.equals( prevMessage ) ) {
									condensedList.add( message );
									prevMessage = message;
								}
							}

							pendingMsgs.add( new ReportMessage(
								ReportMessage.WARNING_SUBSECTION,
								"Strict XML Parser Issues:",
								condensedList
							) );
						}
						if ( xmlReport.outcome == false )
							modValid = false;

						Report sloppyReport = validateSloppyModXML( decodeResult.text );

						if ( sloppyReport.messages.size() > 0 ) {
							pendingMsgs.add( new ReportMessage(
								ReportMessage.ERROR_SUBSECTION,
								"Sloppy XML Parser Issues:",
								sloppyReport.messages
							) );
						}
						if ( sloppyReport.outcome == false )
							modValid = false;
					}
				}

				if ( !pendingMsgs.isEmpty() ) {
					// Prepend the original path.
					messages.add( new ReportMessage(
						ReportMessage.SUBSECTION,
						item.getName()
					) );
					messages.addAll( pendingMsgs );
				}

				zis.closeEntry();
			}

			if ( !seenAppend ) {
				messages.add( new ReportMessage(
					ReportMessage.WARNING_SUBSECTION,
					"This mod doesn't append. It clobbers."
				) );
				modValid = false;
			}
		}
		catch ( Exception e ) {
			log.error( String.format( "Error while validating mod: %s", modFile.getName() ), e );
			messages.add( new ReportMessage(
				ReportMessage.ERROR,
				"An error occurred. See log for details."
			) );
			modValid = false;
		}
		finally {
			try {if ( zis != null ) zis.close();}
			catch ( IOException e ) {}
		}

		if ( modValid ) {
			//messages.clear();  // Nothing bad enough to mention.

			messages.add( new ReportMessage(
				ReportMessage.INFO,
				String.format( "No Problems", modFile.getName() )
			) );
		}

		// Insert the mod's filename at the top.
		messages.add( 0, new ReportMessage(
			ReportMessage.SECTION,
			String.format( "%s:", modFile.getName() )
		) );

		return new Report( messages, modValid );
	}

	/**
	 * Populates an existing Set with unique graphemes from a string.
	 *
	 * What humans think of as a character, is a grapheme. Unicode allows
	 * multiple code points (Java's char values) to cluster into a grapheme
	 * (like a letter, plus an accent).
	 *
	 * This method may be called repeatedly to accumulate graphemes from
	 * multiple strings.
	 *
	 * @src a String to scan
	 * @dstSet a Set to add results into (such as a TreeSet)
	 * @locale a locale for creating a BreakIterator
	 * @see java.text.BreakIterator
	 */
	public static void getUniqueGraphemes( String src, Set<CharSequence> dstSet, Locale locale ) {
		BreakIterator graphemeIt = BreakIterator.getCharacterInstance( locale );  // No arg means default Locale.
		graphemeIt.setText( src );

		int start = graphemeIt.first();
		int end = graphemeIt.next();

		while ( end != BreakIterator.DONE ) {
			CharSequence grapheme = src.subSequence( start, end );
			dstSet.add( grapheme );

			start = end;
			end = graphemeIt.next();
		}
	}

	/**
	 * Appends a grapheme's code points ("U+XXXX") to a buffer.
	 *
	 * If a grapheme involves multiple code points, they will be
	 * space-delimited.
	 */
	public static void appendGraphemeHex( CharSequence src, StringBuilder dstBuf ) {
		for ( int i=0; i < src.length(); i++ ) {
			if ( i > 0 ) dstBuf.append( " " );
			dstBuf.append( String.format( "U+%04X", (int)src.charAt( i ) ) );
		}
	}


	/**
	 * Checks a mod's xml for problems.
	 *
	 * It first tries to preemptively fix and report
	 * common typos all at once.
	 *
	 * Then a real XML parser runs, which stops at the
	 * first typo it sees. :/
	 *
	 * @param text unparsed xml
	 */
	public static Report validateModXML( String text ) {

		List<ReportMessage> messages = new ArrayList<ReportMessage>();
		boolean xmlValid = true;

		StringBuffer srcBuf = new StringBuffer( text );
		StringBuffer dstBuf = new StringBuffer( text.length() );
		StringBuffer tmpBuf;  // For swapping;
		String ptn;
		Matcher m;

		// Wrap everything in a root tag, while mindful of the xml declaration.
		String wrapperSTag = "<wrapper xmlns:mod='mod' xmlns:mod-append='mod-append' xmlns:mod-overwrite='mod-overwrite'>";

		Pattern xmlDeclPtn = Pattern.compile( "<[?]xml [^>]*?[?]>\n*" );
		m = xmlDeclPtn.matcher( srcBuf );
		boolean foundTopDecl = false;
		while ( m.find() ) {
			if ( m.start() == 0 ) {
				foundTopDecl = true;
				m.appendReplacement( dstBuf, "$0\n"+ Matcher.quoteReplacement( wrapperSTag ) );
			}
			else {
				messages.add( new ReportMessage(
					ReportMessage.ERROR,
					"<?xml... ?> should only occur on the first line."
				) );
				m.appendReplacement( dstBuf, "" );
			}
		}
		m.appendTail( dstBuf );
		dstBuf.append( "\n</wrapper>" );

		if ( !foundTopDecl )
			dstBuf.insert( 0, Matcher.quoteReplacement( wrapperSTag ) );

		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength( 0 );

		// Comments with long tails or double-dashes.
		m = Pattern.compile( "(?s)<!--(-*)(.*?)(-*)-->" ).matcher( srcBuf );
		while ( m.find() ) {
			if ( m.group( 1 ).length() > 0 || m.group( 3 ).length() > 0 || m.group( 2 ).indexOf( "--" ) != -1 ) {
				messages.add( new ReportMessage(
					ReportMessage.ERROR,
					"<!-- No other dashes should touch. -->"
				) );
			}
			m.appendReplacement( dstBuf, m.quoteReplacement( m.group( 2 ).replaceAll( "[^\n]", "" ) ) );  // Strip comments, but preserve line count.
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength( 0 );

		// Mismatched single-line tags.
		// Example: blueprints.xml: <title>...</type>
		m = Pattern.compile( "<([^/!][^> ]+?)((?: [^>]+?)?)(?<!/)>([^<]+?)</([^>]+?)>" ).matcher( srcBuf );
		while ( m.find() ) {
			if ( m.group( 1 ).equals( m.group( 4 ) ) == false ) {
				messages.add( new ReportMessage(
					ReportMessage.ERROR,
					"<"+ m.group( 1 ) +"...>...</"+ m.group( 4 ) +">"
				) );
				m.appendReplacement( dstBuf, m.quoteReplacement( "<"+ m.group( 1 ) + m.group( 2 ) +">"+ m.group( 3 ) +"</"+ m.group( 1 ) +">" ) );
			}
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength( 0 );

		// <pilot power="1"max="3" room="0"/>
		// Groan, \t separates attribs sometimes.
		m = Pattern.compile( "<([^> ]+?)( [^>]+?\")([^\"= \t>]+?=\"[^\"]+?\")((?:[^>]+?)?)>" ).matcher( srcBuf );
		while ( m.find() ) {
			messages.add( new ReportMessage(
				ReportMessage.ERROR,
				"<"+ m.group( 1 ) +"...\""+ m.group( 3 ) +"...>"
			) );
			m.appendReplacement( dstBuf, m.quoteReplacement( "<"+ m.group( 1 ) + m.group( 2 ) +" "+ m.group( 3 ) + m.group( 4 ) +">" ) );
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength( 0 );

		// sector_data.xml closing tag.
		m = Pattern.compile( "((?s)<sectorDescription[^>]*>.*?)</sectorDescrption>" ).matcher( srcBuf );
		while ( m.find() ) {
			messages.add( new ReportMessage(
				ReportMessage.ERROR,
				"<sectorDescription>...</sectorDescrption>"
			) );
			m.appendReplacement( dstBuf, m.quoteReplacement( m.group( 1 ) +"</sectorDescription>" ) );
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength( 0 );

		// {anyship}.xml: <gib1>...</gib2>
		m = Pattern.compile( "(?s)<(gib[0-9]+)>(.*?)</(gib[0-9]+)>" ).matcher( srcBuf );
		while ( m.find() ) {
			if ( m.group( 1 ).equals( m.group( 3 ) ) == false ) {
				messages.add( new ReportMessage(
					ReportMessage.ERROR,
					"<"+ m.group( 1 ) +">...</"+ m.group( 3 ) +">"
				) );
				m.appendReplacement( dstBuf, m.quoteReplacement( "<"+ m.group( 1 ) +">"+ m.group( 2 ) +"</"+ m.group( 1 ) +">" ) );
			}
			else {
				m.appendReplacement( dstBuf, m.quoteReplacement( m.group( 0 ) ) );
			}
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength( 0 );

		// event*.xml: <choice... hidden="true" hidden="true">
		m = Pattern.compile( "<([a-zA-Z0-9_-]+?)((?: [^>]+?)?) ([^>]+?)(=\"[^\">]+?\") \\3(?:=\"[^\">]+?\")([^>]*)>" ).matcher( srcBuf );
		while ( m.find() ) {
			messages.add( new ReportMessage(
				ReportMessage.ERROR,
				"<"+ m.group( 1 ) +"... "+ m.group( 3 ) +"=... "+ m.group( 3 ) +"=...>"
			) );
			m.appendReplacement( dstBuf, m.quoteReplacement( "<"+ m.group( 1 ) + m.group( 2 ) +" "+ m.group( 3 ) + m.group( 4 ) +" "+ m.group( 5 ) +">" ) );
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength( 0 );

		// <shields>...</slot>
		ptn = "";
		ptn += "(<shields *(?: [^>]*)?>\\s*";
		ptn +=   "<slot *(?: [^>]*)?>\\s*";
		ptn +=     "(?:<direction>[^<]*</direction>\\s*)?";
		ptn +=     "(?:<number>[^<]*</number>\\s*)?";
		ptn +=   "</slot>\\s*)";
		ptn += "</slot>";  // Wrong closing tag.
		m = Pattern.compile( ptn ).matcher( srcBuf );
		while ( m.find() ) {
			messages.add( new ReportMessage(
				ReportMessage.ERROR,
				"<shields>...</slot>"
			) );
			m.appendReplacement( dstBuf, m.quoteReplacement( m.group( 1 ) +"</shields>" ) );
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength( 0 );

		// <shipBlueprint>...</ship>
		ptn = "";
		ptn += "(<shipBlueprint *(?: [^>]*)?>\\s*";
		ptn +=   "<class>[^<]*</class>\\s*";
		ptn +=   "<systemList *(?: [^>]*)?>\\s*";
		ptn +=      "(?:<[a-zA-Z]+ *(?: [^>]*)?/>\\s*)*";
		ptn +=   "</systemList>\\s*";
		ptn +=   "(?:<droneList *(?: [^>]*)?>\\s*";
		ptn +=      "(?:<[a-zA-Z]+ *(?: [^>]*)?/>\\s*)*";
		ptn +=   "</droneList>\\s*)?";
		ptn +=   "(?:<weaponList *(?: [^>]*)?>\\s*";
		ptn +=      "(?:<[a-zA-Z]+ *(?: [^>]*)?/>\\s*)*";
		ptn +=   "</weaponList>\\s*)?";
		ptn +=   "(?:<[a-zA-Z]+ *(?: [^>]*)?/>\\s*)*)";
		ptn += "</ship>";  // Wrong closing tag.
		m = Pattern.compile( ptn ).matcher( srcBuf );
		while ( m.find() ) {
			messages.add( new ReportMessage(
				ReportMessage.ERROR,
				"<shipBlueprint>...</ship>"
			) );
			m.appendReplacement( dstBuf, m.quoteReplacement( m.group( 1 ) +"</shipBlueprint>" ) );
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength( 0 );

		// <textList>...</text>
		ptn = "";
		ptn += "(<textList *(?: [^>]*)?>\\s*";
		ptn += "(?:<text *(?: [^>]*)?>[^<]*</text>\\s*)*)";
		ptn += "</text>";  // Wrong closing tag.
		m = Pattern.compile( ptn ).matcher( srcBuf );
		while ( m.find() ) {
			messages.add( new ReportMessage(
				ReportMessage.ERROR,
				"<textList>...</text>"
			) );
			m.appendReplacement( dstBuf, m.quoteReplacement( m.group( 1 ) +"</textList>" ) );
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength( 0 );

		Document doc = null;
		try {
			SAXBuilder saxBuilder = new SAXBuilder();
			doc = saxBuilder.build( new StringReader( srcBuf.toString() ) );

			xmlValid = true;
			for ( ReportMessage message : messages ) {
				if ( message.type == ReportMessage.ERROR ) {
					xmlValid = false;
					break;
				}
			}
		}
		catch ( JDOMParseException e ) {
			int lineNum = e.getLineNumber();
			if ( lineNum != -1 ) {
				int badStart = -1;
				int badEnd = -1;
				String badLine = "???";
				m = Pattern.compile( "\n|\\z" ).matcher( srcBuf );
				for ( int i=1; i <= lineNum && m.find(); i++ ) {
					if ( i == lineNum-1 ) {
						badStart = m.end();
					} else if ( i == lineNum ) {
						badEnd = m.start();
						badLine = srcBuf.substring( badStart, badEnd );
					}
				}
				String msg = String.format( "Fix this and try again:\n%s", e.toString() );
				msg += "\n";
				msg += "~  ~  ~  ~  ~\n";
				msg += badLine +"\n";
				msg += "~  ~  ~  ~  ~";
				messages.add( new ReportMessage(
					ReportMessage.EXCEPTION,
					msg
				) );
			}
			else {
				messages.add( new ReportMessage(
					ReportMessage.EXCEPTION,
					"An error occurred. See log for details."
				) );
			}
			xmlValid = false;
		}
		catch ( Exception e ) {
			log.error( "Error while validating mod xml with the strict parser.", e );
			messages.add( new ReportMessage(
				ReportMessage.EXCEPTION,
				"An error occurred. See log for details."
			) );
			xmlValid = false;
		}

		return new Report( messages, xmlValid );
	}


	/**
	 * Checks if a mod's xml can be parsed sloppily.
	 *
	 * @param text unparsed xml
	 */
	public static Report validateSloppyModXML( String text ) {

		List<ReportMessage> messages = new ArrayList<ReportMessage>();
		boolean xmlValid = true;

		// Meh, the parser's gonna make its own wrapper with declarations anyway.
		//text = "<wrapper xmlns:mod='mod' xmlns:mod-append='mod-append' xmlns:mod-overwrite='mod-overwrite'>"+ text +"</wrapper>";

		try {
			SloppyXMLParser parser = new SloppyXMLParser();
			parser.build( text );
		}
		catch ( JDOMParseException e ) {
			int lineNum = e.getLineNumber();
			if ( lineNum != -1 ) {
				int badStart = -1;
				int badEnd = -1;
				String badLine = "???";
				Matcher m = Pattern.compile( "\n|\\z" ).matcher( text );
				for ( int i=1; i <= lineNum && m.find(); i++ ) {
					if ( i == lineNum-1 ) {
						badStart = m.end();
					}
					else if ( i == lineNum ) {
						badEnd = m.start();
						badLine = text.substring( badStart, badEnd );
					}
				}
				String msg = String.format( "Fix this and try again:\n%s", e.toString() );
				msg += "\n";
				msg += "~  ~  ~  ~  ~\n";
				msg += badLine +"\n";
				msg += "~  ~  ~  ~  ~";
				messages.add( new ReportMessage(
					ReportMessage.EXCEPTION,
					msg
				) );
			}
			else {
				log.error( "Error while validating mod xml with the sloppy parser.", e );
				messages.add( new ReportMessage(
					ReportMessage.EXCEPTION,
					"An error occurred. See log for details."
				) );
			}
			xmlValid = false;
		}
		catch ( Exception e ) {
			log.error( "Error while validating mod xml with the sloppy parser.", e );
			messages.add( new ReportMessage(
				ReportMessage.EXCEPTION,
				"An error occurred. See log for details."
			) );
		}

		return new Report( messages, xmlValid );
	}


	/**
	 * Returns the latest modification time among files within a mod.
	 *
	 * If no files have timestamps, -1 is returned.
	 *
	 * Presumably, this time is measured in milliseconds since the
	 * epoch (00:00:00 GMT, January 1, 1970).
	 *
	 * @see java.util.zip.ZipEntry#getTime()
	 */
	public static long getModFileTime( File modFile ) throws IOException {
		long result = -1;

		ZipInputStream zis = null;
		try {
			zis = new ZipInputStream( new FileInputStream( modFile ) );
			ZipEntry item;
			while ( (item = zis.getNextEntry()) != null ) {
				long n = item.getTime();
				if ( n > result ) result = n;
				zis.closeEntry();
			}
		}
		finally {
			try {if ( zis != null ) zis.close();}
			catch ( IOException e ) {}
		}

		return result;
	}



	/**
	 * A holder for results from decodeText().
	 *
	 * text     - The decoded string.
	 * encoding - The encoding used.
	 * eol      - A constant describing the original line endings.
	 * bom      - The BOM bytes found, or null.
	 */
	public static class DecodeResult {
		public static final int EOL_NONE = 0;
		public static final int EOL_CRLF = 1;
		public static final int EOL_LF = 2;
		public static final int EOL_CR = 3;

		public final String text;
		public final String encoding;
		public final int eol;
		public final byte[] bom;

		public DecodeResult( String text, String encoding, int eol, byte[] bom ) {
			this.text = text;
			this.encoding = encoding;
			this.eol = eol;
			this.bom = bom;
		}

		public String getEOLName() {
			if ( eol == EOL_CRLF ) return "CR-LF";
			if ( eol == EOL_LF ) return "LF";
			if ( eol == EOL_CR ) return "CR";
			return "None";
		}
	}
}
