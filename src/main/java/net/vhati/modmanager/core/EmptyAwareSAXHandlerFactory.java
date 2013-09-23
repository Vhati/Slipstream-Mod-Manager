package net.vhati.modmanager.core;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.jdom2.Element;
import org.jdom2.JDOMFactory;
import org.jdom2.Parent;
import org.jdom2.Text;
import org.jdom2.input.sax.SAXHandler;
import org.jdom2.input.sax.SAXHandlerFactory;


/**
 * A factory that produces SAXHandlers which distinguish tag-pairs from dual tags.
 *
 * While this factory is registered with a SAXBuilder, strict parsing
 * will occur as normal, notifying the SAXHandler of progress. When
 * the handler hears that a tag ended at a different line/column than
 * where it started, and no other text was reported along the way, a
 * zero-length text node will be added as token content.
 *
 * Later, when the Element tree is printed, a special
 * XMLOutputProcessor can reproduce the tag pair pased on the
 * presence of child Content objects - like the dummy string - rather
 * than the presence of non-whitespace text after formatting.
 *
 * Note: Not all XMLReaders offer a Locator for line/col info. When
 * that info is not available, this handler will do nothing special.
 *
 * @see org.jdom2.input.SAXBuilder#setSAXHandlerFactory(SAXHandlerFactory factory)
 */
public class EmptyAwareSAXHandlerFactory implements SAXHandlerFactory {

	@Override
	public SAXHandler createSAXHandler( JDOMFactory factory ) {
		return new EmptyAwareSAXHandler( factory );
	}



	public static class EmptyAwareSAXHandler extends SAXHandler {

		boolean emptyTag = true;
		int startTagLine = -1;
		int startTagColumn = -1;

		public EmptyAwareSAXHandler( JDOMFactory factory ) {
			super( factory );
		}

		@Override
		public void startElement( String namespaceURI, String localName, String qName, Attributes atts ) throws SAXException {
			emptyTag = true;
			startTagLine = -1;
			startTagColumn = -1;

			Locator loc = getDocumentLocator();
			if ( loc != null ) {
				startTagLine = loc.getLineNumber();
				startTagColumn = loc.getColumnNumber();
			}
			super.startElement( namespaceURI, localName, qName, atts );
		}

		@Override
		public void comment( char[] ch, final int start, final int length ) throws SAXException {
			emptyTag = false;
			super.comment( ch, start, length );
		}

		@Override
		public void characters( char[] ch, final int start, final int length ) throws SAXException {
			emptyTag = false;
			super.characters( ch, start, length );
		}

		@Override
		public void ignorableWhitespace( char[] ch, int start, int length) throws SAXException {
			emptyTag = false;
			super.characters( ch, start, length );
		}

		@Override
		public void processingInstruction( String target, String data ) throws SAXException {
			super.processingInstruction( target, data );
		}

		@Override
		public void startCDATA() {
			emptyTag = false;
			super.startCDATA();
		}

		@Override
		public void endElement( String namespaceURI, String localName, String qName ) throws SAXException {
			Element closedElement = getCurrentElement();
			boolean twoPartTag = false;

			Locator loc = getDocumentLocator();
			if ( loc != null ) {
				int endTagLine = loc.getLineNumber();
				int endTagColumn = loc.getColumnNumber();

				if ( startTagLine != -1 && endTagLine != -1 && startTagLine != endTagLine) {
					twoPartTag = true;
				}
				else if ( startTagColumn != -1 && endTagColumn != -1 && startTagColumn != endTagColumn ) {
					twoPartTag = true;
				}
			}

			super.endElement( namespaceURI, localName, qName );

			if ( emptyTag && twoPartTag && closedElement.getContent().isEmpty() ) {
				// This is a separate closing tag after an empty value.
				// Add a blank text node.

				final Text text = getFactory().text( "" );
				getFactory().addContent( closedElement, text );
			}
		}
	}
}
