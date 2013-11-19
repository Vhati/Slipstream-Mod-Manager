package net.vhati.modmanager.ui.table;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

import net.vhati.modmanager.core.ModInfo;
import net.vhati.modmanager.ui.table.Reorderable;


public class ChecklistTableModel<T> extends AbstractTableModel implements Reorderable {

	private static final int COLUMN_CHECK = 0;
	private static final int COLUMN_PAYLOAD = 1;

	private static final int DATA_CHECK = 0;
	private static final int DATA_PAYLOAD = 1;

	private String[] columnNames = new String[] {"?", "Name"};
	private Class[] columnTypes = new Class[] {Boolean.class, String.class};

	private List<List<Object>> rowsList = new ArrayList<List<Object>>();


	public void addItem( T o ) {
		insertItem( rowsList.size(), false, o );
	}

	public void insertItem( int row, boolean selected, T o ) {
		int newRowIndex = rowsList.size();

		List<Object> rowData = new ArrayList<Object>();
		rowData.add( new Boolean(selected) );
		rowData.add( o );
		rowsList.add( row, rowData );

		fireTableRowsInserted( row, row );
	}

	public void removeItem( int row ) {
		rowsList.remove( row );
		fireTableRowsDeleted( row, row );
	}

	public void removeAllItems() {
		rowsList.clear();
		fireTableDataChanged();
	}

	@SuppressWarnings("unchecked")
	public T getItem( int row ) {
		return (T)rowsList.get(row).get(DATA_PAYLOAD);
	}

	@Override
	public void reorder( int fromRow, int toRow ) {
		if ( toRow > fromRow ) toRow--;
		List<Object> rowData = rowsList.get( fromRow );
		rowsList.remove( fromRow );
		fireTableRowsDeleted( fromRow, fromRow );
		rowsList.add( toRow, rowData );
		fireTableRowsInserted( toRow, toRow );
	}

	public void setSelected( int row, boolean b ) {
		rowsList.get(row).set( DATA_CHECK, new Boolean(b) );
		fireTableRowsUpdated( row, row );
	}

	@SuppressWarnings("unchecked")
	public boolean isSelected( int row ) {
		return ((Boolean)rowsList.get(row).get(DATA_CHECK)).booleanValue();
	}

	@Override
	public int getColumnCount() {
		return columnNames.length;
	}

	@Override
	public int getRowCount() {
		return rowsList.size();
	}

	@Override
	public Object getValueAt( int row, int column ) {
		if ( column == COLUMN_CHECK ) {
			return rowsList.get(row).get(DATA_CHECK);
		}
		else if ( column == COLUMN_PAYLOAD ) {
			Object o = rowsList.get(row).get(DATA_PAYLOAD);
			return o.toString();
		}
		throw new ArrayIndexOutOfBoundsException();
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setValueAt( Object o, int row, int column ) {
		if ( column == COLUMN_CHECK ) {
			Boolean bool = (Boolean)o;
			rowsList.get(row).set( DATA_CHECK, bool );
			fireTableRowsUpdated( row, row );
		}
	}

	@Override
	public boolean isCellEditable( int row, int column ) {
		if ( column == COLUMN_CHECK ) return true;
		return false;
	}

	@Override
	public Class getColumnClass( int column ) {
		return columnTypes[column];
	}
}
