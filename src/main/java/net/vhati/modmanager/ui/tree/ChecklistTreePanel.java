package net.vhati.modmanager.ui.tree;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.swing.DropMode;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.vhati.modmanager.ui.tree.ChecklistTreeManager;
import net.vhati.modmanager.ui.tree.ChecklistTreeSelectionModel;
import net.vhati.modmanager.ui.tree.GroupTreeCellRenderer;
import net.vhati.modmanager.ui.tree.TreeTransferHandler;


public class ChecklistTreePanel extends JPanel {

	private DefaultTreeModel treeModel = null;
	private JTree tree = null;
	private ChecklistTreeManager checklistManager = null;


	public ChecklistTreePanel() {
		super( new BorderLayout() );

		DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode( "Root", true );
		treeModel = new DefaultTreeModel( rootNode );
		tree = new JTree( treeModel );
		tree.setCellRenderer( new GroupTreeCellRenderer() );
		tree.setRootVisible( false );
		tree.getSelectionModel().setSelectionMode( TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION );
		checklistManager = new ChecklistTreeManager( tree, true, null );

		JScrollPane scrollPane = new JScrollPane( tree, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED );
		this.add( scrollPane, BorderLayout.CENTER );

		tree.setTransferHandler( new TreeTransferHandler( tree ) );
		tree.setDropMode( DropMode.ON_OR_INSERT );  // Drop between rows, or onto groups.
		tree.setDragEnabled( true );
	}


	/**
	 * Returns all userObjects of nodes with ticked checkboxes (except root itself).
	 */
	public List<Object> getSelectedUserObjects() {
		ChecklistTreeSelectionModel checklistSelectionModel = checklistManager.getSelectionModel();
		List<Object> results = new ArrayList<Object>();

		for ( Enumeration enumer = checklistSelectionModel.getAllSelectedPaths(); enumer.hasMoreElements(); ) {
			DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)enumer.nextElement();
			if ( !childNode.isRoot() && childNode.getUserObject() != null ) {
				results.add( childNode.getUserObject() );
			}
		}

		return results;
	}

	/**
	 * Returns all userObjects of all nodes (except root itself).
	 */
	public List<Object> getAllUserObjects() {
		List<Object> results = new ArrayList<Object>();

		DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)treeModel.getRoot();
		getAllUserObjects( rootNode, results );

		return results;
	}

	private void getAllUserObjects( DefaultMutableTreeNode currentNode, List<Object> results ) {
		if ( !currentNode.isRoot() && currentNode.getUserObject() != null ) {
			results.add( currentNode.getUserObject() );
		}

		for ( Enumeration enumer = currentNode.children(); enumer.hasMoreElements(); ) {
			DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)enumer.nextElement();
			getAllUserObjects( currentNode, results );
		}
	}


	public void clear() {
		DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)treeModel.getRoot();
		rootNode.removeAllChildren();
		treeModel.reload();
	}


	/**
	 * Adds a group to consolidate mods.
	 *
	 * TODO: Trigger a rename.
	 */
	public void addGroup() {
		DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)treeModel.getRoot();
		GroupAwareTreeNode groupNode = new GroupAwareTreeNode( "New Group", true );
		rootNode.add( groupNode );
		treeModel.nodesWereInserted( rootNode, new int[]{rootNode.getIndex( groupNode )} );
	}

	/**
	 * Disband selected groups.
	 *
	 * TODO
	 */
	public void removeSelectedGroups() {
	}

	/**
	 * Rename last selected group.
	 *
	 * TODO
	 */
	public void  renameSelectedGroup() {
	}


	/**
	 * Cycles through ticking all checkboxes and clearing them.
	 */
	public void toggleAllNodeSelection() {
	}

	/**
	 * Cycles through expanding all nodes and collapsing them.
	 */
	public void toggleAllNodeExpansion() {
		boolean canExpand = false;
		boolean canCollapse = false;

		for ( int i = tree.getRowCount()-1; i >= 0; i-- ) {
			if ( tree.isCollapsed( i ) ) {
				canExpand = true;
			}
			else if ( tree.isExpanded( i ) ) {
				canCollapse = true;
			}
		}

		if ( canExpand ) {
			expandAllNodes( tree.getRowCount() );
		}
		else if ( canCollapse ) {
			collapseAllNodes( new TreePath( treeModel.getRoot() ) );
		}
	}

	/**
	 * Expands all nodes by repeatedly expanding until the row count stops
	 * growing.
	 */
	public void expandAllNodes( int prevRowCount ) {
		for ( int i=0; i < prevRowCount; i++ ) {
			tree.expandRow( i );
		}
		if ( tree.getRowCount() != prevRowCount ) {
			expandAllNodes( tree.getRowCount() );
		}
	}

	/**
	 * Collapses all nodes by walking the TreeModel.
	 */
	public void collapseAllNodes( TreePath currentPath ) {
		Object currentNode = currentPath.getLastPathComponent();
		for ( int i = treeModel.getChildCount( currentNode )-1; i >= 0; i-- ) {
			Object childNode = treeModel.getChild( currentNode, i );
			TreePath childPath = currentPath.pathByAddingChild( childNode );
			collapseAllNodes( childPath );
		}
		if ( currentNode != treeModel.getRoot() ) tree.collapsePath( currentPath );
	}


	public JTree getTree() {
		return tree;
	}

	public DefaultTreeModel getTreeModel() {
		return treeModel;
	}

	public ChecklistTreeManager getChecklistManager() {
		return checklistManager;
	}
}
