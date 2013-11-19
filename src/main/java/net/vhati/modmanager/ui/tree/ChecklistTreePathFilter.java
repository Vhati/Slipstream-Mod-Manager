package net.vhati.modmanager.ui.tree;

import javax.swing.tree.TreePath;


/**
 * Decides whether a given TreePath should have a checkbox.
 */
public interface ChecklistTreePathFilter {

	public boolean isSelectable( TreePath path );

}
