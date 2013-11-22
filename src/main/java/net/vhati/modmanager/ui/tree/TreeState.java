package net.vhati.modmanager.ui.tree;

import java.util.ArrayList;
import java.util.List;


/**
 * An implementation-agnostic model to pass between the GUI thread and the
 * (de)serializer.
 */
public class TreeState {

	protected TreeNodeState rootNodeState;


	public TreeState() {
	}


	public void setRootNodeState( TreeNodeState rootNodeState ) {
		this.rootNodeState = rootNodeState;
	}

	public TreeNodeState getRootNodeState() {
		return rootNodeState;
	}



	public static class TreeNodeState {
	
		protected Object userObject = null;
		protected boolean expand = false;
		protected List<TreeNodeState> children = null;
	
	
		public TreeNodeState() {
			this( false, false );
		}
	
		public TreeNodeState( boolean allowsChildren, boolean expand ) {
			if ( allowsChildren ) {
				this.expand = expand;
				children = new ArrayList<TreeNodeState>();
			}
		}
	
	
		public boolean getAllowsChildren() {
			return ( children != null );
		}
	
		public List<TreeNodeState> getChildren() {
			return children;
		}
	
	
		public void setUserObject( Object userObject ) {
			this.userObject = userObject;
		}
	
		public Object getUserObject() {
			return userObject;
		}
	}
}
