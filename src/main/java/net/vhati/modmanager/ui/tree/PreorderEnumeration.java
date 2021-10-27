/**
 * Based on PreorderEnumeration (rev 120, 2007-07-20)
 * By Santhosh Kumar T
 * https://java.net/projects/myswing
 *
 * https://java.net/projects/myswing/sources/svn/content/trunk/src/skt/swing/tree/PreorderEnumeration.java?rev=120
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

import java.util.Collections;
import java.util.Enumeration;
import java.util.Stack;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

public class PreorderEnumeration implements Enumeration<TreePath> {

	private TreeModel model;
	protected Stack<Enumeration<TreePath>> stack = new Stack<Enumeration<TreePath>>();

	public PreorderEnumeration(TreePath path, TreeModel model) {
		this(Collections.enumeration(Collections.singletonList(path)), model);
	}

	public PreorderEnumeration(Enumeration<TreePath> enumer, TreeModel model) {
		this.model = model;
		stack.push(enumer);
	}

	@Override
	public boolean hasMoreElements() {
		return (!stack.empty() && stack.peek().hasMoreElements());
	}

	@Override
	public TreePath nextElement() {
		Enumeration<TreePath> enumer = stack.peek();
		TreePath path = enumer.nextElement();

		if (!enumer.hasMoreElements())
			stack.pop();

		if (model.getChildCount(path.getLastPathComponent()) > 0) {
			stack.push(new ChildrenEnumeration(path, model));
		}

		return path;
	}
}
