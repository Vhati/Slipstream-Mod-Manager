package net.vhati.modmanager.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;


/**
 * A Document thats restricts characters based on a regex.
 *
 * @see javax.swing.JTextField.setDocument(javax.swing.text.Ducument)
 */
public class RegexDocument extends PlainDocument {

	private Pattern pattern = null;


	public RegexDocument( Pattern p ) {
		pattern = p;
	}

	public RegexDocument( String regex ) {
		try {
			if ( regex != null && regex.length() > 0 ) {
				pattern = Pattern.compile( regex );
			}
		}
		catch ( PatternSyntaxException e ) {}
	}

	public RegexDocument() {
	}


	@Override
	public void insertString( int offs, String str, AttributeSet a ) throws BadLocationException {
		if ( str == null ) return;

		boolean proceed = true;

		if ( pattern != null ) {
			String tmp = super.getText( 0, offs ) + str + (super.getLength()>offs ? super.getText( offs, super.getLength()-offs ) : "");
			Matcher m = pattern.matcher( tmp );
			proceed = m.matches();
		}

		if ( proceed == true ) super.insertString( offs, str, a );
	}


	@Override
	public void remove( int offs, int len ) throws BadLocationException {
		boolean proceed = true;

		if ( pattern != null ) {
			try {
				String tmp = super.getText( 0, offs ) + (super.getLength()>(offs+len) ? super.getText( offs+len, super.getLength()-(offs+len) ) : "");
				Matcher m = pattern.matcher( tmp );
				proceed = m.matches();
			}
			catch ( BadLocationException e ) {}
		}

		if ( proceed == true ) super.remove( offs, len );
	}
}
