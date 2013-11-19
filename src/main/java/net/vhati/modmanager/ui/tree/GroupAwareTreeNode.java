package net.vhati.modmanager.ui.tree;

import javax.swing.tree.DefaultMutableTreeNode;


/**
 * A TreeNode that remembers whether it allows children when cloned.
 */
public class GroupAwareTreeNode extends DefaultMutableTreeNode {

	public GroupAwareTreeNode( Object userObject, boolean allowsChildren ) {
		super( userObject, allowsChildren );
	}


	@Override
	public Object clone() {
		GroupAwareTreeNode newNode = (GroupAwareTreeNode)super.clone();
		newNode.setAllowsChildren( this.getAllowsChildren() );

		return newNode;
	}
}
