/*
 * Based on CheckTreeSelectionModel (rev 120, 2007-07-20)
 * By Santhosh Kumar T
 * https://java.net/projects/myswing
 *
 * https://java.net/projects/myswing/sources/svn/content/trunk/src/skt/swing/tree/check/CheckTreeSelectionModel.java?rev=120
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Stack;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public class ChecklistTreeSelectionModel extends DefaultTreeSelectionModel {

	private TreeModel model;
	private boolean dig = true;

	public ChecklistTreeSelectionModel(TreeModel model, boolean dig) {
		this.model = model;
		this.dig = dig;
		this.setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
	}

	public boolean isDigged() {
		return dig;
	}

	/**
	 * Returns true if path1 is a descendant of path2.
	 */
	private boolean isDescendant(TreePath path1, TreePath path2) {
		Object obj1[] = path1.getPath();
		Object obj2[] = path2.getPath();
		for (int i = 0; i < obj2.length; i++) {
			if (obj1[i] != obj2[i])
				return false;
		}
		return true;
	}

	/**
	 * Returns true a selected node exists in the subtree of a given unselected
	 * path. Returns false if the given path is itself selected.
	 */
	public boolean isPartiallySelected(TreePath path) {
		if (isPathSelected(path, true))
			return false;

		TreePath[] selectionPaths = getSelectionPaths();
		if (selectionPaths == null)
			return false;

		for (int j = 0; j < selectionPaths.length; j++) {
			if (isDescendant(selectionPaths[j], path)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if a given path is selected.
	 *
	 * If dig is true, then the path is assumed to be selected, if one of its
	 * ancestors is selected.
	 */
	public boolean isPathSelected(TreePath path, boolean dig) {
		if (!dig)
			return super.isPathSelected(path);

		while (path != null && !super.isPathSelected(path)) {
			path = path.getParentPath();
		}
		return (path != null);
	}

	@Override
	public void setSelectionPaths(TreePath[] paths) {
		if (dig) {
			throw new UnsupportedOperationException();
		} else {
			super.setSelectionPaths(paths);
		}
	}

	@Override
	public void addSelectionPaths(TreePath[] paths) {
		if (!dig) {
			super.addSelectionPaths(paths);
			return;
		}

		// Unselect all descendants of paths[].
		for (int i = 0; i < paths.length; i++) {
			TreePath path = paths[i];
			TreePath[] selectionPaths = getSelectionPaths();
			if (selectionPaths == null)
				break;

			List<TreePath> toBeRemoved = new ArrayList<TreePath>();
			for (int j = 0; j < selectionPaths.length; j++) {
				if (isDescendant(selectionPaths[j], path)) {
					toBeRemoved.add(selectionPaths[j]);
				}
			}
			super.removeSelectionPaths((TreePath[]) toBeRemoved.toArray(new TreePath[0]));
		}

		// If all siblings are selected then unselect them and select parent recursively
		// otherwize just select that path.
		for (int i = 0; i < paths.length; i++) {
			TreePath path = paths[i];
			TreePath temp = null;
			while (areSiblingsSelected(path)) {
				temp = path;
				if (path.getParentPath() == null)
					break;
				path = path.getParentPath();
			}
			if (temp != null) {
				if (temp.getParentPath() != null) {
					addSelectionPath(temp.getParentPath());
				} else {
					if (!isSelectionEmpty()) {
						removeSelectionPaths(getSelectionPaths());
					}
					super.addSelectionPaths(new TreePath[] { temp });
				}
			} else {
				super.addSelectionPaths(new TreePath[] { path });
			}
		}
	}

	@Override
	public void removeSelectionPaths(TreePath[] paths) {
		if (!dig) {
			super.removeSelectionPaths(paths);
			return;
		}

		for (int i = 0; i < paths.length; i++) {
			TreePath path = paths[i];
			if (path.getPathCount() == 1) {
				super.removeSelectionPaths(new TreePath[] { path });
			} else {
				toggleRemoveSelection(path);
			}
		}
	}

	/**
	 * Returns true if all siblings of given path are selected.
	 */
	private boolean areSiblingsSelected(TreePath path) {
		TreePath parent = path.getParentPath();
		if (parent == null)
			return true;

		Object node = path.getLastPathComponent();
		Object parentNode = parent.getLastPathComponent();

		int childCount = model.getChildCount(parentNode);
		for (int i = 0; i < childCount; i++) {
			Object childNode = model.getChild(parentNode, i);
			if (childNode == node)
				continue;

			if (!isPathSelected(parent.pathByAddingChild(childNode))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Unselects a given path, toggling ancestors if they were entirely selected.
	 *
	 * If any ancestor node of the given path is selected, it will be unselected,
	 * and all its descendants - except any within the given path - will be
	 * selected. The ancestor will have gone from fully selected to partially
	 * selected.
	 *
	 * Otherwise, the given path will be unselected, and nothing else will change.
	 */
	private void toggleRemoveSelection(TreePath path) {
		Stack<TreePath> stack = new Stack<TreePath>();
		TreePath parent = path.getParentPath();

		while (parent != null && !isPathSelected(parent)) {
			stack.push(parent);
			parent = parent.getParentPath();
		}

		if (parent != null) {
			stack.push(parent);
		} else {
			super.removeSelectionPaths(new TreePath[] { path });
			return;
		}

		while (!stack.isEmpty()) {
			TreePath temp = stack.pop();
			TreePath peekPath = (stack.isEmpty() ? path : stack.peek());
			Object node = temp.getLastPathComponent();
			Object peekNode = peekPath.getLastPathComponent();

			int childCount = model.getChildCount(node);
			for (int i = 0; i < childCount; i++) {
				Object childNode = model.getChild(node, i);
				if (childNode != peekNode) {
					super.addSelectionPaths(new TreePath[] { temp.pathByAddingChild(childNode) });
				}
			}
		}
		super.removeSelectionPaths(new TreePath[] { parent });
	}

	public Enumeration<TreePath> getAllSelectedPaths() {
		Enumeration<TreePath> result = null;

		TreePath[] treePaths = getSelectionPaths();
		if (treePaths == null) {
			List<TreePath> pathsList = Collections.emptyList();
			result = Collections.enumeration(pathsList);
		} else {
			List<TreePath> pathsList = Arrays.asList(treePaths);
			result = Collections.enumeration(pathsList);
			if (dig) {
				result = new PreorderEnumeration(result, model);
			}
		}

		return result;
	}
}
