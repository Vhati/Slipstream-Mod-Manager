package net.vhati.modmanager.ui.tree;

import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import net.vhati.modmanager.ui.tree.TristateButtonModel.TristateState;

/**
 * A cell renderer that augments an existing renderer with a checkbox.
 */
public class ChecklistTreeCellRenderer extends JPanel implements TreeCellRenderer {

	protected ChecklistTreeSelectionModel selectionModel;
	protected ChecklistTreePathFilter checklistFilter;
	protected TreeCellRenderer delegate;
	protected TristateCheckBox checkbox = new TristateCheckBox();
	protected int checkMaxX = 0;

	/**
	 * Constructor.
	 *
	 * @param delegate        a traditional TreeCellRenderer
	 * @param selectionModel  a model to query for checkbox states
	 * @param checklistFilter a TreePath filter, or null to always show a checkbox
	 */
	public ChecklistTreeCellRenderer(TreeCellRenderer delegate, ChecklistTreeSelectionModel selectionModel,
			ChecklistTreePathFilter checklistFilter) {
		super();
		this.delegate = delegate;
		this.selectionModel = selectionModel;
		this.checklistFilter = checklistFilter;

		this.setLayout(new BorderLayout());
		this.setOpaque(false);
		checkbox.setOpaque(false);

		checkMaxX = checkbox.getPreferredSize().width;
	}

	@Override
	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf,
			int row, boolean hasFocus) {
		this.removeAll();
		checkbox.setState(TristateState.DESELECTED);

		Component delegateComp = delegate.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

		TreePath path = tree.getPathForRow(row);
		if (path != null) {
			if (selectionModel.isPathSelected(path, selectionModel.isDigged())) {
				checkbox.setState(TristateState.SELECTED);
			} else {
				checkbox.setState(
						(selectionModel.isDigged() && selectionModel.isPartiallySelected(path)) ? TristateState.INDETERMINATE
								: TristateState.DESELECTED);
			}
		}
		checkbox.setVisible(path == null || checklistFilter == null || checklistFilter.isSelectable(path));
		checkbox.setEnabled(tree.isEnabled());

		this.add(checkbox, BorderLayout.WEST);
		this.add(delegateComp, BorderLayout.CENTER);
		return this;
	}

	public void setDelegate(TreeCellRenderer delegate) {
		this.delegate = delegate;
	}

	public TreeCellRenderer getDelegate() {
		return delegate;
	}

	/**
	 * Returns the checkbox's right edge (in the renderer component's coordinate
	 * space).
	 *
	 * Values less than that can be interpreted as within the checkbox's bounds. X=0
	 * is the renderer component's left edge.
	 */
	public int getCheckboxMaxX() {
		return checkMaxX;
	}
}
