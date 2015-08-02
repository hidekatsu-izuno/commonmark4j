/**
 * Copyright (c) 2015, Hidekatsu Izuno <hidekatsu.izuno@gmail.com>
 *
 * This software is released under the 2 clause BSD License, see LICENSE.
 */
package net.arnx.commonmark4j;

import java.util.Iterator;

/**
 * Defines the requirements for an object that can be used as a CommonMark node.
 *
 * @author Hidekatsu Izuno
 */
public interface CMarkNode extends Iterable<CMarkNode> {
	/**
	 * Gets a node type.
	 *
	 * @return a node type
	 */
	public CMarkNodeType type();

	/**
	 * Gets source positon information.
	 *
	 * @return source positon information
	 */
	public int[][] sourcepos();

	/**
	 * Gets a content literal text.
	 *
	 * @return a content literal text
	 */
	public String literal();

	/**
	 * Gets a parent node.
	 *
	 * @return parent node
	 */
	public CMarkNode parent();

	/**
	 * Gets a first child node.
	 *
	 * @return a first child node.
	 */
	public CMarkNode firstChild();

	/**
	 * Gets a last child node.
	 *
	 * @return a last child node.
	 */
	public CMarkNode lastChild();

	/**
	 * Gets a previous node.
	 *
	 * @return a previous node.
	 */
	public CMarkNode prev();

	/**
	 * Gets a next node.
	 *
	 * @return a next node.
	 */
	public CMarkNode next();

	/**
	 * Iterates a node tree from this node.
	 */
	public Iterator<CMarkNode> iterator();
}
