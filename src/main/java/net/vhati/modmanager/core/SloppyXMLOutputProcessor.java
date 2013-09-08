package net.vhati.modmanager.core;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.jdom2.Attribute;
import org.jdom2.Content;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.LineSeparator;
import org.jdom2.output.XMLOutputter;
import org.jdom2.output.support.AbstractXMLOutputProcessor;
import org.jdom2.output.support.FormatStack;
import org.jdom2.output.support.Walker;
import org.jdom2.util.NamespaceStack;


/**
 * An XMLOutputProcessor that omits the root element.
 *
 * The root element will be invisible.
 * The root's namespace declarations will still count.
 * The root's immediate child content will not be indented.
 *
 * Adding namespace declarations to the root will prevent
 * descendents from having an xmlns:prefix="uri" attribute.
 *
 * To use, construct an XMLOutputter with this as an arg.
 * Then call outputter.output( doc, writer ).
 *
 * Or just call the static sloppyPrint() method below.
 *
 * @see org.jdom2.output.XMLOutputter
 */
public class SloppyXMLOutputProcessor extends AbstractXMLOutputProcessor {

	// Copied from AbstractXMLOutputProcessor in JDOM 2.0.5, with modification.
	@Override
	protected void printElement( Writer out, FormatStack fstack, NamespaceStack nstack, Element element ) throws IOException {
		nstack.push( element );
		try {
			final List<Content> content = element.getContent();

			if ( !element.isRootElement() ) {
				write( out, "<" );
				write( out, element.getQualifiedName() );
				for ( final Namespace ns : nstack.addedForward() ) {
					printNamespace( out, fstack, ns );
				}
				if ( element.hasAttributes() ) {
					for ( final Attribute attribute : element.getAttributes() ) {
						printAttribute( out, fstack, attribute );
					}
				}

				if ( content.isEmpty() ) {
					if ( fstack.isExpandEmptyElements() ) {
						write( out, "></" );
						write( out, element.getQualifiedName() );
						write( out, ">" );
					} else {
						write( out, " />" );
					}
					return;
				}
			}

			if ( element.isRootElement() ) {
				// Undo indention that comes with push().
				String prevIndent = fstack.getLevelIndent();
				fstack.push();
				fstack.setLevelIndent( prevIndent );
			}
			else {
				fstack.push();
			}

			try {
				final String space = element.getAttributeValue( "space", Namespace.XML_NAMESPACE );
				if ( "default".equals(space) ) {
					fstack.setTextMode( fstack.getDefaultMode() );
				}
				else if ( "preserve".equals(space) ) {
					fstack.setTextMode( Format.TextMode.PRESERVE );
				}

				Walker walker = buildWalker( fstack, content, true );
				if ( !walker.hasNext() ) {
					// The walker has formatted away whatever content we had.
					// But there WAS content, so expand the tag.
					// Omitted: />
					if ( !element.isRootElement() ) {
						write( out, "></" );
						write( out, element.getQualifiedName() );
						write( out, ">" );
					}
					return;
				}
				// We have some content.

				if ( !element.isRootElement() ) {
					write( out, ">" );
				}

				if ( !walker.isAllText() ) {
					// We need to newline/indent.
					textRaw( out, fstack.getPadBetween() );
				}

				printContent( out, fstack, nstack, walker );

				if ( !walker.isAllText() ) {
					// We need to newline/indent.
					textRaw( out, fstack.getPadLast() );
				}

				if ( !element.isRootElement() ) {
					write( out, "</" );
					write( out, element.getQualifiedName() );
					write( out, ">" );
				}
			}
			finally {
				fstack.pop();
			}
		}
		finally {
			nstack.pop();
		}

		// Technically, nstack.push(), super.printElement(), nstack.pop()
		//   would be enough to make namespaces look already declared.
		// But to avoid having to loop over root's child content to feed
		//   XMLOutputter, means writing most of this method just for
		//   root (omitting the tag printing parts).
		// And to expand tags that have blank content while still
		//   trimming, means writing the whole method (excluding root
		//    with if blocks).
	}


	/**
	 * Creates an outputter and writes an XML tree.
	 *
	 * The encoding argument here only sets an attribute in the
	 * XML declaration. It's up to the caller to ensure the writer
	 * is encoding bytes to match. If encoding is null, the default
	 * is "UTF-8".
	 *
	 * LineEndings will be CR-LF. Except for comments!?
	 */
	public static void sloppyPrint( Document doc, Writer writer, String encoding ) throws IOException {
		Format format = Format.getPrettyFormat();
		format.setExpandEmptyElements( false );
		format.setOmitDeclaration( false );
		format.setIndent( "\t" );
		format.setLineSeparator( LineSeparator.CRNL );

		if ( encoding != null ) format.setEncoding( encoding );

		XMLOutputter outputter = new XMLOutputter( format, new SloppyXMLOutputProcessor() );
		outputter.output( doc, writer );
	}
}
