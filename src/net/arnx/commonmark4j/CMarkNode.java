package net.arnx.commonmark4j;

import java.util.Iterator;

public interface CMarkNode extends Iterable<CMarkNode> {
	public CMarkNodeType type();

	public int[][] sourcepos();

	public String literal();

	public CMarkNode parent();

	public Iterator<CMarkNode> iterator();
}
