/**
 * Copyright (c) 2015, Hidekatsu Izuno <hidekatsu.izuno@gmail.com>
 *
 * This software is released under the 2 clause BSD License, see LICENSE.
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
import net.arnx.commonmark4j.CMarkNodeType;
import net.arnx.commonmark4j.CMarkRenderer;
import net.arnx.commonmark4j.impl.Node.Event;
import net.arnx.commonmark4j.impl.Node.NodeWalker;

public class XmlRenderer implements CMarkRenderer {
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

	private static Pattern reXMLTag = Pattern.compile("\\<[^>]*\\>");

	private static String toTagName(CMarkNodeType s) {
		return s.name().toLowerCase();
	}

	class RenderNodes {
		List<Attr> attrs;
		String tagname;
		NodeWalker walker;
		Event event; Node node; boolean entering;
		char lastOut = '\n';
		int disableTags = 0;
		int indentLevel = 0;
		String indent = "  ";
		boolean unescapedContents;
		boolean container;
		boolean selfClosing;
		CMarkNodeType nodetype;

		private void out(Appendable buffer, String s) throws IOException {
			if (disableTags > 0) {
				buffer.append(replace(s, reXMLTag, ""));
			} else {
				buffer.append(s);
			}
			lastOut = s.equals("\n") ? '\n' : ' ';
		}

		private void cr(Appendable buffer) throws IOException {
			if (lastOut != '\n') {
				buffer.append('\n');
				lastOut = '\n';
				for (int i = indentLevel; i > 0; i--) {
					buffer.append(indent);
				}
			}
		}

		public void render(Node block, Appendable buffer) throws IOException {
			walker = block.walker();
			long time = 0L;

			if (options.time) { time = System.currentTimeMillis(); }

			buffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			buffer.append("<!DOCTYPE CommonMark SYSTEM \"CommonMark.dtd\">\n");

			Map<String, String> attrs = new LinkedHashMap<>();
			while ((event = walker.next()) != null) {
				boolean entering = event.entering;
				Node node = event.node;
				CMarkNodeType nodetype = node.type();

				container = node.isContainer();
				selfClosing = nodetype == CMarkNodeType.HORIZONTAL_RULE || nodetype == CMarkNodeType.HARDBREAK ||
						nodetype == CMarkNodeType.SOFTBREAK;
				unescapedContents = nodetype == CMarkNodeType.HTML || nodetype == CMarkNodeType.HTML_BLOCK;
				tagname = toTagName(nodetype);

				if (entering) {

					attrs.clear();

					switch (nodetype) {
					case LIST:
						if (node.listType() != null) {
							attrs.put("type", node.listType().name().toLowerCase());
						}
						if (node.listStart() != null) {
							attrs.put("start", "" + node.listStart());
						}
						if (node.listTight() != null) {
							attrs.put("tight", (node.listTight()) ? "true" : "false");
						}
						Character delim = node.listDelimiter();
						if (delim != null) {
							String delimword = "";
							if (delim == '.') {
								delimword = "period";
							} else {
								delimword = "paren";
							}
							attrs.put("delimiter", delimword);
						}
						break;
					case CODE_BLOCK:
						if (node.info() != null) {
							attrs.put("info", node.info());
						}
						break;
					case HEADER:
						attrs.put("level", "" + node.level());
						break;
					case LINK:
					case IMAGE:
						attrs.put("destination", node.destination());
						attrs.put("title", node.title());
						break;
					default:
						break;
					}
					if (options.sourcepos) {
						int[][] pos = node.sourcepos();
						if (pos != null) {
							attrs.put("sourcepos", "" + pos[0][0] + ':' +
									pos[0][1] + '-' + pos[1][0] + ':' +
									pos[1][1]);
						}
					}

					cr(buffer);
					out(buffer, tag(tagname, attrs, selfClosing));
					if (container) {
						indentLevel += 1;
					} else if (!container && !selfClosing) {
						String lit = node.literal();
						if (lit != null) {
							out(buffer, unescapedContents ? lit : escape(lit, false));
						}
						out(buffer, tag("/" + tagname, null, false));
					}
				} else {
					indentLevel -= 1;
					cr(buffer);
					out(buffer, tag("/" + tagname, null, false));
				}
			}

			if (options.time) { System.out.println("rendering: " + ((System.currentTimeMillis() - time) / 1000.0) + "ms"); }
			buffer.append("\n");
		}
	}

	// The XmlRenderer object
	public XmlRenderer() {
		this(null);
	}

	public XmlRenderer(Options options) {
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
