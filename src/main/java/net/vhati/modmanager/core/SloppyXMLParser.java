package net.vhati.modmanager.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xml.sax.SAXParseException;

import org.jdom2.Attribute;
import org.jdom2.AttributeType;
import org.jdom2.CDATA;
import org.jdom2.Comment;
import org.jdom2.Content;
import org.jdom2.DefaultJDOMFactory;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.IllegalAddException;
import org.jdom2.JDOMFactory;
import org.jdom2.Namespace;
import org.jdom2.Parent;
import org.jdom2.Text;
import org.jdom2.input.JDOMParseException;


/**
 * A scraper for malformed XML.
 *
 * Sloppiness:
 *   Any closing tag, regardless of its name, closes the parent tag.
 *   <!-- <!-- blah --> is valid.
 *     The example above will become two comments. Any extra dashes will
 *     be discarded.
 *   --> can occur alone (discarded).
 *   An attribute name can start right after the quote from a prior value.
 *   Namespace prefixes for nodes and attributes are unique.
 *     (Each prefix will be used as the namespace's URI).
 *   Unrecognized named entities (&...;) and lone ampersands are accepted
 *     as literal text. (Those ampersands will be escaped if outputted).
 *
 * The text must have \n line endings.
 *
 * If a line/column aware JDOMFactory is passed to the constructor,
 *   that factory will receive locations for Elements (start tags).
 *   That will be the 1-based line/col of the end character,
 *   plus 1 col.
 *
 * If parsing fails, the thrown JDOMParseException has getter methods
 * to report the nearest upcoming non-whitespace character, from where
 * the parser gave up.
 *
 * Only use this as a last resort, after a real parser fails.
 *
 * @see org.jdom2.input.JDOMParseException
 * @see org.jdom2.located.LocatedJDOMFactory
 */
public class SloppyXMLParser {

	private Pattern declPtn = Pattern.compile( "(\\s*)<[?]xml [^?]*[?]>" );
	private Pattern emptyCommentPtn = Pattern.compile( "(\\s*)<!---->" );
	private Pattern commentPtn = Pattern.compile( "(?s)(\\s*)<!--((?:.(?!-->))*.)-->" );
	private Pattern emptyCDATAPtn = Pattern.compile( "(\\s*)<!\\[CDATA\\[\\]\\]>" );
	private Pattern cdataPtn = Pattern.compile( "(?s)(\\s*)<!\\[CDATA\\[((?:.(?!\\]\\]>))*.)\\]\\]>" );
	private Pattern sTagPtn = Pattern.compile( "(\\s*)<(?:([\\w.-]+):)?([\\w.-]+)((?: [^>]+?)??)\\s*(/?)>" );
	private Pattern eTagPtn = Pattern.compile( "([^<]*)</\\s*([^>]+)>" );
	private Pattern endSpacePtn = Pattern.compile( "\\s+$" );
	private Pattern strayCharsPtn = Pattern.compile( "(\\s*)(?:-->|[-.>,])" );

	private Pattern attrPtn = Pattern.compile( "\\s*(?:([\\w.-]+):)?([\\w.-]+)\\s*=\\s*(\"[^\"]*\"|'[^']*')" );
	private Pattern entityPtn = Pattern.compile( "&(?:(?:#([0-9]+))|(?:#x([0-9A-Fa-f]+))|([^;]+));" );

	private Pattern breakPtn = Pattern.compile( "\n" );

	private List<Pattern> chunkPtns = new ArrayList<Pattern>();
	private Map<String,String> entityMap = new HashMap<String,String>();

	private JDOMFactory factory;

	private int pos = -1;


	public SloppyXMLParser() {
		this( null );
	}

	public SloppyXMLParser( JDOMFactory factory ) {
		if ( factory == null ) factory = new DefaultJDOMFactory();
		this.factory = factory;

		chunkPtns.add( declPtn );
		chunkPtns.add( emptyCommentPtn );
		chunkPtns.add( commentPtn );
		chunkPtns.add( emptyCDATAPtn );
		chunkPtns.add( cdataPtn );
		chunkPtns.add( sTagPtn );
		chunkPtns.add( eTagPtn );
		chunkPtns.add( endSpacePtn );
		chunkPtns.add( strayCharsPtn );

		entityMap.put( "lt", "<" );
		entityMap.put( "gt", ">" );
		entityMap.put( "amp", "&" );
		entityMap.put( "apos", "'" );
		entityMap.put( "quot", "\"" );
	}


	public Document build( CharSequence s ) throws JDOMParseException {
		Element rootNode = factory.element( "wrapper" );
		Document doc = factory.document( rootNode );

		Parent parentNode = rootNode;
		int sLen = s.length();
		int lastPos = -1;
		pos = 0;
		int[] lastLineAndCol = new int[] {0, 0};  // Counts \n's and chars after the last \n.
		String tmp = null;
		Matcher m = declPtn.matcher( s );

		try {
			while ( pos > lastPos && pos < sLen ) {
				m.region( pos, sLen );
				boolean matchedChunk = false;

				for ( Pattern chunkPtn : chunkPtns ) {
					m.usePattern( chunkPtn );
					if ( !m.lookingAt() ) continue;

					if ( chunkPtn == declPtn ) {
						// Don't care.
						addLineAndCol( lastLineAndCol, m.group(0) );
					}
					else if ( chunkPtn == emptyCommentPtn ) {
						String whitespace = m.group( 1 );
						if ( whitespace.length() > 0 )
							factory.addContent( parentNode, factory.text( whitespace ) );

						addLineAndCol( lastLineAndCol, s, m.start(), m.end() );
					}
					else if ( chunkPtn == commentPtn ) {
						String whitespace = m.group( 1 );
						if ( whitespace.length() > 0 )
							factory.addContent( parentNode, factory.text( whitespace ) );

						tmp = m.group( 2 );
						if ( tmp.length() == 0 ) {
							factory.addContent( parentNode, factory.comment( "" ) );
						}
						else {
							Matcher splicedMatcher = Pattern.compile( "(\\s*)<!--" ).matcher( tmp );
							int commentStart = 0;
							while ( splicedMatcher.find() ) {
								if ( splicedMatcher.start() - commentStart > 0 ) {
									String splicedChunk = tmp.substring( commentStart, splicedMatcher.start() );
									splicedChunk = splicedChunk.replaceAll( "^-+|(?<=-)-+|-+$", "" );
									if ( splicedChunk.startsWith( " " ) ) splicedChunk += " ";
									Comment commentNode = factory.comment( splicedChunk );
									factory.addContent( parentNode, commentNode );
								}
								if ( splicedMatcher.group(1).length() > 0 ) {
									// Whitespace between comments.
									factory.addContent( parentNode, factory.text( splicedMatcher.group(1) ) );
								}
								commentStart = splicedMatcher.end();
							}
							if ( commentStart < tmp.length() ) {
								String finalChunk = tmp.substring( commentStart );
								finalChunk = finalChunk.replaceAll( "^-+|(?<=-)-+|-+$", "" );
								Comment commentNode = factory.comment( finalChunk );
								factory.addContent( parentNode, commentNode );
							}
						}

						addLineAndCol( lastLineAndCol, s, m.start(), m.end() );
					}
					else if ( chunkPtn == emptyCDATAPtn ) {
						String whitespace = m.group( 1 );
						if ( whitespace.length() > 0 )
							factory.addContent( parentNode, factory.text( whitespace ) );

						addLineAndCol( lastLineAndCol, s, m.start(), m.end() );
					}
					else if ( chunkPtn == cdataPtn ) {
						String whitespace = m.group( 1 );
						if ( whitespace.length() > 0 )
							factory.addContent( parentNode, factory.text( whitespace ) );

						CDATA cdataNode = factory.cdata( m.group(2) );
						factory.addContent( parentNode, cdataNode );

						addLineAndCol( lastLineAndCol, s, m.start(), m.end() );
					}
					else if ( chunkPtn == sTagPtn ) {
						String whitespace = m.group( 1 );
						if ( whitespace.length() > 0 )
							factory.addContent( parentNode, factory.text( whitespace ) );

						String nodePrefix = m.group( 2 );  // Might be null.
						String nodeName = m.group( 3 );
						String attrString = m.group( 4 );
						boolean selfClosing = ( m.group( 5 ).length() > 0 );

						addLineAndCol( lastLineAndCol, s, m.start(), m.end() );

						Element tagNode;
						if ( nodePrefix != null ) {
							Namespace nodeNS = Namespace.getNamespace( nodePrefix, nodePrefix );  // URI? *shrug*
							factory.addNamespaceDeclaration( rootNode, nodeNS );
							tagNode = factory.element( lastLineAndCol[0]+1, lastLineAndCol[1]+1+1, nodeName, nodeNS );
						} else {
							tagNode = factory.element( lastLineAndCol[0]+1, lastLineAndCol[1]+1+1, nodeName );
						}

						if ( attrString.length() > 0 ) {
							Matcher am = attrPtn.matcher( attrString );
							while ( am.lookingAt() ) {
								String attrPrefix = am.group( 1 );  // Might be null.
								String attrName = am.group( 2 );
								String attrValue = am.group( 3 );
								attrValue = attrValue.substring( 1, attrValue.length()-1 );
								attrValue = unescape( attrValue );

								if ( attrPrefix != null ) {
									if ( attrPrefix.equals( "xmlns" ) ) {
										// This is a pseudo attribute declaring a namespace prefix.
										// Move it to the root node.
										Namespace attrNS = Namespace.getNamespace( attrName, attrName );  // URI? *shrug*
										factory.addNamespaceDeclaration( rootNode, attrNS );
									}
									else {
										Namespace attrNS = Namespace.getNamespace( attrPrefix, attrPrefix );  // URI? *shrug*
										factory.addNamespaceDeclaration( rootNode, attrNS );
										Attribute attrObj = factory.attribute( attrName, attrValue, AttributeType.UNDECLARED, attrNS );
										factory.setAttribute( tagNode, attrObj );
									}
								} else if ( attrName.equals("xmlns") ) {
									// New default namespace URI within this node.
									Namespace attrNS = Namespace.getNamespace( attrValue );
									factory.addNamespaceDeclaration( tagNode, attrNS );
								} else {
									// Normal attribute.
									Attribute attrObj = factory.attribute( attrName, attrValue, AttributeType.UNDECLARED, Namespace.NO_NAMESPACE );
									factory.setAttribute( tagNode, attrObj );
								}
								am.region( am.end(), am.regionEnd() );
							}
							if ( am.regionStart() < attrString.length() ) {
								int nonspacePos = findNextNonspace( s, pos );
								int errorPos = ( (nonspacePos != -1) ? nonspacePos : pos );

								int[] lineAndCol = getLineAndCol( s, errorPos );
								int lineNum = lineAndCol[0];
								int colNum = lineAndCol[1];

								SAXParseException cause = new SAXParseException( String.format( "At line %d, column %d: Strange attributes.", lineNum, colNum ), null, null, lineNum, colNum );
								throw new JDOMParseException( String.format( "Error on line %d: %s", lineNum, cause.getMessage() ), cause );
							}
						}

						factory.addContent( parentNode, tagNode );
						if ( !selfClosing ) parentNode = tagNode;
					}
					else if ( chunkPtn == eTagPtn ) {
						String interimText = m.group( 1 );
						interimText = unescape( interimText );

						factory.addContent( parentNode, factory.text( interimText ) );
						parentNode = parentNode.getParent();

						addLineAndCol( lastLineAndCol, s, m.start(), m.end() );
					}
					else if ( chunkPtn == endSpacePtn ) {
						// This is the end of the document.
					}
					else if ( chunkPtn == strayCharsPtn ) {
						// Non-space junk between an end tag and a start tag.

						String whitespace = m.group( 1 );
						if ( whitespace.length() > 0 )
							factory.addContent( parentNode, factory.text( whitespace ) );

						addLineAndCol( lastLineAndCol, s, m.start(), m.end() );
					}

					matchedChunk = true;
					lastPos = pos;
					pos = m.end();
					break;
				}

				if ( !matchedChunk ) {
					int nonspacePos = findNextNonspace( s, pos );
					int errorPos = ( (nonspacePos != -1) ? nonspacePos : pos );

					int[] lineAndCol = getLineAndCol( s, errorPos );
					int lineNum = lineAndCol[0];
					int colNum = lineAndCol[1];

					SAXParseException cause = new SAXParseException( String.format( "At line %d, column %d: Unexpected characters.", lineNum, colNum ), null, null, lineNum, colNum );
					throw new JDOMParseException( String.format( "Error on line %d: %s", lineNum, cause.getMessage() ), cause );
				}
			}

			if ( rootNode.getChildren().size() == 1 ) {
				// No need for the wrapper, promote its only child to root.

				Element newRoot = rootNode.getChildren().get( 0 );
				newRoot.detach();
				for ( Namespace ns : rootNode.getAdditionalNamespaces() ) {
					factory.addNamespaceDeclaration( newRoot, ns );
				}
				factory.setRoot( doc, newRoot );
			}

		}
		catch( IllegalAddException e ) {
			int nonspacePos = findNextNonspace( s, pos );
			int errorPos = ( (nonspacePos != -1) ? nonspacePos : pos );

			int[] lineAndCol = getLineAndCol( s, errorPos );
			int lineNum = lineAndCol[0];
			int colNum = lineAndCol[1];

			String hint = "";
			if ( e.getMessage() != null && e.getMessage().contains( "not allowed at the document root" ) ) {
				hint = " (There's likely an extraneous closing tag before this point.)";
			}
			SAXParseException cause = new SAXParseException( String.format( "At line %d, column %d: %s%s", lineNum, colNum, e.getMessage(), hint ), null, null, lineNum, colNum, e );
			throw new JDOMParseException( String.format( "Error on line %d: %s", lineNum, cause.getMessage() ), cause );
		}

		return doc;
	}


	/**
	 * Unescapes standard named entities and numeric character references.
	 * This applies to attributes and element values.
	 *
	 * They are: lt, gt, quot, apos, amp, #1234, #x1a2b.
	 */
	public String unescape( String s ) {
		StringBuffer buf = new StringBuffer( s.length() );
		Matcher m = entityPtn.matcher( s );
		String decRef;
		String hexRef;
		int charCode;
		String entName;
		String entity;

		while ( m.find() ) {
			decRef = m.group( 1 );
			hexRef = m.group( 2 );
			entName = m.group( 3 );
			if ( (decRef != null) ) {
				// Decimal character reference.
				charCode = Integer.parseInt( decRef );
				entity = Character.toString( (char)charCode );
			}
			else if ( (hexRef != null) ) {
				// Hex character reference.
				charCode = Integer.parseInt( hexRef, 16 );
				entity = Character.toString( (char)charCode );
			}
			else {
				entity = entityMap.get( entName );
				if ( entity == null ) {
					// Unknown entity, repeat it as-is.
					entity = "&"+ entName +";";
				}
			}
			m.appendReplacement( buf, entity );
		}
		m.appendTail( buf );

		return buf.toString();
	}


	/**
	 * Returns the position of the next non whitespace character after pos.
	 *
	 * Returns -1 if there isn't one.
	 */
	public int findNextNonspace( CharSequence s, int pos ) {
		Matcher nonspaceMatcher = Pattern.compile( "\\S" ).matcher( s );
		if ( nonspaceMatcher.find( pos ) )
			return nonspaceMatcher.start();

		return -1;
	}


	/**
	 * Increments an ongoing tally of lines and the col on the current line.
	 *
	 * @param lastLineAndCol the current tally to increment (0-based)
	 * @param s a string to check for \n's
	 * @param start a start index in the string to search from (inclusive)
	 * @param start an end index in the string (exclusive)
	 */
	private void addLineAndCol( int[] lastLineAndCol, CharSequence s, int start, int end ) {
		if ( s.length() == 0 || start == end ) return;

		Matcher breakMatcher = breakPtn.matcher( s );
		breakMatcher.region( start, end );
		int breakCount = 0;
		int lastBreakPos = -1;
		while ( breakMatcher.find() ) {
			lastBreakPos = breakMatcher.start();
			breakCount++;
		}
		if ( lastBreakPos == -1 ) {
			// Same line, a few more chars in. Increment col.
			lastLineAndCol[1] += end-1 - start;
		} else {
			// On a new line now, reset the col.
			lastLineAndCol[0] += breakCount;
			lastLineAndCol[1] = end-1 - lastBreakPos;
		}
	}

	private void addLineAndCol( int[] lastLineAndCol, CharSequence s ) {
		addLineAndCol( lastLineAndCol, s, 0, s.length() );
	}


	/**
	 * Returns lineNum and colNum for a position in text.
	 * The first line is line 1.
	 * Line breaks start a new line as col 0.
	 * The first char of each line, after the break is col 1.
	 *
	 * @param pos a 0-based offset
	 * @return 1-based ints for line and col (the first char is line 1, col 1)
	 * @see org.jdom2.input.JDOMParseException
	 */
	public int[] getLineAndCol( CharSequence s, int pos ) {
		pos = Math.min( pos, s.length() );

		Matcher breakMatcher = breakPtn.matcher( s );
		breakMatcher.region( 0, pos+1 );  // Include pos itself in case it's a break.
		int breakCount = 0;
		int lastBreakPos = -1;
		while ( breakMatcher.find() ) {
			lastBreakPos = breakMatcher.start();
			breakCount++;
		}
		int colNum;
		if ( lastBreakPos == -1 )
			colNum = pos+1;  // Pretend ^ was column 0, as a \n would.
		else
			colNum = pos - lastBreakPos;

		return new int[] { breakCount+1, colNum };
	}


	/**
	 * Returns the last character offset this parser was looking at.
	 *
	 * Usually this will be a patch of whitespace prior to unrecognized chars.
	 * This method is a fallback when an unexpected exception doesn't provide
	 * line info.
	 *
	 * @see findNextNonspace(CharSequence s, int pos)
	 */
	public int getLastPosition() {
		return pos;
	}
}
