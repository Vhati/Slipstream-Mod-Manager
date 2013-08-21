package net.vhati.modmanager.ui;


public interface Reorderable {
	/**
	 * Moves an element at fromIndex to toIndex.
	 */
	public void reorder( int fromIndex, int toIndex );
}
