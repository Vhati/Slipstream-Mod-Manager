package net.vhati.modmanager.ui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A MouseListener to show rollover help text in a status bar.
 *
 * Construct this with the help text, and a class implementing the Statusbar
 * interface.
 *
 * Then add this mouseListener to a component.
 */
public class StatusbarMouseListener extends MouseAdapter {

	protected Statusbar bar = null;
	protected String text = null;

	public StatusbarMouseListener(Statusbar bar, String text) {
		this.bar = bar;
		this.text = text;
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		bar.setStatusText(text);
	}

	@Override
	public void mouseExited(MouseEvent e) {
		bar.setStatusText("");
	}
}
