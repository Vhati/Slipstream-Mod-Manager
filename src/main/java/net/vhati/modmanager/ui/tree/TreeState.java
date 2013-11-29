package net.vhati.modmanager.ui.tree;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * An implementation-agnostic model to pass between the GUI thread and the
 * (de)serializer.
 */
public class TreeState {

	protected TreeNodeState rootNodeState = null;


	public TreeState() {
	}


	public void setRootNodeState( TreeNodeState rootNodeState ) {
		this.rootNodeState = rootNodeState;
	}

	public TreeNodeState getRootNodeState() {
		return rootNodeState;
	}


	public List<TreeNodeState> findNodeStates( TreeNodeStateFilter filter ) {
		return findNodeStates( getRootNodeState(), filter );
	}

	/**
	 * Returns a list of descendant node states which match a given filter.
	 */
	public List<TreeNodeState> findNodeStates( TreeNodeState currentNodeState, TreeNodeStateFilter filter ) {
		List<TreeNodeState> results = new ArrayList<TreeNodeState>( 1 );
		collectNodeStates( currentNodeState, filter, results );
		return results;
	}

	public boolean collectNodeStates( TreeNodeState currentNodeState, TreeNodeStateFilter filter, List<TreeNodeState> results ) {
		int maxResultCount = filter.getMaxResultCount();
		boolean found = false;

		if ( filter.accept( currentNodeState ) ) {
			results.add( currentNodeState );
			if ( maxResultCount > 0 && maxResultCount >= results.size() ) return true;
		}

		if ( currentNodeState.getAllowsChildren() ) {
			for ( Iterator<TreeNodeState> it = currentNodeState.children(); it.hasNext(); ) {
				TreeNodeState childNodeState = it.next();
				found = collectNodeStates( childNodeState, filter, results );
				if ( found && maxResultCount > 0 && maxResultCount >= results.size() ) return true;
			}
		}

		return found;
	}


	public boolean containsUserObject( Object o ) {
		UserObjectTreeNodeStateFilter filter = new UserObjectTreeNodeStateFilter( o );
		filter.setMaxResultCount( 1 );
		List<TreeNodeState> results = findNodeStates( filter );

		return ( !results.isEmpty() );
	}



	public static interface TreeNodeStateFilter {
		public int getMaxResultCount();
		public boolean accept( TreeNodeState nodeState );
	}



	public static class UserObjectTreeNodeStateFilter implements TreeNodeStateFilter {

		private Class objectClass = null;
		private Object o = null;
		private int maxResultCount = 0;


		/**
		 * Constructs a filter matching objects of a given class (or subclass).
		 */
		public UserObjectTreeNodeStateFilter( Class objectClass ) {
			this.objectClass = objectClass;
		}

		/**
		 * Constructs a filter matching objects equal to a given object.
		 */
		public UserObjectTreeNodeStateFilter( Object o ) {
			this.o = o;
		}


		public void setMaxResultCount( int n ) { maxResultCount = n; }

		@Override
		public int getMaxResultCount() { return maxResultCount; }

		@Override
		public boolean accept( TreeNodeState nodeState ) {
			Object nodeObject = nodeState.getUserObject();
			if ( objectClass != null && nodeObject != null ) {
				return ( objectClass.isAssignableFrom( nodeObject.getClass() ) );
			}
			else if ( o != null ) {
				return ( o.equals( nodeState.getUserObject() ) );
			}
			return false;
		}
	}


	public static class TreeNodeState {

		protected Object userObject = null;
		protected boolean expand = false;
		protected List<TreeNodeState> children = null;
		private TreeNodeState parentNodeState = null;


		public TreeNodeState() {
			this( false, false );
		}

		public TreeNodeState( boolean allowsChildren, boolean expand ) {
			if ( allowsChildren ) {
				this.expand = expand;
				children = new ArrayList<TreeNodeState>();
			}
		}


		/**
		 * Sets this node's parent to newParent but does not change the
		 * parent's child array.
		 */
		public void setParent( TreeNodeState nodeState ) {
			parentNodeState = nodeState;
		}

		public TreeNodeState getParent() {
			return parentNodeState;
		}


		public boolean getAllowsChildren() {
			return ( children != null );
		}

		public void addChild( TreeNodeState childNodeState ) {
			TreeNodeState oldParent = childNodeState.getParent();
			if ( oldParent != null ) oldParent.removeChild( childNodeState );

			childNodeState.setParent( this );
			children.add( childNodeState );
		}

		public void removeChild( TreeNodeState childNodeState ) {
			children.remove( childNodeState );
			childNodeState.setParent( null );
		}

		/**
		 * Returns an iterator over this node state's children.
		 */
		public Iterator<TreeNodeState> children() {
			return children.iterator();
		}


		public void setUserObject( Object userObject ) {
			this.userObject = userObject;
		}

		public Object getUserObject() {
			return userObject;
		}
	}
}
