package net.arnx.commonmark4j;

import java.io.IOException;

import net.arnx.commonmark4j.impl.HtmlRenderer;

/**
 * Defines the requirements for an object that can be used as a CommonMark renderer.
 *
 * @author Hidekatsu Izuno
 */
public interface CMarkRenderer {
	/**
	 * Creates a default HTML reanderer.
	 *
	 * @return a new HTML reanderer instance.
	 */
	public static CMarkRenderer newHtmlRenderer() {
		return new HtmlRenderer();
	}

	/**
	 * Render CommonMark contents.
	 *
	 * @param node a root node.
	 * @param out a destination.
	 * @throws IOException an I/O error occures.
	 */
	public void render(CMarkNode node, Appendable out) throws IOException;
}
