/**
 * Copyright (c) 2015, Hidekatsu Izuno <hidekatsu.izuno@gmail.com>
 *
 * This software is released under the 2 clause BSD License, see LICENSE.
 */
package net.arnx.commonmark4j.impl;

import java.io.BufferedReader;
import java.io.IOException;

import net.arnx.commonmark4j.CMarkNode;
import net.arnx.commonmark4j.CMarkParser;
import net.arnx.commonmark4j.CMarkRenderer;
import net.arnx.commonmark4j.CMarkTransformer;

public class Transformer implements CMarkTransformer {
	private String format = "html";
	private boolean safe;
	private boolean smart;
	private String softbreak = "\n";
	private boolean sourcepos;
	private boolean time;

	@Override
	public CMarkTransformer format(String text) {
		format = text;
		return this;
	}

	@Override
	public CMarkTransformer safe(boolean value) {
		safe = value;
		return this;
	}

	@Override
	public CMarkTransformer smart(boolean value) {
		smart = value;
		return this;
	}

	@Override
	public CMarkTransformer softbreak(String text) {
		softbreak = text;
		return this;
	}

	@Override
	public CMarkTransformer sourcepos(boolean value) {
		sourcepos = value;
		return this;
	}

	@Override
	public CMarkTransformer time(boolean value) {
		time = value;
		return this;
	}

	@Override
	public void transform(BufferedReader in, Appendable out) throws IOException {
		CMarkParser parser = new Parser(new Parser.Options()
				.smart(smart)
				.time(time));
		CMarkNode node = parser.parse(in);

		CMarkRenderer renderer;
		if (format.equals("xml")) {
			renderer = new XmlRenderer(new XmlRenderer.Options()
					.time(time)
					.safe(safe)
					.sourcepos(sourcepos)
					.softbreak(softbreak));
		} else {
			renderer = new HtmlRenderer(new HtmlRenderer.Options()
					.time(time)
					.safe(safe)
					.sourcepos(sourcepos)
					.softbreak(softbreak));
		}

		renderer.render(node, out);
	}

}
