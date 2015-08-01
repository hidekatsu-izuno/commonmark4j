/**
 * Copyright (c) 2015, Hidekatsu Izuno <hidekatsu.izuno@gmail.com>
 *
 * This software is released under the 2 clause BSD License, see LICENSE.txt.
 */
package net.arnx.commonmark4j.impl;

import static net.arnx.commonmark4j.impl.Common.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.w3c.dom.Attr;

import net.arnx.commonmark4j.CMarkNode;
import net.arnx.commonmark4j.CMarkRenderer;
import net.arnx.commonmark4j.CMarkNodeType;
import net.arnx.commonmark4j.impl.Node.Event;
import net.arnx.commonmark4j.impl.Node.NodeWalker;

public class HtmlRenderer implements CMarkRenderer {
	private static String tag(String name, Map<String, String> attrs, boolean selfclosing) {
		StringBuilder result = new StringBuilder().append("<").append(name);
		if (attrs != null) {
			for (Map.Entry<String, String> attrib : attrs.entrySet()) {
				result.append(' ').append(attrib.getKey()).append("=\"").append(attrib.getValue()).append("\"");
			}
		}
		if (selfclosing) {
			result.append(" /");
		}

		result.append(">");
		return result.toString();
	}

	private static final Pattern reHtmlTag = Pattern.compile("\\<[^>]*\\>");
	private static final Pattern reUnsafeProtocol = Pattern.compile("^javascript:|vbscript:|file:|data:", Pattern.CASE_INSENSITIVE);
	private static final Pattern reSafeDataProtocol = Pattern.compile("^data:image/(?:png|gif|jpeg|webp)", Pattern.CASE_INSENSITIVE);

	boolean potentiallyUnsafe(String url) {
		return reUnsafeProtocol.matcher(url).find() &&
				!reSafeDataProtocol.matcher(url).find();
	}

	class RenderNodes {
		List<Attr> attrs;
		String[] info_words;
		String tagname;
		NodeWalker walker;
		Event event; Node node; boolean entering;
		char lastOut = '\n';
		int disableTags = 0;
		Node grandparent;

		private void out(Appendable buffer, String s) throws IOException {
			if (disableTags > 0) {
				buffer.append(replace(s, reHtmlTag, ""));
			} else {
				buffer.append(s);
			}
			lastOut = s.equals("\n") ? '\n' : ' ';
		}

		private void cr(Appendable buffer) throws IOException {
			if (lastOut != '\n') {
				buffer.append('\n');
				lastOut = '\n';
			}
		}

		public void render(Node block, Appendable buffer) throws IOException {
			walker = block.walker();
			long time = 0L;

			if (options.time) { time = System.currentTimeMillis(); }

			Map<String, String> attrs = new LinkedHashMap<>();
			while ((event = walker.next()) != null) {
				boolean entering = event.entering;
				Node node = event.node;

				attrs.clear();
				if (options.sourcepos) {
					int[][] pos = node.sourcepos();
					if (pos != null) {
						attrs.put("data-sourcepos", "" + pos[0][0] + ':' +
								pos[0][1] + '-' + pos[1][0] + ':' +
								pos[1][1]);
					}
				}

				switch (node.type()) {
				case TEXT:
					out(buffer, escape(node.literal(), false));
					break;

				case SOFTBREAK:
					out(buffer, options.softbreak);
					break;

				case HARDBREAK:
					out(buffer, tag("br", null, true));
					cr(buffer);
					break;

				case EMPH:
					out(buffer, tag(entering ? "em" : "/em", null, false));
					break;

				case STRONG:
					out(buffer, tag(entering ? "strong" : "/strong", null, false));
					break;

				case HTML:
					if (options.safe) {
						out(buffer, "<!-- raw HTML omitted -->");
					} else {
						out(buffer, node.literal());
					}
					break;

				case LINK:
					if (entering) {
						if (!(options.safe && potentiallyUnsafe(node.destination()))) {
							attrs.put("href", escape(node.destination(), true));
						}
						if (node.title() != null && !node.title().isEmpty()) {
							attrs.put("title", escape(node.title(), true));
						}
						out(buffer, tag("a", attrs, false));
					} else {
						out(buffer, tag("/a", null, false));
					}
					break;

				case IMAGE:
					if (entering) {
						if (disableTags == 0) {
							if (options.safe &&
									potentiallyUnsafe(node.destination())) {
								out(buffer, "<img src=\"\" alt=\"");
							} else {
								out(buffer, "<img src=\"" + escape(node.destination(), true) +
										"\" alt=\"");
							}
						}
						disableTags += 1;
					} else {
						disableTags -= 1;
						if (disableTags == 0) {
							if (node.title() != null && !node.title().isEmpty()) {
								out(buffer, "\" title=\"" + escape(node.title(), true));
							}
							out(buffer, "\" />");
						}
					}
					break;

				case CODE:
					out(buffer, tag("code", null, false) + escape(node.literal(), false) + tag("/code", null, false));
					break;

				case DOCUMENT:
					break;

				case PARAGRAPH:
					grandparent = node.parent().parent();
					if (grandparent != null &&
							grandparent.type() == CMarkNodeType.LIST) {
						if (grandparent.listTight()) {
							break;
						}
					}
					if (entering) {
						cr(buffer);
						out(buffer, tag("p", attrs, false));
					} else {
						out(buffer, tag("/p", null, false));
						cr(buffer);
					}
					break;

				case BLOCK_QUOTE:
					if (entering) {
						cr(buffer);
						out(buffer, tag("blockquote", attrs, false));
						cr(buffer);
					} else {
						cr(buffer);
						out(buffer, tag("/blockquote", null, false));
						cr(buffer);
					}
					break;

				case ITEM:
					if (entering) {
						out(buffer, tag("li", attrs, false));
					} else {
						out(buffer, tag("/li", null, false));
						cr(buffer);
					}
					break;

				case LIST:
					tagname = node.listType() == ListType.BULLET ? "ul" : "ol";
					if (entering) {
						int start = node.listStart();
						if (start == 0 || start > 1) {
							attrs.put("start", "" + start);
						}
						cr(buffer);
						out(buffer, tag(tagname, attrs, false));
						cr(buffer);
					} else {
						cr(buffer);
						out(buffer, tag("/" + tagname, null, false));
						cr(buffer);
					}
					break;

				case HEADER:
					tagname = "h" + node.level();
					if (entering) {
						cr(buffer);
						out(buffer, tag(tagname, attrs, false));
					} else {
						out(buffer, tag("/" + tagname, null, false));
						cr(buffer);
					}
					break;

				case CODE_BLOCK:
					info_words = node.info() != null ? node.info().split(SPACE + "+") : new String[0];
					if (info_words.length > 0 && info_words[0].length() > 0) {
						attrs.put("class", "language-" + escape(info_words[0], true));
					}
					cr(buffer);
					out(buffer, tag("pre", null, false) + tag("code", attrs, false));
					out(buffer, escape(node.literal(), false));
					out(buffer, tag("/code", null, false) + tag("/pre", null, false));
					cr(buffer);
					break;

				case HTML_BLOCK:
					cr(buffer);
					if (options.safe) {
						out(buffer, "<!-- raw HTML omitted -->");
					} else {
						out(buffer, node.literal());
					}
					cr(buffer);
					break;

				case HORIZONTAL_RULE:
					cr(buffer);
					out(buffer, tag("hr", attrs, true));
					cr(buffer);
					break;

				default:
					throw new IllegalStateException("Unknown node type " + node.type());
				}
			}
			if (options.time) { System.out.println("rendering: " + ((System.currentTimeMillis() - time) / 1000.0) + "ms"); }
		}
	}

	// The HtmlRenderer object.
	public HtmlRenderer() {
		this(null);
	}

	public HtmlRenderer(Options options) {
		this.options = (options != null) ? options : new Options();
	}

	public String escape(String s, boolean preserve_entities) {
		return Common.escapeXml(s, preserve_entities);
	}

	@Override
	public void render(CMarkNode block, Appendable out) throws IOException {
		new RenderNodes().render((Node)block, out);
	}

	// default options:
	Options options;

	public static class Options {
		boolean time;
		boolean sourcepos;
		boolean safe;
		String softbreak = "\n";

		public Options time(boolean flag) {
			time = flag;
			return this;
		}

		public Options sourcepos(boolean flag) {
			sourcepos = flag;
			return this;
		}

		public Options safe(boolean flag) {
			safe = flag;
			return this;
		}

		// by default, soft breaks are rendered as newlines in HTML
		// set to "<br />" to make them hard breaks
		// set to " " if you want to ignore line wrapping in source
		public Options softbreak(String value) {
			softbreak = value;
			return this;
		}
	}
}
