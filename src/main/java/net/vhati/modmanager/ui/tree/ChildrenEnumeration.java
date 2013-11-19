/**
 * Based on ChildrenEnumeration (rev 120, 2007-07-20)
 * By Santhosh Kumar T
 * https://java.net/projects/myswing
 *
 * https://java.net/projects/myswing/sources/svn/content/trunk/src/skt/swing/tree/ChildrenEnumeration.java?rev=120
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

import java.util.Enumeration;
import java.util.NoSuchElementException;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;


public class ChildrenEnumeration implements Enumeration {

	private TreePath path;
	private TreeModel model;
	private int position = 0;
	private int childCount;


	public ChildrenEnumeration( TreePath path, TreeModel model ) {
		this.path = path;
		this.model = model;
		childCount = model.getChildCount( path.getLastPathComponent() );
	}

	@Override
	public boolean hasMoreElements() {
		return position < childCount;
	}

	@Override
	public Object nextElement() {
		if( !hasMoreElements() ) throw new NoSuchElementException();

		return path.pathByAddingChild( model.getChild( path.getLastPathComponent(), position++ ) );
	}
}
