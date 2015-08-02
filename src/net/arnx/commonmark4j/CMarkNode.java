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
	 * Iterates a node tree from this node.
	 */
	public Iterator<CMarkNode> iterator();
}
