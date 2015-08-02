/**
 * Copyright (c) 2015, Hidekatsu Izuno <hidekatsu.izuno@gmail.com>
 *
 * This software is released under the 2 clause BSD License, see LICENSE.txt.
 */
package net.arnx.commonmark4j.impl;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.NoSuchElementException;

import net.arnx.commonmark4j.CMarkNode;
import net.arnx.commonmark4j.CMarkNodeType;

public class Node implements CMarkNode {
	private static boolean isContainer(Node node) {
		switch (node._type) {
		case DOCUMENT:
		case BLOCK_QUOTE:
		case LIST:
		case ITEM:
		case PARAGRAPH:
		case HEADER:
		case EMPH:
		case STRONG:
		case LINK:
		case IMAGE:
			return true;
		default:
			return false;
		}
	}

	CMarkNodeType _type;
	Node _parent;
	Node _firstChild;
	Node _lastChild;
	Node _prev;
	Node _next;
	int[][] _sourcepos;
	boolean _lastLineBlank;
	boolean _open;
	String _string_content;
	String _literal;
	ListData _listData;
	String _info;
	String _destination;
	String _title;
	boolean _isFenced;
	char _fenceChar;
	int _fenceLength;
	int _fenceOffset;
	int _level;
	int _htmlBlockType;

	public static class Event {
		public boolean entering;
		public Node node;
	}

	public static class NodeWalker {
		public Node current;
		public Node root;
		public boolean entering = true;

		public NodeWalker(Node root) {
			this.current = root;
			this.root = root;
		}

		public Event next() {
			Node cur = this.current;
			boolean entering = this.entering;

			if (cur == null) {
				return null;
			}

			boolean container = isContainer(cur);

			if (entering && container) {
				if (cur._firstChild != null) {
					this.current = cur._firstChild;
					this.entering = true;
				} else {
					// stay on node but exit
					this.entering = false;
				}

			} else if (cur == this.root) {
				this.current = null;

			} else if (cur._next == null) {
				this.current = cur._parent;
				this.entering = false;

			} else {
				this.current = cur._next;
				this.entering = true;
			}

			Event next = new Event();
			next.entering = entering;
			next.node = cur;
			return next;
		}

		public void resumeAt(Node node, boolean entering) {
			this.current = node;
			this.entering = entering;
		}
	}

	public Node(CMarkNodeType nodeType, int[][] sourcepos) {
		this._type = nodeType;
		this._parent = null;
		this._firstChild = null;
		this._lastChild = null;
		this._prev = null;
		this._next = null;
		this._sourcepos = sourcepos;
		this._lastLineBlank = false;
		this._open = true;
		this._string_content = null;
		this._literal = null;
		this._listData = null;
		this._info = null;
		this._destination = null;
		this._title = null;
		this._isFenced = false;
		this._fenceChar = '\0';
		this._fenceLength = 0;
		this._fenceOffset = -1;
		this._level = -1;
	}

	public boolean isContainer() {
		return isContainer(this);
	}

	@Override
	public CMarkNodeType type() {
		return _type;
	}

	public Node firstChild() {
		return _firstChild;
	}

	public Node lastChild() {
		return _lastChild;
	}

	public Node next() {
		return _next;
	}

	public Node prev() {
		return _prev;
	}

	@Override
	public Node parent() {
		return _parent;
	}

	public int[][] sourcepos() {
		return _sourcepos;
	}

	public String literal() {
		return _literal;
	}

	public void literal(String s) {
		this._literal = s;
	}

	public String destination() {
		return _destination;
	}

	public void destination(String s) {
		this._destination = s;
	}

	public String title() {
		return _title;
	}

	public void title(String s) {
		this._title = s;
	}

	public String info() {
		return _info;
	}

	public void info(String s) {
		this._info = s;
	}

	public int level() {
		return _level;
	}

	public void level(int s) {
		this._level = s;
	}

	public ListType listType() {
		return this._listData.type;
	}

	public void listType(ListType t) {
		this._listData.type = t;
	}

	public Boolean listTight() {
		return this._listData.tight;
	}

	public void listTight(Boolean t) {
		this._listData.tight = t;
	}

	public Integer listStart() {
		return this._listData.start;
	}

	public void listStart(Integer n) {
		this._listData.start = n;
	}

	public Character listDelimiter() {
		return this._listData.delimiter;
	}

	public void listDelimiter(Character delim) {
		this._listData.delimiter = delim;
	}

	public void appendChild(Node child) {
		child.unlink();
		child._parent = this;
		if (this._lastChild != null) {
			this._lastChild._next = child;
			child._prev = this._lastChild;
			this._lastChild = child;
		} else {
			this._firstChild = child;
			this._lastChild = child;
		}
	}

	public void prependChild(Node child) {
		child.unlink();
		child._parent = this;
		if (this._firstChild != null) {
			this._firstChild._prev = child;
			child._next = this._firstChild;
			this._firstChild = child;
		} else {
			this._firstChild = child;
			this._lastChild = child;
		}
	}

	public void unlink() {
		if (this._prev != null) {
			this._prev._next = this._next;
		} else if (this._parent != null) {
			this._parent._firstChild = this._next;
		}
		if (this._next != null) {
			this._next._prev = this._prev;
		} else if (this._parent != null) {
			this._parent._lastChild = this._prev;
		}
		this._parent = null;
		this._next = null;
		this._prev = null;
	}

	public void insertAfter(Node sibling) {
		sibling.unlink();
		sibling._next = this._next;
		if (sibling._next != null) {
			sibling._next._prev = sibling;
		}
		sibling._prev = this;
		this._next = sibling;
		sibling._parent = this._parent;
		if (sibling._next == null) {
			sibling._parent._lastChild = sibling;
		}
	}

	public void insertBefore(Node sibling) {
		sibling.unlink();
		sibling._prev = this._prev;
		if (sibling._prev != null) {
			sibling._prev._next = sibling;
		}
		sibling._next = this;
		this._prev = sibling;
		sibling._parent = this._parent;
		if (sibling._prev == null) {
			sibling._parent._firstChild = sibling;
		}
	}

	public NodeWalker walker() {
		NodeWalker walker = new NodeWalker(this);
		return walker;
	}

	@Override
	public Iterator<CMarkNode> iterator() {
		return new Iterator<CMarkNode>() {
			private Node current = Node.this;
			private boolean available = true;

			@Override
			public boolean hasNext() {
				if (!available) {
					if (current == null) {
						// no handle
					} else if (current._firstChild != null) {
						current = current._firstChild;
					} else {
						boolean find = false;

						Node target = current;
						while (target != null) {
							if (target._next != null) {
								current = target._next;
								find = true;
								break;
							}
							target = target._parent;
						}

						if (!find) {
							current = null;
						}
					}

					available = true;
				}
				return (current != null);
			}

			@Override
			public CMarkNode next() {
				hasNext();

				if (current == null) {
					throw new NoSuchElementException();
				}

				available = false;
				return current;
			}
		};
	}

	@Override
	public String toString() {
		StringWriter writer = new StringWriter();
		try {
			(new XmlRenderer()).render(this, writer);
		} catch (IOException e) {
			return "";
		}
		return writer.toString();
	}
}
