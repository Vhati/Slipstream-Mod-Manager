package net.vhati.modmanager.ui.tree;

import java.awt.Component;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;


/**
 * A renderer that sets icons based on whether children are allowed.
 *
 * A group with no children will still have a group icon.
 */
public class GroupTreeCellRenderer extends DefaultTreeCellRenderer {

	@Override
	public Component getTreeCellRendererComponent( JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus ) {
		super.getTreeCellRendererComponent( tree, value, sel, expanded, leaf, row, hasFocus );

		if ( value instanceof DefaultMutableTreeNode ) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
			if ( node.getAllowsChildren() ) {
				if ( expanded ) {
					this.setIcon( this.getDefaultOpenIcon() );
					this.setDisabledIcon( this.getDefaultOpenIcon() );
				} else {
					this.setIcon( this.getDefaultClosedIcon() );
					this.setDisabledIcon( this.getDefaultClosedIcon() );
				}
			} else {
				this.setIcon( this.getDefaultLeafIcon() );
				this.setDisabledIcon( this.getDefaultLeafIcon() );
			}
		}

		return this;
	}
}
