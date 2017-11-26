package net.vhati.modmanager.ui.table;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DropMode;
import javax.swing.ListSelectionModel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import net.vhati.modmanager.core.ModFileInfo;
import net.vhati.modmanager.ui.table.ChecklistTableModel;
import net.vhati.modmanager.ui.table.TableRowTransferHandler;


public class ChecklistTablePanel<T> extends JPanel {

	protected ChecklistTableModel<T>tableModel;
	protected JTable table;


	public ChecklistTablePanel() {
		super( new BorderLayout() );

		tableModel = new ChecklistTableModel<T>();

		table = new JTable( tableModel );
		table.setFillsViewportHeight( true );
		table.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		table.setTableHeader( null );
		table.getColumnModel().getColumn( 0 ).setMinWidth( 30 );
		table.getColumnModel().getColumn( 0 ).setMaxWidth( 30 );
		table.getColumnModel().getColumn( 0 ).setPreferredWidth( 30 );

		JScrollPane scrollPane = new JScrollPane( null, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER );
		scrollPane.setViewportView( table );
		//scrollPane.setColumnHeaderView( null );  // Counterpart to setTableHeader().
		scrollPane.setPreferredSize( new Dimension( Integer.MIN_VALUE, Integer.MIN_VALUE ) );
		this.add( scrollPane, BorderLayout.CENTER );


		// Double-click toggles checkboxes.
		table.addMouseListener(new MouseAdapter() {
			int prevRow = -1;
			int streak = 0;

			@Override
			public void mouseClicked( MouseEvent e ) {
				if ( e.getSource() != table ) return;
				int thisRow = table.rowAtPoint( e.getPoint() );

				// Reset on first click and when no longer on that row.
				if ( e.getClickCount() == 1 ) prevRow = -1;

				if ( thisRow != prevRow || thisRow == -1 ) {
					streak = 1;
					prevRow = thisRow;
					return;
				}
				else {
					streak++;
				}
				if ( streak % 2 != 0 ) return;  // Respond only to click pairs.

				// Don't further toggle a multi-clicked checkbox.
				int viewCol = table.columnAtPoint( e.getPoint() );
				int modelCol = table.getColumnModel().getColumn( viewCol ).getModelIndex();
				if ( modelCol == 0 ) return;

				int selRow = table.getSelectedRow();
				if ( selRow != -1 ) {
					boolean selected = tableModel.isSelected( selRow );
					tableModel.setSelected( selRow, !selected );
				}
			}
		});

		table.setTransferHandler( new TableRowTransferHandler( table ) );
		table.setDropMode( DropMode.INSERT );  // Drop between rows, not on them.
		table.setDragEnabled( true );
	}


	public void clear() {
		tableModel.removeAllItems();
	}


	public List<T> getAllItems() {
		List<T> results = new ArrayList<T>();

		for ( int i=0; i < tableModel.getRowCount(); i++ ) {
			results.add( tableModel.getItem( i ) );
		}

		return results;
	}

	public List<T> getSelectedItems() {
		List<T> results = new ArrayList<T>();

		for ( int i=0; i < tableModel.getRowCount(); i++ ) {
			if ( tableModel.isSelected( i ) ) {
				results.add( tableModel.getItem( i ) );
			}
		}

		return results;
	}


	public void toggleAllItemSelection() {
		int selectedCount = 0;
		for ( int i = tableModel.getRowCount()-1; i >= 0; i-- ) {
			if ( tableModel.isSelected( i ) ) selectedCount++;
		}
		boolean b = ( selectedCount != tableModel.getRowCount() );

		for ( int i = tableModel.getRowCount()-1; i >= 0; i-- ) {
			tableModel.setSelected( i, b );
		}
	}


	public ChecklistTableModel<T> getTableModel() {
		return tableModel;
	}

	public JTable getTable() {
		return table;
	}
}
