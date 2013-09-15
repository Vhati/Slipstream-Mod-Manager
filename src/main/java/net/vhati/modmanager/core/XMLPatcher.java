package net.vhati.modmanager.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.vhati.modmanager.core.SloppyXMLParser;

import org.jdom2.Attribute;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.AbstractFilter;
import org.jdom2.filter.ElementFilter;
import org.jdom2.filter.Filter;
import org.jdom2.input.JDOMParseException;
import org.jdom2.input.SAXBuilder;


/**
 * Programmatically edits existing XML with instructions from another XML doc.
 * Other tags are simply appended as-is.
 */
public class XMLPatcher {

	protected Namespace modNS;
	protected Namespace modAppendNS;
	protected Namespace modOverwriteNS;


	public XMLPatcher() {
		modNS = Namespace.getNamespace( "mod", "mod" );
		modAppendNS = Namespace.getNamespace( "mod-append", "mod-append" );
		modOverwriteNS = Namespace.getNamespace( "mod-overwrite", "mod-overwrite" );
	}


	public Document patch( Document mainDoc, Document appendDoc ) {
		Document resultDoc = mainDoc.clone();
		Element resultRoot = resultDoc.getRootElement();
		Element appendRoot = appendDoc.getRootElement();

		ElementFilter modFilter = new ElementFilter( modNS );
		for ( Content content : appendRoot.getContent() ) {
			if ( modFilter.matches( content ) ) {
				Element node = (Element)content;

				boolean handled = false;
				List<Element> matchedNodes = handleModFind( resultRoot, node );
				if ( matchedNodes != null ) {
					handled = true;
					for ( Element matchedNode : matchedNodes ) {
						handleModCommands( matchedNode, node );
					}
				}

				if ( !handled ) {
					throw new IllegalArgumentException( String.format( "Unrecognized mod tag <%s> (%s).", node.getName(), getPathToRoot(node) ) );
				}
			}
			else {
				resultRoot.addContent( content.clone() );
			}
		}

		return resultDoc;
	}


	/**
	 * Returns find results if node is a find tag, or null if it's not.
	 *
	 * An empty list will be returned if there were no matches.
	 *
	 * TODO: Throw an exception in callers if results are required.
	 */
	protected List<Element> handleModFind( Element contextNode, Element node ) {
		List<Element> result = null;

		if ( node.getNamespace().equals( modNS ) ) {

			if ( node.getName().equals( "findName" ) ) {

				String searchName = node.getAttributeValue( "name" );
				String searchType = node.getAttributeValue( "type" );
				boolean searchReverse = getAttributeBooleanValue( node, "reverse", true );
				int searchStart = getAttributeIntValue( node, "start", 0 );
				int searchLimit = getAttributeIntValue( node, "limit", 1 );
				boolean panic = getAttributeBooleanValue( node, "panic", false );
	
				if ( searchName == null || searchName.length() == 0 )
					throw new IllegalArgumentException( String.format( "<%s> requires a name attribute (%s).", node.getName(), getPathToRoot(node) ) );
				if ( searchType != null && searchType.length() == 0 )
					throw new IllegalArgumentException( String.format( "<%s> type attribute, when present, can't be empty (%s).", node.getName(), getPathToRoot(node) ) );
				if ( searchStart < 0 )
					throw new IllegalArgumentException( String.format( "<%s> 'start' attribute is not >= 0 (%s).", node.getName(), getPathToRoot(node) ) );
				if ( searchLimit < -1 )
					throw new IllegalArgumentException( String.format( "<%s> 'limit' attribute is not >= -1 (%s).", node.getName(), getPathToRoot(node) ) );
	
				Map<String,String> attrMap = new HashMap<String,String>();
				attrMap.put( "name", searchName );
				LikeFilter searchFilter = new LikeFilter( searchType, attrMap, null );
	
				List<Element> matchedNodes = new ArrayList<Element>( contextNode.getContent( searchFilter ) );
				if ( searchReverse ) Collections.reverse( matchedNodes );
	
				if ( searchStart < matchedNodes.size() ) {
					if ( searchLimit > -1 ) {
						matchedNodes = matchedNodes.subList( searchStart, Math.max( matchedNodes.size(), searchStart + searchLimit ) );
					} else if ( searchStart > 0 ) {
						matchedNodes = matchedNodes.subList( searchStart, matchedNodes.size() );
					}
				}
				if ( panic && matchedNodes.isEmpty() )
					throw new NoSuchElementException( String.format( "<%s> was set to require results but found none (%s).", node.getName(), getPathToRoot(node) ) );

				result = matchedNodes;
			}
			else if ( node.getName().equals( "findLike" ) ) {

				String searchType = node.getAttributeValue( "type" );
				boolean searchReverse = getAttributeBooleanValue( node, "reverse", false );
				int searchStart = getAttributeIntValue( node, "start", 0 );
				int searchLimit = getAttributeIntValue( node, "limit", -1 );
				boolean panic = getAttributeBooleanValue( node, "panic", false );
	
				if ( searchType != null && searchType.length() == 0 )
					throw new IllegalArgumentException( String.format( "<%s> type attribute, when present, can't be empty (%s).", node.getName(), getPathToRoot(node) ) );
				if ( searchStart < 0 )
					throw new IllegalArgumentException( String.format( "<%s> 'start' attribute is not >= 0 (%s).", node.getName(), getPathToRoot(node) ) );
				if ( searchLimit < -1 )
					throw new IllegalArgumentException( String.format( "<%s> 'limit' attribute is not >= -1 (%s).", node.getName(), getPathToRoot(node) ) );
	
				Map<String,String> attrMap = new HashMap<String,String>();
				String searchValue = null;
	
				Element selectorNode = node.getChild( "selector", modNS );
				if ( selectorNode != null ) {
					for ( Attribute attr : selectorNode.getAttributes() ) {
						if ( attr.getNamespace().equals( Namespace.NO_NAMESPACE ) ) {
							// Blank element values can't be detected as different from absent values (never null).
							// Forbid "" attributes for consistency. :/
							if ( attr.getValue().length() == 0 )
								throw new IllegalArgumentException( String.format( "<%s> attributes, when present, can't be empty (%s).", selectorNode.getName(), getPathToRoot(selectorNode) ) );

							attrMap.put( attr.getName(), attr.getValue() );
						}
					}
					searchValue = selectorNode.getTextTrim();  // Never null, but often "".
					if ( searchValue.length() > 0 ) searchValue = null;
				}
	
				LikeFilter searchFilter = new LikeFilter( searchType, attrMap, searchValue );
	
				List<Element> matchedNodes = new ArrayList<Element>( contextNode.getContent( searchFilter ) );
				if ( searchReverse ) Collections.reverse( matchedNodes );
	
				if ( searchStart < matchedNodes.size() ) {
					if ( searchLimit > -1 ) {
						matchedNodes = matchedNodes.subList( searchStart, Math.max( matchedNodes.size(), searchStart + searchLimit ) );
					} else if ( searchStart > 0 ) {
						matchedNodes = matchedNodes.subList( searchStart, matchedNodes.size() );
					}
				}
				if ( panic && matchedNodes.isEmpty() )
					throw new NoSuchElementException( String.format( "<%s> was set to require results but found none (%s).", node.getName(), getPathToRoot(node) ) );
	
				result = matchedNodes;
			}
			else if ( node.getName().equals( "findWithChildLike" ) ) {

				String searchType = node.getAttributeValue( "type" );
				String searchChildType = node.getAttributeValue( "child-type" );
				boolean searchReverse = getAttributeBooleanValue( node, "reverse", false );
				int searchStart = getAttributeIntValue( node, "start", 0 );
				int searchLimit = getAttributeIntValue( node, "limit", -1 );
				boolean panic = getAttributeBooleanValue( node, "panic", false );
	
				if ( searchType != null && searchType.length() == 0 )
					throw new IllegalArgumentException( String.format( "<%s> type attribute, when present, can't be empty (%s).", node.getName(), getPathToRoot(node) ) );
				if ( searchChildType != null && searchChildType.length() == 0 )
					throw new IllegalArgumentException( String.format( "<%s> child-type attribute, when present, can't be empty (%s).", node.getName(), getPathToRoot(node) ) );
				if ( searchStart < 0 )
					throw new IllegalArgumentException( String.format( "<%s> 'start' attribute is not >= 0 (%s).", node.getName(), getPathToRoot(node) ) );
				if ( searchLimit < -1 )
					throw new IllegalArgumentException( String.format( "<%s> 'limit' attribute is not >= -1 (%s).", node.getName(), getPathToRoot(node) ) );
	
				Map<String,String> attrMap = new HashMap<String,String>();
				String searchValue = null;
	
				Element selectorNode = node.getChild( "selector", modNS );
				if ( selectorNode != null ) {
					for ( Attribute attr : selectorNode.getAttributes() ) {
						if ( attr.getNamespace().equals( Namespace.NO_NAMESPACE ) ) {
							// TODO: Forbid "" attributes, because blank value doesn't work?
							attrMap.put( attr.getName(), attr.getValue() );
						}
					}
					searchValue = selectorNode.getTextTrim();  // Never null, but often "".
					if ( searchValue.length() > 0 ) searchValue = null;
				}
	
				LikeFilter searchChildFilter = new LikeFilter( searchChildType, attrMap, searchValue );
				WithChildFilter searchFilter = new WithChildFilter( searchType, searchChildFilter );

				List<Element> matchedNodes = new ArrayList<Element>( contextNode.getContent( searchFilter ) );
				if ( searchReverse ) Collections.reverse( matchedNodes );
	
				if ( searchStart < matchedNodes.size() ) {
					if ( searchLimit > -1 ) {
						matchedNodes = matchedNodes.subList( searchStart, Math.max( matchedNodes.size(), searchStart + searchLimit ) );
					} else if ( searchStart > 0 ) {
						matchedNodes = matchedNodes.subList( searchStart, matchedNodes.size() );
					}
				}
				if ( panic && matchedNodes.isEmpty() )
					throw new NoSuchElementException( String.format( "<%s> was set to require results but found none (%s).", node.getName(), getPathToRoot(node) ) );
	
				result = matchedNodes;
			}
			else if ( node.getName().equals( "findComposite" ) ) {

				boolean searchReverse = getAttributeBooleanValue( node, "reverse", false );
				int searchStart = getAttributeIntValue( node, "start", 0 );
				int searchLimit = getAttributeIntValue( node, "limit", -1 );
				boolean panic = getAttributeBooleanValue( node, "panic", false );
	
				if ( searchStart < 0 )
					throw new IllegalArgumentException( String.format( "<%s> 'start' attribute is not >= 0 (%s).", node.getName(), getPathToRoot(node) ) );
				if ( searchLimit < -1 )
					throw new IllegalArgumentException( String.format( "<%s> 'limit' attribute is not >= -1 (%s).", node.getName(), getPathToRoot(node) ) );

				Element parNode = node.getChild( "par", modNS );
				if ( parNode == null )
					throw new IllegalArgumentException( String.format( "<%s> requires a <par> tag (%s).", node.getName(), getPathToRoot(node) ) );

				List<Element> matchedNodes = handleModPar( contextNode, parNode );
				if ( searchReverse ) Collections.reverse( matchedNodes );
	
				if ( searchStart < matchedNodes.size() ) {
					if ( searchLimit > -1 ) {
						matchedNodes = matchedNodes.subList( searchStart, Math.max( matchedNodes.size(), searchStart + searchLimit ) );
					} else if ( searchStart > 0 ) {
						matchedNodes = matchedNodes.subList( searchStart, matchedNodes.size() );
					}
				}
				if ( panic && matchedNodes.isEmpty() )
					throw new NoSuchElementException( String.format( "<%s> was set to require results but found none (%s).", node.getName(), getPathToRoot(node) ) );
	
				result = matchedNodes;
			}
		}

		return result;
	}


	/**
	 * Returns collated find results (and par results, handled recursively), or null if node wasn't a par.
	 *
	 * Unique results from all finds will be combined and sorted in the order they appear under contextNode.
	 */
	protected List<Element> handleModPar( Element contextNode, Element node ) {
		List<Element> result = null;

		if ( node.getNamespace().equals( modNS ) ) {

			if ( node.getName().equals( "par" ) ) {

				String parOp = node.getAttributeValue( "op" );

				if ( parOp == null || (!parOp.equals("AND") && !parOp.equals("OR")) )
					throw new IllegalArgumentException( String.format( "Invalid \"op\" attribute (%s). Must be 'AND' or 'OR'.", getPathToRoot(node) ) );

				boolean isAnd = parOp.equals("AND");
				boolean isOr = parOp.equals("OR");

				Set<Element> candidateSet = new HashSet<Element>();
				for ( Element criteriaNode : node.getChildren() ) {
					List<Element> candidates;
					if ( criteriaNode.getName().equals( "par" ) && criteriaNode.getNamespace().equals( modNS ) ) {
						candidates = handleModPar( contextNode, criteriaNode );
					} else {
						candidates = handleModFind( contextNode, criteriaNode );
						if ( candidates == null )
							throw new IllegalArgumentException( String.format( "Invalid <par> search criteria <%s> (%s). Must be a <find...> or <par>.", criteriaNode.getName(), getPathToRoot(criteriaNode) ) );
					}

					if ( isOr || candidateSet.isEmpty() ) {
						candidateSet.addAll( candidates );
					}
					else if ( isAnd ) {
						candidateSet.retainAll( candidates );
					}
				}
				Map<Integer,Element> orderedCandidateMap = new TreeMap<Integer,Element>();
				for ( Element candidate : candidateSet ) {
					int index = contextNode.indexOf( candidate );
					orderedCandidateMap.put( new Integer(index), candidate );
				}

				List<Element> matchedNodes = new ArrayList<Element>( orderedCandidateMap.values() );

				result = matchedNodes;
			}
		}

		return result;
	}


	/**
	 * Performs child mod-commands under node, against contextNode.
	 *
	 * TODO: Maybe have handleModCommand() returning null when unrecognized,
	 * or an object with flags to continue or stop looping commands at
	 * contextNode (e.g., halting after removeTag).
	 */
	protected void handleModCommands( Element contextNode, Element node ) {

		for ( Element cmdNode : node.getChildren() ) {
			boolean handled = false;

			if ( cmdNode.getNamespace().equals( modNS ) ) {

				// Handle nested finds.
				List<Element> matchedNodes = handleModFind( contextNode, cmdNode );
				if ( matchedNodes != null ) {
					handled = true;
					for ( Element matchedNode : matchedNodes ) {
						handleModCommands( matchedNode, cmdNode );
					}
				}
				else if ( cmdNode.getName().equals( "selector" ) ) {
					handled = true;
					// No-op.
				}
				else if ( cmdNode.getName().equals( "par" ) ) {
					handled = true;
					// No-op.
				}
				else if ( cmdNode.getName().equals( "setAttributes" ) ) {
					handled = true;
					for ( Attribute attrib : cmdNode.getAttributes() ) {
						contextNode.setAttribute( attrib.clone() );
					}
				}
				else if ( cmdNode.getName().equals( "setValue" ) ) {
					handled = true;
					contextNode.setText( cmdNode.getTextTrim() );
				}
				else if ( cmdNode.getName().equals( "removeTag" ) ) {
					handled = true;
					contextNode.detach();
					break;
				}

			}

			else if ( cmdNode.getNamespace().equals( modAppendNS ) ) {
				// Append cmdNode (sans namespace) to the contextNode.
				handled = true;

				Element newNode = cmdNode.clone();
				newNode.setNamespace( null );
				contextNode.addContent( newNode );
			}

			else if ( cmdNode.getNamespace().equals( modOverwriteNS ) ) {
				// Remove the first child with the same type and insert cmdNode at its position.
				// Or just append if nothing was replaced.
				handled = true;

				Element newNode = cmdNode.clone();
				newNode.setNamespace( null );

				Element doomedNode = contextNode.getChild( cmdNode.getName(), null );
				if ( doomedNode != null ) {
					int doomedIndex = contextNode.indexOf( doomedNode );
					doomedNode.detach();
					contextNode.addContent( doomedIndex, newNode );
				}
				else {
					contextNode.addContent( newNode );
				}
			}

			if ( !handled ) {
				throw new IllegalArgumentException( String.format( "Unrecognized mod tag <%s> (%s).", cmdNode.getName(), getPathToRoot(cmdNode) ) );
			}
		}
	}


	/**
	 * Returns a string describing this element's location.
	 *
	 * Example: /root/event(SOME_NAME)/choice/text
	 */
	protected String getPathToRoot( Element node ) {
		StringBuilder buf = new StringBuilder();
		String chunk;
		String tmp;
		while ( node != null ) {
			chunk = "/"+ node.getName();

			tmp = node.getAttributeValue( "name" );
			if ( tmp != null && tmp.length() > 0 )
				chunk += "("+ tmp +")";

			buf.insert( 0, chunk );
			node = node.getParentElement();
		}
		return buf.toString();
	}


	/**
	 * Returns the boolean value of an attribute, or a default when the attribute is null.
	 * Only 'true' and 'false' are accepted.
	 */
	protected boolean getAttributeBooleanValue( Element node, String attrName, boolean defaultValue ) {
		String tmp = node.getAttributeValue( attrName );
		if ( tmp == null ) return defaultValue;

		if ( tmp.equals( "true" ) ) {
			return true;
		} else if ( tmp.equals( "false" ) ) {
			return false;
		} else {
			throw new IllegalArgumentException( String.format( "Invalid boolean attribute \"%s\" (%s). Must be 'true' or 'false'.", attrName, getPathToRoot(node) ) );
		}
	}

	/**
	 * Returns the int value of an attribute, or a default when the attribute is null.
	 */
	protected int getAttributeIntValue( Element node, String attrName, int defaultValue ) {
		String tmp = node.getAttributeValue( attrName );
		if ( tmp == null ) return defaultValue;
		try {
			return Integer.parseInt( tmp );
		}
		catch ( NumberFormatException e ) {
			throw new IllegalArgumentException( String.format( "Invalid int attribute \"%s\" (%s).", attrName, getPathToRoot(node) ) );
		}
	}


	/**
	 * Matches elements with equal type/attributes/value.
	 * Null args are ignored. A blank type or value arg is ignored.
	 * All given attributes must be present on a candidate to match.
	 * Attribute values in the map must not be null.
	 */
	protected static class LikeFilter extends AbstractFilter<Element> {
		private String type = null;;
		private Map<String,String> attrMap = null;
		private String value = null;

		public LikeFilter( String type, Element selectorNode ) {
			this.type = type;

			if ( selectorNode.hasAttributes() ) {
				this.attrMap = new HashMap<String,String>();
				for ( Attribute attr : selectorNode.getAttributes() ) {
					attrMap.put( attr.getName(), attr.getValue() );
				}
			}

			this.value = selectorNode.getTextTrim();
			if ( this.value.length() == 0 ) this.value = null;
		}

		public LikeFilter( String type, Map<String,String> attrMap, String value ) {
			super();
			if ( type != null && type.length() == 0 ) type = null;
			if ( value != null && value.length() == 0 ) value = null;

			this.type = type;
			this.attrMap = attrMap;
			this.value = value;
		}

		@Override
		public Element filter( Object content ) {
			if ( content instanceof Element == false ) return null;
			Element node = (Element)content;
			String tmp;

			if ( type != null ) {
				if ( type.equals( node.getName() ) == false ) {
					return null;
				}
			}

			if ( attrMap != null ) {
				for ( Map.Entry<String,String> entry : attrMap.entrySet() ) {
					String attrName = entry.getKey();
					String attrValue = entry.getValue();
					tmp = node.getAttributeValue( attrName );

					if ( attrValue.equals( tmp ) == false ) {
						return null;
					}
				}
			}

			if ( value != null ) {
				if ( value.equals( node.getTextTrim() ) == false ) {
					return null;
				}
			}
			return node;
		}
	}



	/**
	 * Matches elements with child elements that match a filter.
	 * If the filter is null, matches all elements with children.
	 */
	protected static class WithChildFilter extends AbstractFilter<Element> {
		private String type;
		private Filter<Element> childFilter;

		public WithChildFilter( Filter<Element> childFilter ) {
			this( null, childFilter );
		}

		public WithChildFilter( String type, Filter<Element> childFilter ) {
			this.type = type;
			this.childFilter = childFilter;
		}

		@Override
		public Element filter( Object content ) {
			if ( content instanceof Element == false ) return null;
			Element node = (Element)content;

			if ( type != null ) {
				if ( type.equals( node.getName() ) == false ) {
					return null;
				}
			}

			if ( childFilter != null ) {
				if ( node.getContent( childFilter ).isEmpty() )
					return null;
			}
			else if ( node.getChildren().isEmpty() ) {
				return null;
			}
			return node;
		}
	}
}
