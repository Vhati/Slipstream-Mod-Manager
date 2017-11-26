package net.vhati.modmanager.ui.table;

import java.awt.Cursor;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DragSource;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;

import net.vhati.modmanager.ui.table.Reorderable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * Allows drag and drop reordering of JTable rows.
 *
 * Its TableModel must implement the Reorderable interface.
 */
public class TableRowTransferHandler extends TransferHandler {

	private static final Logger log = LogManager.getLogger( TableRowTransferHandler.class );

	private DataFlavor localIntegerFlavor = null;

	private JTable table = null;


	public TableRowTransferHandler( JTable table ) {
		super();
		if ( table.getModel() instanceof Reorderable == false ) {
			throw new IllegalArgumentException( "The tableModel doesn't implement Reorderable." );
		}
		this.table = table;

		try {
			localIntegerFlavor = new DataFlavor( DataFlavor.javaJVMLocalObjectMimeType + ";class=\""+ Integer.class.getName() +"\"" );
		}
		catch ( ClassNotFoundException e ) {
			log.error( e );
		}
	}

	@Override
	protected Transferable createTransferable( JComponent c ) {
		assert ( c == table );
		int row = table.getSelectedRow();
		return new IntegerTransferrable( new Integer( row ) );
	}

	@Override
	public boolean canImport( TransferHandler.TransferSupport ts ) {
		boolean b = ( ts.getComponent() == table && ts.isDrop() && ts.isDataFlavorSupported( localIntegerFlavor ) );
		table.setCursor( b ? DragSource.DefaultMoveDrop : DragSource.DefaultMoveNoDrop );
		return b;
	}

	@Override
	public int getSourceActions( JComponent comp ) {
		return TransferHandler.MOVE;
	}

	@Override
	@SuppressWarnings("Unchecked")
	public boolean importData( TransferHandler.TransferSupport ts ) {
		if ( !canImport( ts ) ) return false;

		JTable target = (JTable)ts.getComponent();
		JTable.DropLocation dl = (JTable.DropLocation)ts.getDropLocation();
		int dropRow = dl.getRow();
		int rowCount = table.getModel().getRowCount();
		if ( dropRow < 0 || dropRow > rowCount ) dropRow = rowCount;

		target.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
		try {
			Integer draggedRow = (Integer)ts.getTransferable().getTransferData( localIntegerFlavor );
			if ( draggedRow != -1 && draggedRow != dropRow ) {
				((Reorderable)table.getModel()).reorder( draggedRow, dropRow );
				if ( dropRow > draggedRow ) dropRow--;
				target.getSelectionModel().addSelectionInterval( dropRow, dropRow );
				return true;
			}
		}
		catch ( Exception e ) {
			log.error( e );
		}
		return false;
	}

	@Override
	protected void exportDone( JComponent source, Transferable data, int action ) {
		if ( action == TransferHandler.MOVE || action == TransferHandler.NONE ) {
			table.setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
		}
	}



	/**
	 * Drag and drop Integer data, constructed with a raw object
	 * from a drag source, to be transformed into a flavor
	 * suitable for the drop target.
	 */
	private class IntegerTransferrable implements Transferable {
		private Integer data;

		public IntegerTransferrable( Integer data ) {
			this.data = data;
		}

		@Override
		public Object getTransferData( DataFlavor flavor ) {
			if ( flavor.equals( localIntegerFlavor ) ) {
				return data;
			}
			return null;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return new DataFlavor[] {localIntegerFlavor};
		}

		@Override
		public boolean isDataFlavorSupported( DataFlavor flavor ) {
			return flavor.equals( localIntegerFlavor );
		}
	}
}
