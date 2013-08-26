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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.vhati.modmanager.core.Report;
import net.vhati.modmanager.core.Report.ReportMessage;
import net.vhati.modmanager.core.SloppyXMLParser;

import ar.com.hjg.pngj.PngReader;

import org.jdom2.Document;
import org.jdom2.input.JDOMParseException;
import org.jdom2.input.SAXBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ModUtilities {

	private static final Logger log = LogManager.getLogger(ModUtilities.class);

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
		while ( (len = is.read(buf)) >= 0 ) {
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
	 * Semi-intelligently appends XML from one file (src) onto another (dst).
	 *
	 * The two InputStreams are read, and the combined result
	 * is returned as a new third InputStream.
	 *
	 * The returned stream is a ByteArrayInputStream
	 * which doesn't need closing.
	 *
	 * The result will be UTF-8 with CR-LF line endings.
	 *
	 * The description arguments identify the streams for log messages.
	 */
	public static InputStream appendXMLFile( InputStream srcStream, InputStream dstStream, String srcDescription, String dstDescription ) throws IOException {
		Pattern xmlDeclPtn = Pattern.compile( "<[?]xml version=\"1.0\" encoding=\"[^\"]+?\"[?]>\n*" );

		String srcText = decodeText( srcStream, srcDescription ).text;
		srcText = xmlDeclPtn.matcher(srcText).replaceFirst( "" );

		String dstText = decodeText( dstStream, dstDescription ).text;
		dstText = xmlDeclPtn.matcher(dstText).replaceFirst( "" );

		StringBuilder buf = new StringBuilder( srcText.length() +100+ dstText.length() );
		buf.append( "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" );
		buf.append( dstText );
		buf.append( "\n\n<!-- Appended by Slipstream -->\n\n" );
		buf.append( srcText );
		buf.append( "\n" );

		String mergedString = Pattern.compile("\n").matcher( buf ).replaceAll("\r\n");

		ByteArrayOutputStream tmpData = new ByteArrayOutputStream();
		BufferedWriter bw = new BufferedWriter( new OutputStreamWriter( tmpData, "UTF-8" ) );
		bw.write( mergedString );
		bw.flush();

		InputStream result = new ByteArrayInputStream( tmpData.toByteArray() );
		return result;
	}


	/**
	 * Calls decodeText() on a stream, replaces line endings, and re-encodes.
	 *
	 * The returned stream is a ByteArrayInputStream
	 * which doesn't need closing.
	 *
	 * The result will be UTF-8 with the desired line endings.
	 *
	 * The description argument identifies the stream for log messages.
	 */
	public static InputStream setLineEndings( InputStream srcStream, String eol, String srcDescription ) throws IOException {
		// decodeText() returns a LF string.
		String srcText = decodeText( srcStream, srcDescription ).text;
		String fixedText = Pattern.compile("\n").matcher( srcText ).replaceAll( Matcher.quoteReplacement(eol) );

		ByteArrayOutputStream tmpData = new ByteArrayOutputStream();
		BufferedWriter bw = new BufferedWriter( new OutputStreamWriter( tmpData, "UTF-8" ) );
		bw.write( fixedText );
		bw.flush();

		InputStream result = new ByteArrayInputStream( tmpData.toByteArray() );
		return result;
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

		Pattern junkFilePtn = Pattern.compile( "[.]DS_Store$|^thumbs[.]db$" );

		Pattern validRootDirPtn = Pattern.compile( "^(?:audio|data|fonts|img)/" );
		List<String> seenJunkDirs = new ArrayList<String>();

		CharsetEncoder asciiEncoder = Charset.forName("US-ASCII").newEncoder();

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

				if ( !asciiEncoder.canEncode( innerPath ) ) {
					pendingMsgs.add( new ReportMessage(
						ReportMessage.ERROR,
						String.format( "Non-ASCII characters in path." )
					) );
					modValid = false;
				}

				if ( innerPath.indexOf("/") == -1 ) {
					pendingMsgs.add( new ReportMessage(
						ReportMessage.WARNING,
						String.format( "Extraneous top-level file." )
					) );
					modValid = false;
				}
				else if ( !validRootDirPtn.matcher(innerPath).find() ) {
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
				else if ( junkFilePtn.matcher(innerPath).find() ) {
					pendingMsgs.add( new ReportMessage(
						ReportMessage.ERROR,
						String.format( "Junk file" )
					) );
					modValid = false;
				}
				else if ( innerPath.endsWith( "[.]png" ) ) {
					try {
						PngReader pngr = new PngReader( zis );

						// Check for Truecolor+Alpha (32bit RGBA).
						if ( pngr.imgInfo.channels != 4 || pngr.imgInfo.bitDepth != 8 ) {

							String colorTypeString = "???";
							if ( pngr.imgInfo.channels == 4 )
								colorTypeString = "RGB+Alpha";
							else if ( pngr.imgInfo.channels == 3 )
								colorTypeString = "RGB";
							else if ( pngr.imgInfo.channels == 2 )
								colorTypeString = "Gray+Alpha";
							else if ( pngr.imgInfo.channels == 1 && !pngr.imgInfo.greyscale )
								colorTypeString = "Indexed Color";
							else if ( pngr.imgInfo.channels == 1 && pngr.imgInfo.greyscale )
								colorTypeString = "Gray";

							pendingMsgs.add( new ReportMessage(
								ReportMessage.WARNING,
								String.format( "ColorType: %s (Usually 32bit Truecolor+Alpha)", colorTypeString )
							) );
						}
					}
					catch ( Exception e ) {
						log.error( String.format( "Error while validating \"%s:%s\".", modFile.getName(), innerPath ), e );
						pendingMsgs.add( new ReportMessage(
							ReportMessage.ERROR,
							"An error occurred. See log for details."
						) );
						modValid = false;
					}
				}
				else if ( innerPath.matches( "^.*(?:[.]xml[.]append|[.]append[.]xml|[.]xml|[.]txt)$" ) ) {
					boolean isTxt = innerPath.matches( "^.*(?:[.]txt)$" );
					boolean isXML = innerPath.matches( "^.*(?:[.]xml[.]append|[.]append[.]xml|[.]xml)$" );
					boolean isXMLAppend = innerPath.matches( "^.*(?:[.]xml[.]append|[.]append[.]xml)$" );

					DecodeResult decodeResult = ModUtilities.decodeText( zis, modFile.getName()+":"+innerPath );

					if ( decodeResult.bom != null ) {
						pendingMsgs.add( new ReportMessage(
							ReportMessage.WARNING,
							String.format( "%s BOM detected. (Some tools may not handle BOMs)", decodeResult.encoding )
						) );
						modValid = false;
					}

					if ( decodeResult.encoding.equalsIgnoreCase( "windows-1252" ) ) {
						pendingMsgs.add( new ReportMessage(
							ReportMessage.WARNING,
							String.format( "Fancy %s chars. (UTF-8 is recommended for that)", decodeResult.encoding )
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

					List<Pattern> oddCharPtns = new ArrayList<Pattern>();
					Map<Pattern,String> oddCharSuggestions = new HashMap<Pattern,String>();
					Map<Pattern,List<Character>> oddCharLists = new HashMap<Pattern,List<Character>>();

					oddCharPtns.add( Pattern.compile( "\\u0060|\\u201A|\\u2018|\\u2019" ) );
					oddCharSuggestions.put( oddCharPtns.get(oddCharPtns.size()-1), "'" );

					oddCharPtns.add( Pattern.compile( "\\u201E|\\u201C|\\u201D" ) );
					oddCharSuggestions.put( oddCharPtns.get(oddCharPtns.size()-1), "\"" );

					oddCharPtns.add( Pattern.compile( "\\u2013|\\u2014" ) );
					oddCharSuggestions.put( oddCharPtns.get(oddCharPtns.size()-1), "-" );

					oddCharPtns.add( Pattern.compile( "\\u2026" ) );
					oddCharSuggestions.put( oddCharPtns.get(oddCharPtns.size()-1), "..." );

					for ( Pattern ptn : oddCharPtns ) {
						Matcher m = ptn.matcher( decodeResult.text );
						List<Character> chars = null;
						while ( m.find() ) {
							if ( chars == null )
								chars = new ArrayList<Character>();

							Character cObj = new Character( m.group(0).charAt(0) );
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

					// TODO: Nag if there are chars FTL can't show.

					if ( isXML ) {
						if ( isXMLAppend ) seenAppend = true;

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
								"Normal XML Parser Issues:",
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
		Pattern xmlDeclPtn = Pattern.compile( "<[?]xml version=\"1.0\" encoding=\"[^\"]+?\"[?]>" );
		m = xmlDeclPtn.matcher( srcBuf );
		boolean foundTopDecl = false;
		while ( m.find() ) {
			if ( m.start() == 0 ) {
				foundTopDecl = true;
				m.appendReplacement( dstBuf, "$0\n<wrapper>\n" );
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
			dstBuf.insert( 0, "<wrapper>\n" );

		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength(0);

		// Comments with long tails or double-dashes.
		m = Pattern.compile( "(?s)<!--(-*)(.*?)(-*)-->" ).matcher( srcBuf );
		while ( m.find() ) {
			if ( m.group(1).length() > 0 || m.group(3).length() > 0 || m.group(2).indexOf("--") != -1 ) {
				messages.add( new ReportMessage(
					ReportMessage.ERROR,
					"<!-- No other dashes should touch. -->"
				) );
			}
			m.appendReplacement( dstBuf, m.quoteReplacement(m.group(2).replaceAll("[^\n]", "")) );  // Strip comments, but preserve line count.
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength(0);

		// Mismatched single-line tags.
		// Example: blueprints.xml: <title>...</type>
		m = Pattern.compile( "<([^/!][^> ]+?)((?: [^>]+?)?)(?<!/)>([^<]+?)</([^>]+?)>" ).matcher( srcBuf );
		while ( m.find() ) {
			if ( m.group(1).equals( m.group(4) ) == false ) {
				messages.add( new ReportMessage(
					ReportMessage.ERROR,
					"<"+ m.group(1) +"...>...</"+ m.group(4) +">"
				) );
				m.appendReplacement( dstBuf, m.quoteReplacement("<"+ m.group(1) + m.group(2) +">"+ m.group(3) +"</"+ m.group(1) +">") );
			}
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength(0);

		// <pilot power="1"max="3" room="0"/>
		// Groan, \t separates attribs sometimes.
		m = Pattern.compile( "<([^> ]+?)( [^>]+?\")([^\"= \t>]+?=\"[^\"]+?\")((?:[^>]+?)?)>" ).matcher( srcBuf );
		while ( m.find() ) {
			messages.add( new ReportMessage(
				ReportMessage.ERROR,
				"<"+ m.group(1) +"...\""+ m.group(3) +"...>"
			) );
			m.appendReplacement( dstBuf, m.quoteReplacement("<"+ m.group(1) + m.group(2) +" "+ m.group(3) + m.group(4) +">") );
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength(0);

		// sector_data.xml closing tag.
		m = Pattern.compile( "((?s)<sectorDescription[^>]*>.*?)</sectorDescrption>" ).matcher( srcBuf );
		while ( m.find() ) {
			messages.add( new ReportMessage(
				ReportMessage.ERROR,
				"<sectorDescription>...</sectorDescrption>"
			) );
			m.appendReplacement( dstBuf, m.quoteReplacement(m.group(1) +"</sectorDescription>") );
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength(0);

		// {anyship}.xml: <gib1>...</gib2>
		m = Pattern.compile( "(?s)<(gib[0-9]+)>(.*?)</(gib[0-9]+)>" ).matcher( srcBuf );
		while ( m.find() ) {
			if ( m.group(1).equals( m.group(3) ) == false ) {
				messages.add( new ReportMessage(
					ReportMessage.ERROR,
					"<"+ m.group(1) +">...</"+ m.group(3) +">"
				) );
				m.appendReplacement( dstBuf, m.quoteReplacement("<"+ m.group(1) +">"+ m.group(2) +"</"+ m.group(1) +">") );
			}
			else {
				m.appendReplacement( dstBuf, m.quoteReplacement(m.group(0)) );
			}
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength(0);

		// event*.xml: <choice... hidden="true" hidden="true">
		m = Pattern.compile( "<([a-zA-Z0-9_-]+?)((?: [^>]+?)?) ([^>]+?)(=\"[^\">]+?\") \\3(?:=\"[^\">]+?\")([^>]*)>" ).matcher( srcBuf );
		while ( m.find() ) {
			messages.add( new ReportMessage(
				ReportMessage.ERROR,
				"<"+ m.group(1) +"... "+ m.group(3) +"=... "+ m.group(3) +"=...>"
			) );
			m.appendReplacement( dstBuf, m.quoteReplacement("<"+ m.group(1) + m.group(2) +" "+ m.group(3) + m.group(4) +" "+ m.group(5) +">") );
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength(0);

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
			m.appendReplacement( dstBuf, m.quoteReplacement(m.group(1) +"</shields>") );
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength(0);

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
			m.appendReplacement( dstBuf, m.quoteReplacement(m.group(1) +"</shipBlueprint>") );
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength(0);

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
			m.appendReplacement( dstBuf, m.quoteReplacement(m.group(1) +"</textList>") );
		}
		m.appendTail( dstBuf );
		tmpBuf = srcBuf; srcBuf = dstBuf; dstBuf = tmpBuf; dstBuf.setLength(0);

		Document doc = null;
		try {
			SAXBuilder saxBuilder = new SAXBuilder();
			doc = saxBuilder.build( new StringReader(srcBuf.toString()) );

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
				for ( int i=1; i <= lineNum && m.find(); i++) {
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
			log.error( "Error while validating mod xml.", e );
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
				for ( int i=1; i <= lineNum && m.find(); i++) {
					if ( i == lineNum-1 ) {
						badStart = m.end();
					} else if ( i == lineNum ) {
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
				messages.add( new ReportMessage(
					ReportMessage.EXCEPTION,
					"An error occurred. See log for details."
				) );
			}
			xmlValid = false;
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
