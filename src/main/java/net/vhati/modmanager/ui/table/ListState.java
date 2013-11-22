package net.vhati.modmanager.ui.table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * An implementation-agnostic model to pass between the GUI thread and the
 * (de)serializer.
 */
public class ListState<T> {

	protected List<T> items = new ArrayList<T>();


	public ListState() {
	}


	public void addItem( T item ) {
		items.add( item );
	}

	/**
	 * Returns a new list containing items in this state.
	 */
	public List<T> getItems() {
		return new ArrayList<T>( items );
	}

	public void removeItem( T item ) {
		items.remove( item );
	}

	public boolean containsItem( T item ) {
		return items.contains( item );
	}
}
