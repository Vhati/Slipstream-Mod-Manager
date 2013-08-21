package net.vhati.modmanager.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.text.JTextComponent;


/**
 * A Cut/Copy/Paste/SelectAll context menu for JTextComponents.
 */
public class ClipboardMenuMouseListener extends MouseAdapter {

	private JPopupMenu popup = new JPopupMenu();

	private Action cutAction;
	private Action copyAction;
	private Action pasteAction;
	private Action selectAllAction;

	private JTextComponent textComponent = null;


	public ClipboardMenuMouseListener() {
		cutAction = new AbstractAction( "Cut" ) {
			@Override
			public void actionPerformed( ActionEvent ae ) {
				textComponent.cut();
			}
		};
		copyAction = new AbstractAction( "Copy" ) {
			@Override
			public void actionPerformed( ActionEvent ae ) {
				textComponent.copy();
			}
		};
		pasteAction = new AbstractAction( "Paste" ) {
			@Override
			public void actionPerformed( ActionEvent ae ) {
				textComponent.paste();
			}
		};
		selectAllAction = new AbstractAction( "Select All" ) {
			@Override
			public void actionPerformed( ActionEvent ae ) {
				textComponent.selectAll();
			}
		};

		popup.add( cutAction );
		popup.add( copyAction );
		popup.add( pasteAction );
		popup.addSeparator();
		popup.add( selectAllAction );
	}


	@Override
	public void mousePressed( MouseEvent e ) {
		if ( e.isPopupTrigger() ) showMenu( e );
	}

	@Override
	public void mouseReleased( MouseEvent e ) {
		if ( e.isPopupTrigger() ) showMenu( e );
	}

	public void showMenu( MouseEvent e ) {
		if ( e.getSource() instanceof JTextComponent == false ) return;

		textComponent = (JTextComponent)e.getSource();
		textComponent.requestFocus();

		boolean enabled = textComponent.isEnabled();
		boolean editable = textComponent.isEditable();
		boolean nonempty = !(textComponent.getText() == null || textComponent.getText().equals(""));
		boolean marked = textComponent.getSelectedText() != null;

		boolean pasteAvailable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null).isDataFlavorSupported(DataFlavor.stringFlavor);

		cutAction.setEnabled( enabled && editable && marked );
		copyAction.setEnabled( enabled && marked );
		pasteAction.setEnabled( enabled && editable && pasteAvailable );
		selectAllAction.setEnabled( enabled && nonempty );

		int nx = e.getX();
		if ( nx > 500 ) nx = nx - popup.getSize().width;

		popup.show( e.getComponent(), nx, e.getY() - popup.getSize().height );
	}
}