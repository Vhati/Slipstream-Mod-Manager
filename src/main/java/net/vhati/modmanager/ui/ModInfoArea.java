package net.vhati.modmanager.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.HashMap;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.event.MouseInputAdapter;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ModInfoArea extends JScrollPane {

	private static final Logger log = LogManager.getLogger(ModInfoArea.class);

	private static final String HYPERLINK_TARGET = "hyperlink-target";

	private JTextPane textPane;
	private StyledDocument doc;
	private HashMap<String,SimpleAttributeSet> attrMap = new HashMap<String,SimpleAttributeSet>();


	public ModInfoArea() {
		super();

		textPane = new JTextPane();
		textPane.setEditable( false );

		doc = textPane.getStyledDocument();

		SimpleAttributeSet tmpAttr = new SimpleAttributeSet();
		StyleConstants.setFontFamily( tmpAttr, "Monospaced" );
		StyleConstants.setFontSize( tmpAttr, 12 );
		attrMap.put( "regular", tmpAttr );

		tmpAttr = new SimpleAttributeSet();
		StyleConstants.setFontFamily( tmpAttr, "SansSerif" );
		StyleConstants.setFontSize( tmpAttr, 24 );
		StyleConstants.setBold( tmpAttr, true );
		attrMap.put( "title", tmpAttr );

		tmpAttr = new SimpleAttributeSet();
		StyleConstants.setFontFamily( tmpAttr, "Monospaced" );
		StyleConstants.setFontSize( tmpAttr, 12 );
		StyleConstants.setForeground( tmpAttr, Color.BLUE );
		StyleConstants.setUnderline( tmpAttr, true );
		attrMap.put( "hyperlink", tmpAttr );

		MouseInputAdapter hyperlinkListener = new MouseInputAdapter() {
			@Override
			public void mouseClicked( MouseEvent e ) {
				AttributeSet tmpAttr = doc.getCharacterElement( textPane.viewToModel(e.getPoint()) ).getAttributes();
				Object targetObj = tmpAttr.getAttribute( HYPERLINK_TARGET );
				if ( targetObj != null ) {
					Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
					if ( desktop != null && desktop.isSupported(Desktop.Action.BROWSE) ) {
						try {
							desktop.browse( new URI(targetObj.toString()) );
						}
						catch ( Exception f ) {
							log.error( "Error browsing clicked url: "+ targetObj.toString(), f );
						}
					}
				}
			}

			@Override
			public void mouseMoved( MouseEvent e ) {
				AttributeSet tmpAttr = doc.getCharacterElement( textPane.viewToModel(e.getPoint()) ).getAttributes();
				if ( tmpAttr.getAttribute( HYPERLINK_TARGET ) != null ) {
					textPane.setCursor( new Cursor(Cursor.HAND_CURSOR) );
				} else {
					textPane.setCursor( new Cursor(Cursor.DEFAULT_CURSOR) );
				}
			}
		};
		textPane.addMouseListener( hyperlinkListener );
		textPane.addMouseMotionListener( hyperlinkListener );

		textPane.addMouseListener( new ClipboardMenuMouseListener() );

		this.setViewportView( textPane );
	}


	public void setDescription( String title, String body ) {
		setDescription( title, null, null, null, body );
	}

	public void setDescription( String title, String author, String version, String url, String body ) {
		try {
			doc.remove( 0, doc.getLength() );
			doc.insertString( doc.getLength(), title +"\n", attrMap.get("title") );

			boolean first = true;
			if ( author != null ) {
				doc.insertString( doc.getLength(), String.format("%sby %s", (first ? "" : " "), author), attrMap.get("regular") );
				first = false;
			}
			if ( version != null ) {
				doc.insertString( doc.getLength(), String.format("%s(version %s)", (first ? "" : " "), version), attrMap.get("regular") );
				first = false;
			}
			if ( !first ) {
				doc.insertString( doc.getLength(), "\n", attrMap.get("regular") );
			}

			if ( url != null ) {
				SimpleAttributeSet tmpAttr;
				doc.insertString( doc.getLength(), "Website: ", attrMap.get("regular") );

				boolean browseWorks = false;
				Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
				if ( desktop != null && desktop.isSupported(Desktop.Action.BROWSE) ) {
					browseWorks = true;
				}

				if ( browseWorks && url.matches("^(?:https?|ftp)://.*") ) {
					tmpAttr = new SimpleAttributeSet( attrMap.get("hyperlink") );
					tmpAttr.addAttribute( HYPERLINK_TARGET, url );
					doc.insertString( doc.getLength(), "Link", tmpAttr );
				} else {
					tmpAttr = new SimpleAttributeSet( attrMap.get("regular") );
					doc.insertString( doc.getLength(), url, tmpAttr );
				}

				doc.insertString( doc.getLength(), "\n", attrMap.get("regular") );
			}

			doc.insertString( doc.getLength(), "\n", attrMap.get("regular") );

			if ( body != null ) {
				doc.insertString( doc.getLength(), body, attrMap.get("regular") );
			}
		}
		catch ( BadLocationException e) {
			log.error( e );
		}

		textPane.setCaretPosition(0);
	}
}
