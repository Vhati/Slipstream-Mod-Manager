package net.vhati.modmanager.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;


/**
 * When applied to a JTextField via setDocument(), you can only enter a limited set of characters.
 */
public class RegexDocument extends PlainDocument {
	private boolean dontCheck = false;
	private Pattern p = null;


	public RegexDocument( String regex ) {
		if ( regex == null || regex.length()==0 ) dontCheck = true;

		try {
			p = Pattern.compile(regex);
		} catch (PatternSyntaxException e) {dontCheck = true;}
	}

	public RegexDocument() {
		dontCheck = true;
	}


	@Override
	public void insertString( int offs, String str, AttributeSet a ) throws BadLocationException {
		if ( str == null ) return;

		boolean proceed = true;

		if ( dontCheck == false ) {
			String tmp = super.getText(0, offs) + str + (super.getLength()>offs ? super.getText(offs,super.getLength()-offs) : "");
			Matcher m = p.matcher(tmp);
			proceed = m.matches();
		}

		if ( proceed == true ) super.insertString( offs, str, a );
	}


	@Override
	public void remove( int offs, int len ) throws BadLocationException {
		boolean proceed = true;

		if ( dontCheck == false ) {
			try {
				String tmp = super.getText(0, offs) + (super.getLength()>(offs+len) ? super.getText(offs+len, super.getLength()-(offs+len)) : "");
				Matcher m = p.matcher(tmp);
				proceed = m.matches();
			} catch (BadLocationException f) {}
		}

		if ( proceed == true ) super.remove( offs, len );
	}
}
