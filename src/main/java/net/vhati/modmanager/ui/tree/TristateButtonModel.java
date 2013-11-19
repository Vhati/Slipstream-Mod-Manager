/**
 * Copied from "TristateCheckBox Revisited" (2007-05-25)
 * By Dr. Heinz M. Kabutz
 * http://www.javaspecialists.co.za/archive/Issue145.html
 */

package net.vhati.modmanager.ui.tree;

import java.awt.event.ItemEvent;
import javax.swing.JToggleButton.ToggleButtonModel;


public class TristateButtonModel extends ToggleButtonModel {

  private TristateState state = TristateState.DESELECTED;


	public TristateButtonModel( TristateState state ) {
		setState( state );
	}

	public TristateButtonModel() {
		this( TristateState.DESELECTED );
	}


	public void setIndeterminate() {
		setState( TristateState.INDETERMINATE );
	}

	public boolean isIndeterminate() {
		return ( state == TristateState.INDETERMINATE );
	}


	@Override
	public void setEnabled( boolean enabled ) {
		super.setEnabled(enabled);
		// Restore state display.
		displayState();
	}

	@Override
	public void setSelected( boolean selected ) {
		setState( selected ? TristateState.SELECTED : TristateState.DESELECTED );
	}

	@Override
	public void setArmed( boolean b ) {
	}

	@Override
	public void setPressed( boolean b ) {
	}


	public void iterateState() {
		setState( state.next() );
	}

	public void setState( TristateState state ) {
		this.state = state;
		displayState();
		if ( state == TristateState.INDETERMINATE && isEnabled() ) {
			// Send ChangeEvent.
			fireStateChanged();

			// Send ItemEvent.
			int indeterminate = 3;
			fireItemStateChanged(new ItemEvent( this, ItemEvent.ITEM_STATE_CHANGED, this, indeterminate ));
		}
	}

	private void displayState() {
		super.setSelected( state != TristateState.DESELECTED );
		super.setArmed( state == TristateState.INDETERMINATE );
		super.setPressed( state == TristateState.INDETERMINATE );
	}

	public TristateState getState() {
		return state;
	}



	public static enum TristateState {
		SELECTED {
			public TristateState next() {
				return INDETERMINATE;
			}
		},
		INDETERMINATE {
			public TristateState next() {
				return DESELECTED;
			}
		},
		DESELECTED {
			public TristateState next() {
				return SELECTED;
			}
		};

		public abstract TristateState next();
	}
}
