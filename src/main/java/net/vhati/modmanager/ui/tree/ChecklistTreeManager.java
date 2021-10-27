/**
 * Based on CheckTreeManager (rev 120, 2007-07-20)
 * By Santhosh Kumar T
 * https://java.net/projects/myswing
 *
 * https://java.net/projects/myswing/sources/svn/content/trunk/src/skt/swing/tree/check/CheckTreeManager.java?rev=120
 */

/**
 * MySwing: Advanced Swing Utilites
 * Copyright (C) 2005  Santhosh Kumar T
 * <p/>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */

package net.vhati.modmanager.ui.tree;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

public class ChecklistTreeManager extends MouseAdapter implements TreeSelectionListener {

	private ChecklistTreeSelectionModel selectionModel;
	private ChecklistTreePathFilter checklistFilter;
	protected JTree tree = new JTree();
	protected int checkMaxX = 0;

	/**
	 * Constructor.
	 *
	 * Modifies a given tree to add checkboxes. - The tree's existing cell renderer
	 * will be wrapped with a ChecklistTreeCellRenderer. - A MouseListener will be
	 * added to the tree to detect clicks, which will toggle checkboxes.
	 *
	 * A secondary ChecklistTreeSelectionModel will track checkboxes' states
	 * (independent of row highlighting).
	 *
	 * @param tree a tree to modify
	 * @param dig  true show that a node is partially selected by scanning its
	 *             descendents, false otherwise
	 * @checklistFilter a filter to decide which TreePaths need checkboxes, or null
	 */
	public ChecklistTreeManager(JTree tree, boolean dig, ChecklistTreePathFilter checklistFilter) {
		this.tree = tree;
		this.checklistFilter = checklistFilter;

		// Note: If largemodel is not set then treenodes are getting truncated.
		// Need to debug further to find the problem.
		if (checklistFilter != null)
			tree.setLargeModel(true);

		selectionModel = new ChecklistTreeSelectionModel(tree.getModel(), dig);

		ChecklistTreeCellRenderer checklistRenderer = new ChecklistTreeCellRenderer(tree.getCellRenderer(), selectionModel,
				checklistFilter);
		setCheckboxMaxX(checklistRenderer.getCheckboxMaxX());
		tree.setCellRenderer(checklistRenderer);

		selectionModel.addTreeSelectionListener(this);
		tree.addMouseListener(this);
	}

	/**
	 * Sets the checkbox's right edge (in the TreeCellRenderer component's
	 * coordinate space).
	 *
	 * Values less than that will be interpreted as within the checkbox's bounds.
	 * X=0 is the renderer component's left edge.
	 */
	public void setCheckboxMaxX(int x) {
		checkMaxX = x;
	}

	public ChecklistTreePathFilter getChecklistFilter() {
		return checklistFilter;
	}

	public ChecklistTreeSelectionModel getSelectionModel() {
		return selectionModel;
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		TreePath path = tree.getPathForLocation(e.getX(), e.getY());
		if (path == null)
			return;

		if (e.getX() > tree.getPathBounds(path).x + checkMaxX)
			return;

		if (checklistFilter != null && !checklistFilter.isSelectable(path))
			return;

		boolean selected = selectionModel.isPathSelected(path, selectionModel.isDigged());
		selectionModel.removeTreeSelectionListener(this);

		try {
			if (selected) {
				selectionModel.removeSelectionPath(path);
			} else {
				selectionModel.addSelectionPath(path);
			}
		} finally {
			selectionModel.addTreeSelectionListener(this);
			tree.treeDidChange();
		}
	}

	@Override
	public void valueChanged(TreeSelectionEvent e) {
		tree.treeDidChange();
	}
}
