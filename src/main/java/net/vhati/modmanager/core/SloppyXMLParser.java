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
 * Only use this as a last resort, after a real parser fails.
 */
public class SloppyXMLParser {

	private Pattern declPtn = Pattern.compile( "(\\s*)<[?]xml [^?]*[?]>" );
	private Pattern commentPtn = Pattern.compile( "(?s)(\\s*)<!--((?:.(?!-->))*.)-->" );
	private Pattern cdataPtn = Pattern.compile( "(?s)(\\s*)<!\\[CDATA\\[((?:.(?!\\]\\]>))*.)\\]\\]>" );
	private Pattern sTagPtn = Pattern.compile( "(\\s*)<(?:([\\w.-]+):)?([\\w.-]+)((?: [^>]+?)??)\\s*(/?)>" );
	private Pattern eTagPtn = Pattern.compile( "([^<]*)</\\s*([^>]+)>" );
	private Pattern endSpacePtn = Pattern.compile( "\\s+$" );
	private Pattern strayCharsPtn = Pattern.compile( "(\\s*)(?:-->|[-.>,])" );

	private Pattern attrPtn = Pattern.compile( "\\s*(?:([\\w.-]+):)?([\\w.-]+)\\s*=\\s*(\"[^\"]*\"|'[^']*')" );
	private Pattern entityPtn = Pattern.compile( "&(?:(?:#([0-9]+))|(?:#x([0-9A-Fa-f]+))|([^;]+));" );

	private List<Pattern> chunkPtns = new ArrayList<Pattern>();
	private Map<String,String> entityMap = new HashMap<String,String>();

	private JDOMFactory factory;


	public SloppyXMLParser() {
		this( null );
	}

	public SloppyXMLParser( JDOMFactory factory ) {
		if ( factory == null ) factory = new DefaultJDOMFactory();
		this.factory = factory;

		chunkPtns.add( declPtn );
		chunkPtns.add( commentPtn );
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
		int pos = 0;
		String tmp = null;
		Matcher m = declPtn.matcher( s );

		while ( pos > lastPos && pos < sLen ) {
			m.region( pos, sLen );
			boolean matchedChunk = false;

			for ( Pattern chunkPtn : chunkPtns ) {
				m.usePattern( chunkPtn );
				if ( !m.lookingAt() ) continue;

				if ( chunkPtn == declPtn ) {
					// Don't care.
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
				}
				else if ( chunkPtn == cdataPtn ) {
					String whitespace = m.group( 1 );
					if ( whitespace.length() > 0 )
						factory.addContent( parentNode, factory.text( whitespace ) );

					CDATA cdataNode = factory.cdata( m.group(2) );
					factory.addContent( parentNode, cdataNode );
				}
				else if ( chunkPtn == sTagPtn ) {
					String whitespace = m.group( 1 );
					if ( whitespace.length() > 0 )
						factory.addContent( parentNode, factory.text( whitespace ) );

					String nodePrefix = m.group( 2 );  // Might be null.
					String nodeName = m.group( 3 );
					String attrString = m.group( 4 );
					boolean selfClosing = ( m.group( 5 ).length() > 0 );

					Element tagNode;
					if ( nodePrefix != null ) {
						Namespace nodeNS = Namespace.getNamespace( nodePrefix, nodePrefix );  // URI? *shrug*
						factory.addNamespaceDeclaration( rootNode, nodeNS );
						tagNode = factory.element( nodeName, nodeNS );
					} else {
						tagNode = factory.element( nodeName );
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
							int[] lineAndCol = getLineAndCol( s, pos );
							int lineNum = lineAndCol[0];
							int colNum = lineAndCol[1];

							SAXParseException cause = new SAXParseException( String.format( "At line %d, column %d: Strange attributes.", lineNum, colNum ), null, null, lineNum, colNum);
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
				}
				else if ( chunkPtn == endSpacePtn ) {
					// This is the end of the document.
				}
				else if ( chunkPtn == strayCharsPtn ) {
					// Non-space junk between an end tag and a start tag.

					String whitespace = m.group( 1 );
					if ( whitespace.length() > 0 )
						factory.addContent( parentNode, factory.text( whitespace ) );
				}

				matchedChunk = true;
				lastPos = pos;
				pos = m.end();
				break;
			}

			if ( !matchedChunk ) {
				int[] lineAndCol = getLineAndCol( s, pos );
				int lineNum = lineAndCol[0];
				int colNum = lineAndCol[1];

				SAXParseException cause = new SAXParseException( String.format( "At line %d, column %d: Unexpected characters.", lineNum, colNum ), null, null, lineNum, colNum);
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
	 * Returns lineNum and colNum for a position in text.
	 */
	private int[] getLineAndCol( CharSequence s, int pos ) {
		Matcher breakMatcher = Pattern.compile( "\n" ).matcher( s );
		breakMatcher.region( 0, pos+1 );
		int lastBreakPos = -1;
		int lineNum = 1;
		while ( breakMatcher.find() ) {
			lastBreakPos = breakMatcher.start();
			breakMatcher.region( breakMatcher.end(), breakMatcher.regionEnd() );
			lineNum++;
		}
		int colNum;
		if ( lastBreakPos == -1 )
			colNum = pos+1;
		else
			colNum = pos - lastBreakPos;

		return new int[] { lineNum, colNum };
	}
}
