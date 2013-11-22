package net.vhati.modmanager.ui.tree;

import java.awt.Cursor;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DragSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;


/**
 * A handler to enable drag-and-drop within a JTree.
 *
 * When dropped, copies of highlighted nodes will be made via clone() and
 * inserted at the drop location, then the originals will be removed.
 *
 * Dragging onto a space between nodes will insert at that location.
 * Dragging onto a node that allows children will insert into it.
 * Dragging onto a node that doesn't allow children will insert after it.
 *
 * All nodes must be instances of DefaultMutableTreeNode (or subclasses).
 * Set the Jtree's DropMode to ON_OR_INSERT.
 * The root node must be hidden, to prevent it from being dragged.
 * The tree's selection model may be set to single or multiple.
 */
public class TreeTransferHandler extends TransferHandler {

	private DataFlavor localTreePathFlavor = null;
	private JTree tree = null;


	public TreeTransferHandler( JTree tree ) {
		super();
		this.tree = tree;

		try {
			localTreePathFlavor = new DataFlavor( DataFlavor.javaJVMLocalObjectMimeType + ";class=\""+ TreePath[].class.getName() +"\"" );
		}
		catch ( ClassNotFoundException e ) {
			//log.error( e );
		}
	}

	@Override
	protected Transferable createTransferable( JComponent c ) {
		assert ( c == tree );
		TreePath[] highlightedPaths = tree.getSelectionPaths();

		Map<Integer,List<TreePath>> pathsByLengthMap = new TreeMap<Integer,List<TreePath>>();
		for ( TreePath path : highlightedPaths ) {
			if ( path.getPath().length == 1 ) continue;  // Omit root node (shouldn't drag it anyway).

			Integer pathLength = new Integer( path.getPath().length );
			if ( !pathsByLengthMap.containsKey( pathLength ) ) {
				pathsByLengthMap.put( pathLength, new ArrayList<TreePath>() );
			}
			pathsByLengthMap.get( pathLength ).add( path );
		}
		// For each length (shortest-first), iterate its paths.
		// For each of those paths, search longer lengths' lists,
		// removing any paths that are descendants of those short ancestor nodes.
		List<Integer> lengthsList = new ArrayList<Integer>( pathsByLengthMap.keySet() );
		for ( int i=0; i < lengthsList.size(); i++ ) {
			for ( TreePath ancestorPath : pathsByLengthMap.get( lengthsList.get( i ) ) ) {
				for ( int j=i+1; j < lengthsList.size(); j++ ) {

					List<TreePath> childPaths = pathsByLengthMap.get( lengthsList.get( j ) );
					for ( Iterator<TreePath> childIt = childPaths.iterator(); childIt.hasNext(); ) {
						TreePath childPath = childIt.next();
						if ( ancestorPath.isDescendant( childPath ) ) {
							childIt.remove();
						}
					}

				}
			}
		}
		List<TreePath> uniquePathList = new ArrayList<TreePath>();
		for ( List<TreePath> paths : pathsByLengthMap.values() ) {
			uniquePathList.addAll( paths );
		}
		TreePath[] uniquePathsArray = uniquePathList.toArray( new TreePath[uniquePathList.size()] );

		return new TreePathTransferrable( uniquePathsArray );
	}

	@Override
	public boolean canImport( TransferHandler.TransferSupport ts ) {
		boolean b = ( ts.getComponent() == tree && ts.isDrop() && ts.isDataFlavorSupported(localTreePathFlavor) );
		tree.setCursor( b ? DragSource.DefaultMoveDrop : DragSource.DefaultMoveNoDrop );
		return b;
	}

	@Override
	public int getSourceActions( JComponent comp ) {
		return TransferHandler.MOVE;
	}

	@Override
	@SuppressWarnings("Unchecked")
	public boolean importData( TransferHandler.TransferSupport ts ) {
		if ( !canImport(ts) ) return false;

		JTree dstTree = (JTree)ts.getComponent();
		DefaultTreeModel dstTreeModel = (DefaultTreeModel)dstTree.getModel();
		JTree.DropLocation dl = (JTree.DropLocation)ts.getDropLocation();
		TreePath dropPath = dl.getPath();    // Dest parent node, or null.
		int dropIndex = dl.getChildIndex();  // Insertion child index in the dest parent node,
		                                     // or -1 if dropped onto a group.

		dstTree.setCursor( Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR) );
		if ( dropPath == null ) return false;
		DefaultMutableTreeNode dropParentNode = (DefaultMutableTreeNode)dropPath.getLastPathComponent();

		// When dropping onto a non-group node, insert into the position after it instead.
		if ( !dropParentNode.getAllowsChildren() ) {
			DefaultMutableTreeNode prevParentNode = dropParentNode;
			dropPath = dropPath.getParentPath();
			dropParentNode = (DefaultMutableTreeNode)dropPath.getLastPathComponent();
			dropIndex = dropParentNode.getIndex( prevParentNode ) + 1;
		}

		try {
			TreePath[] draggedPaths = (TreePath[])ts.getTransferable().getTransferData( localTreePathFlavor );

			// Bail if the dropPath was among those dragged.
			boolean badDrop = false;
			for ( TreePath path : draggedPaths ) {
				if ( path.equals( dropPath ) ) {
					badDrop = true;
					break;
				}
			}

			if ( !badDrop && dropParentNode.getAllowsChildren() ) {
				for ( TreePath path : draggedPaths ) {
					// Copy the dragged node and any children.
					DefaultMutableTreeNode srcNode = (DefaultMutableTreeNode)path.getLastPathComponent();
					DefaultMutableTreeNode newNode = (DefaultMutableTreeNode)cloneNodes( srcNode );

					if ( dropIndex != -1 ) {
						// Insert.
						dropParentNode.insert( newNode, dropIndex );
						dstTreeModel.nodesWereInserted( dropParentNode, new int[]{dropIndex} );
						dropIndex++;  // Next insertion will be after this node.
					}
					else {
						// Add to the end.
						dropParentNode.add( newNode );
						dstTreeModel.nodesWereInserted( dropParentNode, new int[]{dropParentNode.getChildCount()-1} );
						if ( !dstTree.isExpanded( dropPath ) ) dstTree.expandPath( dropPath );
					}
				}
				return true;
			}
		}
		catch ( Exception e ) {
			// UnsupportedFlavorException: if Transferable.getTransferData() fails.
			// IOException: if Transferable.getTransferData() fails.
			// IllegalStateException: if insert/add fails because dropPath's node doesn't allow children.
			//log.error( e );
		}
		return false;
	}

	@Override
	protected void exportDone( JComponent source, Transferable data, int action ) {
		if ( action == TransferHandler.MOVE || action == TransferHandler.NONE ) {
			tree.setCursor( Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR) );
		}

		JTree srcTree = (JTree)source;
		DefaultTreeModel srcTreeModel = (DefaultTreeModel)srcTree.getModel();

		if ( action == TransferHandler.MOVE ) {
			// Remove original dragged rows now that the move completed.
			// Scan the tree checking equality is fine (DefaultMutableTreeNode's equals() does ==).

			try {
				TreePath[] draggedPaths = (TreePath[])data.getTransferData( localTreePathFlavor );
				for ( TreePath path : draggedPaths ) {
					DefaultMutableTreeNode doomedNode = (DefaultMutableTreeNode)path.getLastPathComponent();
					TreeNode parentNode = doomedNode.getParent();
					int doomedIndex = parentNode.getIndex( doomedNode );
					doomedNode.removeFromParent();
					srcTreeModel.nodesWereRemoved( parentNode, new int[]{doomedIndex}, new Object[]{doomedNode} );
				}
			}
			catch ( Exception e ) {
				//log.error( e );
			}
		}
	}


	/**
	 * Recursively clones a node and its descendants.
	 *
	 * The clone() methods will reuse userObjects, but the nodes themselves will be new.
	 */
	@SuppressWarnings("Unchecked")
	private DefaultMutableTreeNode cloneNodes( DefaultMutableTreeNode srcNode ) {
		DefaultMutableTreeNode resultNode = (DefaultMutableTreeNode)srcNode.clone();

		Enumeration enumer = srcNode.children();
		while ( enumer.hasMoreElements() ) {
			DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)enumer.nextElement();
			resultNode.add( cloneNodes( (DefaultMutableTreeNode)childNode ) );
		}

		return resultNode;
	}


	/**
	 * Drag and drop TreePath data, constructed with a raw object
	 * from a drag source, to be transformed into a flavor
	 * suitable for the drop target.
	 */
	private class TreePathTransferrable implements Transferable {
		private TreePath[] data;

		public TreePathTransferrable( TreePath[] data ) {
			this.data = data;
		}

		@Override
		public Object getTransferData( DataFlavor flavor ) {
			if ( flavor.equals( localTreePathFlavor ) ) {
				return data;
			}
			return null;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return new DataFlavor[] {localTreePathFlavor};
		}

		@Override
		public boolean isDataFlavorSupported( DataFlavor flavor ) {
			return flavor.equals( localTreePathFlavor );
		}
	}
}
