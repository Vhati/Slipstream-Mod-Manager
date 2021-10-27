/*
 * Based on "TristateCheckBox Revisited" (2007-05-25)
 * By Dr. Heinz M. Kabutz
 * http://www.javaspecialists.co.za/archive/Issue145.html
 */

package net.vhati.modmanager.ui.tree;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ActionMapUIResource;

import net.vhati.modmanager.ui.tree.TristateButtonModel.TristateState;

public class TristateCheckBox extends JCheckBox {

	private final ChangeListener enableListener;

	public TristateCheckBox(String text, Icon icon, TristateState initial) {
		super(text, icon);

		setModel(new TristateButtonModel(initial));

		enableListener = new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				TristateCheckBox.this.setFocusable(TristateCheckBox.this.getModel().isEnabled());
			}
		};

		// Add a listener for when the mouse is pressed.
		super.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				TristateCheckBox.this.iterateState();
			}
		});

		// Reset the keyboard action map.
		ActionMap map = new ActionMapUIResource();
		map.put("pressed", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				TristateCheckBox.this.iterateState();
			}
		});
		map.put("released", null);
		SwingUtilities.replaceUIActionMap(this, map);
	}

	public TristateCheckBox(String text, TristateState initial) {
		this(text, null, initial);
	}

	public TristateCheckBox(String text) {
		this(text, null);
	}

	public TristateCheckBox() {
		this(null);
	}

	public void setIndeterminate() {
		getTristateModel().setIndeterminate();
	}

	public boolean isIndeterminate() {
		return getTristateModel().isIndeterminate();
	}

	public void setState(TristateState state) {
		getTristateModel().setState(state);
	}

	public TristateState getState() {
		return getTristateModel().getState();
	}

	@Override
	public void setModel(ButtonModel newModel) {
		super.setModel(newModel);

		// Listen for enable changes.
		if (model instanceof TristateButtonModel) {
			model.addChangeListener(enableListener);
		}
	}

	public TristateButtonModel getTristateModel() {
		return (TristateButtonModel) super.getModel();
	}

	/**
	 * No one may add mouse listeners, not even Swing!
	 */
	@Override
	public void addMouseListener(MouseListener l) {
	}

	private void iterateState() {
		// Maybe do nothing at all?
		if (!super.getModel().isEnabled())
			return;

		this.grabFocus();

		// Iterate state.
		getTristateModel().iterateState();

		// Fire ActionEvent.
		int modifiers = 0;
		AWTEvent currentEvent = EventQueue.getCurrentEvent();
		if (currentEvent instanceof InputEvent) {
			modifiers = ((InputEvent) currentEvent).getModifiers();
		} else if (currentEvent instanceof ActionEvent) {
			modifiers = ((ActionEvent) currentEvent).getModifiers();
		}
		fireActionPerformed(
				new ActionEvent(this, ActionEvent.ACTION_PERFORMED, this.getText(), System.currentTimeMillis(), modifiers));
	}
}
