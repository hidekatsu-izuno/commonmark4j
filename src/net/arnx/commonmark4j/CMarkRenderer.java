package net.arnx.commonmark4j;

import java.io.IOException;

import net.arnx.commonmark4j.impl.HtmlRenderer;

public interface CMarkRenderer {
	public static CMarkRenderer newHtmlRenderer() {
		return new HtmlRenderer();
	}

	public void render(CMarkNode node, Appendable out) throws IOException;
}
