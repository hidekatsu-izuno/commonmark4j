/**
 * Copyright (c) 2015, Hidekatsu Izuno <hidekatsu.izuno@gmail.com>
 *
 * This software is released under the 2 clause BSD License, see LICENSE.txt.
 */
package net.arnx.commonmark4j.impl;

import static net.arnx.commonmark4j.impl.Common.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.arnx.commonmark4j.CMarkParser;
import net.arnx.commonmark4j.CMarkNodeType;
import net.arnx.commonmark4j.impl.Node.Event;
import net.arnx.commonmark4j.impl.Node.NodeWalker;

public class Parser implements CMarkParser {
	static final int CODE_INDENT = 4;

	static final char C_NEWLINE = 10;
	static final char C_GREATERTHAN = 62;
	static final char C_LESSTHAN = 60;
	static final char C_SPACE = 32;
	static final char C_OPEN_BRACKET = 91;

	static Pattern[] reHtmlBlockOpen = {
			Pattern.compile("."), // dummy for 0
			Pattern.compile("^<(?:script|pre|style)(?:" + SPACE + "|>|$)", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^<!--"),
			Pattern.compile("^<[?]"),
			Pattern.compile("^<![A-Z]"),
			Pattern.compile("^<!\\[CDATA\\["),
			Pattern.compile("^<[/]?(?:address|article|aside|base|basefont|blockquote|body|caption|center|col|colgroup|dd|details|dialog|dir|div|dl|dt|fieldset|figcaption|figure|footer|form|frame|frameset|h1|head|header|hr|html|legend|li|link|main|menu|menuitem|meta|nav|noframes|ol|optgroup|option|p|param|pre|section|source|title|summary|table|tbody|td|tfoot|th|thead|title|tr|track|ul)(?:" + SPACE + "|[/]?[>]|$)", Pattern.CASE_INSENSITIVE),
			Pattern.compile("^(?:" + OPENTAG + "|" + CLOSETAG + ")" + SPACE + "*$", Pattern.CASE_INSENSITIVE)
	};

	static Pattern[] reHtmlBlockClose = {
			Pattern.compile("."), // dummy for 0
			Pattern.compile("<\\/(?:script|pre|style)>", Pattern.CASE_INSENSITIVE),
			Pattern.compile("-->"),
			Pattern.compile("\\?>"),
			Pattern.compile(">"),
			Pattern.compile("\\]\\]>")
	};

	static Pattern reHrule = Pattern.compile("^(?:(?:\\* *){3,}|(?:_ *){3,}|(?:- *){3,}) *$");

	static Pattern reMaybeSpecial = Pattern.compile("^[#`~*+_=<>0-9-]");

	static Pattern reNonSpace = Pattern.compile("[^ \\t\\f\\v\\r\\n]");

	static Pattern reBulletListMarker = Pattern.compile("^[*+-]( +|$)");

	static Pattern reOrderedListMarker = Pattern.compile("^(\\d{1,9})([.)])( +|$)");

	static Pattern reATXHeaderMarker = Pattern.compile("^#{1,6}(?: +|$)");

	static Pattern reCodeFence = Pattern.compile("^`{3,}(?!.*`)|^~{3,}(?!.*~)");

	static Pattern reClosingCodeFence = Pattern.compile("^(?:`{3,}|~{3,})(?= *$)");

	static Pattern reSetextHeaderLine = Pattern.compile("^(?:=+|-+) *$");

	// Returns true if string contains only space characters.
	static boolean isBlank(String s) {
		return !reNonSpace.matcher(s).find();
	}

	static int peek(String ln, int pos) {
		if (pos < ln.length()) {
			return ln.charAt(pos);
		} else {
			return -1;
		}
	}

	// DOC PARSER

	// These are methods of a Parser object, defined below.

	// Returns true if block ends with a blank line, descending if needed
	// into lists and sublists.
	static boolean endsWithBlankLine(Node block) {
		while (block != null) {
			if (block._lastLineBlank) {
				return true;
			}
			CMarkNodeType t = block.type();
			if (t == CMarkNodeType.LIST || t == CMarkNodeType.ITEM) {
				block = block._lastChild;
			} else {
				break;
			}
		}
		return false;
	}

	// Break out of all containing lists, resetting the tip of the
	// document to the parent of the highest list, and finalizing
	// all the lists.  (This is used to implement the "two blank lines
	// break of of all lists" feature.)
	void breakOutOfLists(Node block) {
		Node b = block;
		Node last_list = null;
		do {
			if (b.type() == CMarkNodeType.LIST) {
				last_list = b;
			}
			b = b._parent;
		} while (b != null);

		if (last_list != null) {
			while (block != last_list) {
				this.finalize(block, this.lineNumber);
				block = block._parent;
			}
			this.finalize(last_list, this.lineNumber);
			this.tip = last_list._parent;
		}
	}

	// Add a line to the block at the tip.  We assume the tip
	// can accept lines -- that check should be done before calling this.
	void addLine() {
		this.tip._string_content += this.currentLine.substring(this.offset) + "\n";
	}

	// Add block of type tag as a child of the tip.  If the tip can't
	// accept children, close and finalize it and try its parent,
	// and so on til we find a block that can accept children.
	Node addChild(CMarkNodeType tag, int offset) {
		while (!blocks.get(this.tip.type()).canContain(tag)) {
			this.finalize(this.tip, this.lineNumber - 1);
		}

		int column_number = offset + 1; // offset 0 = column 1
		Node newBlock = new Node(tag, new int[][] {{this.lineNumber, column_number}, {0, 0}});
		newBlock._string_content = "";
		this.tip.appendChild(newBlock);
		this.tip = newBlock;
		return newBlock;
	}

	// Parse a list marker and return data on the marker (type,
	// start, delimiter, bullet character, padding) or null.
	static ListData parseListMarker(String ln, int offset, int indent) {
		String rest = ln.substring(offset);
		Matcher match;
		int spaces_after_marker;
		ListData data = new ListData(null,
				true,  // lists are tight by default
				'\0',
				-1,
				'\0',
				-1,
				indent);
		if ((match = reBulletListMarker.matcher(rest)).find()) {
			spaces_after_marker = match.group(1).length();
			data.type = ListType.BULLET;
			data.bulletChar = match.group().charAt(0);

		} else if ((match = reOrderedListMarker.matcher(rest)).find()) {
			spaces_after_marker = match.group(3).length();
			data.type = ListType.ORDERED;
			data.start = Integer.parseInt(match.group(1));
			data.delimiter = match.group(2).charAt(0);
		} else {
			return null;
		}
		boolean blank_item = match.group().length() == rest.length();
		if (spaces_after_marker >= 5 ||
				spaces_after_marker < 1 ||
				blank_item) {
			data.padding = match.group().length() - spaces_after_marker + 1;
		} else {
			data.padding = match.group().length();
		}
		return data;
	}

	// Returns true if the two list items are of the same type,
	// with the same delimiter and bullet character.  This is used
	// in agglomerating list items into lists.
	static boolean listsMatch(ListData list_data, ListData item_data) {
		return (list_data.type == item_data.type &&
				list_data.delimiter == item_data.delimiter &&
				list_data.bulletChar == item_data.bulletChar);
	}

	// Finalize and close any unmatched blocks. Returns true.
	void closeUnmatchedBlocks() {
		if (!this.allClosed) {
			// finalize any blocks not matched
			while (this.oldtip != this.lastMatchedContainer) {
				Node parent = this.oldtip._parent;
				this.finalize(this.oldtip, this.lineNumber - 1);
				this.oldtip = parent;
			}
			this.allClosed = true;
		}
	}

	// 'finalize' is run when the block is closed.
	// 'continue' is run to check whether the block is continuing
	// at a certain line and offset (e.g. whether a block quote
	// contains a `>`.  It returns 0 for matched, 1 for not matched,
	// and 2 for "we've dealt with this line completely, go to next."
	static final Map<CMarkNodeType, Block> blocks = new EnumMap<CMarkNodeType, Block>(CMarkNodeType.class) {
		{
			put(CMarkNodeType.DOCUMENT, new Block() {
				@Override
				public int continue_(Parser parser, Node container) {
					return 0;
				}
				@Override
				public void finalize(Parser parser, Node block) {
				}
				@Override
				public boolean canContain(CMarkNodeType t) {
					return t != CMarkNodeType.ITEM;
				}
				@Override
				public boolean acceptsLines() {
					return false;
				}
			});
			put(CMarkNodeType.LIST, new Block() {
				@Override
				public int continue_(Parser parser, Node container) {
					return 0;
				}
				@Override
				public void finalize(Parser parser, Node block) {
					Node item = block._firstChild;
					while (item != null) {
						// check for non-final list item ending with blank line:
						if (endsWithBlankLine(item) && item._next != null) {
							block._listData.tight = false;
							break;
						}
						// recurse into children of list item, to see if there are
						// spaces between any of them:
						Node subitem = item._firstChild;
						while (subitem != null) {
							if (endsWithBlankLine(subitem) &&
									(item._next != null || subitem._next != null)) {
								block._listData.tight = false;
								break;
							}
							subitem = subitem._next;
						}
						item = item._next;
					}
				}
				@Override
				public boolean canContain(CMarkNodeType t) {
					return t == CMarkNodeType.ITEM;
				}
				@Override
				public boolean acceptsLines() {
					return false;
				}
			});
			put(CMarkNodeType.BLOCK_QUOTE, new Block() {
				@Override
				public int continue_(Parser parser, Node container) {
					String ln = parser.currentLine;
					if (!parser.indented &&
							peek(ln, parser.nextNonspace) == C_GREATERTHAN) {
						parser.advanceNextNonspace();
						parser.advanceOffset(1, false);
						if (peek(ln, parser.offset) == C_SPACE) {
							parser.offset++;
						}
					} else {
						return 1;
					}
					return 0;
				}
				@Override
				public void finalize(Parser parser, Node block) {
				}
				@Override
				public boolean canContain(CMarkNodeType t) {
					return t != CMarkNodeType.ITEM;
				}

				@Override
				public boolean acceptsLines() {
					return false;
				}
			});
			put(CMarkNodeType.ITEM, new Block() {
				@Override
				public int continue_(Parser parser, Node container) {
					if (parser.blank) {
						parser.advanceNextNonspace();
					} else if (parser.indent >=
							container._listData.markerOffset +
							container._listData.padding) {
						parser.advanceOffset(container._listData.markerOffset +
								container._listData.padding, true);
					} else {
						return 1;
					}
					return 0;
				}
				@Override
				public void finalize(Parser parser, Node block) {
				}
				@Override
				public boolean canContain(CMarkNodeType t) {
					return t != CMarkNodeType.ITEM;
				}
				@Override
				public boolean acceptsLines() {
					return false;
				}
			});
			put(CMarkNodeType.HEADER, new Block() {
				@Override
				public int continue_(Parser parser, Node container) {
					// a header can never container > 1 line, so fail to match:
					return 1;
				}
				@Override
				public void finalize(Parser parser, Node block) {

				}
				@Override
				public boolean canContain(CMarkNodeType t) {
					return false;
				}
				@Override
				public boolean acceptsLines() {
					return false;
				}
			});
			put(CMarkNodeType.HORIZONTAL_RULE, new Block() {
				@Override
				public int continue_(Parser parser, Node container) {
					// a header can never container > 1 line, so fail to match:
					return 1;
				}
				@Override
				public void finalize(Parser parser, Node block) {
				}
				@Override
				public boolean canContain(CMarkNodeType t) {
					return false;
				}
				@Override
				public boolean acceptsLines() {
					return false;
				}
			});
			put(CMarkNodeType.CODE_BLOCK, new Block() {
				@Override
				public int continue_(Parser parser, Node container) {
					String ln = parser.currentLine;
					int indent = parser.indent;
					if (container._isFenced) { // fenced
						Matcher m = null;
						if (indent <= 3 && parser.nextNonspace < ln.length() && ln.charAt(parser.nextNonspace) == container._fenceChar
								&& (m = reClosingCodeFence.matcher(ln.substring(parser.nextNonspace))).find()
								&& m.group(0).length() >= container._fenceLength) {
							// closing fence - we're at end of line, so we can return
							parser.finalize(container, parser.lineNumber);
							return 2;
						} else {
							// skip optional spaces of fence offset
							int i = container._fenceOffset;
							while (i > 0 && peek(ln, parser.offset) == C_SPACE) {
								parser.advanceOffset(1, false);
								i--;
							}
						}
					} else { // indented
						if (indent >= CODE_INDENT) {
							parser.advanceOffset(CODE_INDENT, true);
						} else if (parser.blank) {
							parser.advanceNextNonspace();
						} else {
							return 1;
						}
					}
					return 0;
				}
				@Override
				public void finalize(Parser parser, Node block) {
					if (block._isFenced) { // fenced
						// first line becomes info string
						String content = block._string_content;
						int newlinePos = content.indexOf('\n');
						String firstLine = content.substring(0, newlinePos);
						String rest = content.substring(newlinePos + 1);
						block.info(Common.unescapeString(firstLine.trim()));
						block._literal = rest;
					} else { // indented
						block._literal = block._string_content.replaceFirst("(\\n *)+$", "\n");
					}
					block._string_content = null; // allow GC
				}
				@Override
				public boolean canContain(CMarkNodeType t) {
					return false;
				}
				@Override
				public boolean acceptsLines() {
					return true;
				}
			});
			put(CMarkNodeType.HTML_BLOCK, new Block() {
				@Override
				public int continue_(Parser parser, Node container) {
					return ((parser.blank &&
							(container._htmlBlockType == 6 ||
							container._htmlBlockType == 7)) ? 1 : 0);
				}
				@Override
				public void finalize(Parser parser, Node block) {
					block._literal = block._string_content.replaceFirst("(\\n *)+$", "");
					block._string_content = null; // allow GC
				}
				@Override
				public boolean canContain(CMarkNodeType t) {
					return false;
				}
				@Override
				public boolean acceptsLines() {
					return true;
				}
			});
			put(CMarkNodeType.PARAGRAPH, new Block() {
				@Override
				public int continue_(Parser parser, Node container) {
					return (parser.blank ? 1 : 0);
				}
				@Override
				public void finalize(Parser parser, Node block) {
					int pos;
					boolean hasReferenceDefs = false;

					// try parsing the beginning as link reference definitions:
					while (peek(block._string_content, 0) == C_OPEN_BRACKET &&
							(pos =
							parser.inlineParser.parseReference(block._string_content,
									parser.refmap)) != 0) {
						block._string_content = block._string_content.substring(pos);
						hasReferenceDefs = true;
					}
					if (hasReferenceDefs && isBlank(block._string_content)) {
						block.unlink();
					}
				}
				@Override
				public boolean canContain(CMarkNodeType t) {
					return false;
				}
				@Override
				public boolean acceptsLines() {
					return true;
				}
			});
		}
	};

	// block start functions.  Return values:
	// 0 = no match
	// 1 = matched container, keep going
	// 2 = matched leaf, no more block starts
	static final BlockStart[] blockStarts = new BlockStart[] {
			// block quote
			(parser, container) -> {
				if (!parser.indented &&
						peek(parser.currentLine, parser.nextNonspace) == C_GREATERTHAN) {
					parser.advanceNextNonspace();
					parser.advanceOffset(1, false);
					// optional following space
					if (peek(parser.currentLine, parser.offset) == C_SPACE) {
						parser.advanceOffset(1, false);
					}
					parser.closeUnmatchedBlocks();
					parser.addChild(CMarkNodeType.BLOCK_QUOTE, parser.nextNonspace);
					return 1;
				} else {
					return 0;
				}
			},

			// ATX header
			(parser, container) -> {
				Matcher match;
				if (!parser.indented &&
						(match = reATXHeaderMarker.matcher(parser.currentLine.substring(parser.nextNonspace))).find()) {
					parser.advanceNextNonspace();
					parser.advanceOffset(match.group().length(), false);
					parser.closeUnmatchedBlocks();
					Node container2 = parser.addChild(CMarkNodeType.HEADER, parser.nextNonspace);
					container2.level(match.group().trim().length()); // number of #s
					// remove trailing ###s:
					container2._string_content =
							parser.currentLine.substring(parser.offset).replaceFirst("^ *#+ *$", "").replaceFirst(" +#+ *$", "");
					parser.advanceOffset(parser.currentLine.length() - parser.offset, false);
					return 2;
				} else {
					return 0;
				}
			},

			// Fenced code block
			(parser, container) -> {
				Matcher match;
				if (!parser.indented &&
						(match = reCodeFence.matcher(parser.currentLine.substring(parser.nextNonspace))).find()) {
					int fenceLength = match.group().length();
					parser.closeUnmatchedBlocks();
					Node container2 = parser.addChild(CMarkNodeType.CODE_BLOCK, parser.nextNonspace);
					container2._isFenced = true;
					container2._fenceLength = fenceLength;
					container2._fenceChar = match.group().charAt(0);
					container2._fenceOffset = parser.indent;
					parser.advanceNextNonspace();
					parser.advanceOffset(fenceLength, false);
					return 2;
				} else {
					return 0;
				}
			},

			// HTML block
			(parser, container) -> {
				if (!parser.indented &&
						peek(parser.currentLine, parser.nextNonspace) == C_LESSTHAN) {
					String s = parser.currentLine.substring(parser.nextNonspace);
					int blockType;

					for (blockType = 1; blockType <= 7; blockType++) {
						if (reHtmlBlockOpen[blockType].matcher(s).find() &&
								(blockType < 7 ||
										container.type() != CMarkNodeType.PARAGRAPH)) {
							parser.closeUnmatchedBlocks();
							// We don't adjust parser.offset;
							// spaces are part of the HTML block:
							Node b = parser.addChild(CMarkNodeType.HTML_BLOCK,
									parser.offset);
							b._htmlBlockType = blockType;
							return 2;
						}
					}
				}

				return 0;

			},

			// Setext header
			(parser, container) -> {
				Matcher match;
				if (!parser.indented &&
						container.type() == CMarkNodeType.PARAGRAPH &&
						(container._string_content.indexOf('\n') ==
						container._string_content.length() - 1) &&
						((match = reSetextHeaderLine.matcher(parser.currentLine.substring(parser.nextNonspace))).find())) {
					parser.closeUnmatchedBlocks();
					Node header = new Node(CMarkNodeType.HEADER, container.sourcepos());
					header.level(match.group().charAt(0) == '=' ? 1 : 2);
					header._string_content = container._string_content;
					container.insertAfter(header);
					container.unlink();
					parser.tip = header;
					parser.advanceOffset(parser.currentLine.length() - parser.offset, false);
					return 2;
				} else {
					return 0;
				}
			},

			// hrule
			(parser, container) -> {
				if (!parser.indented &&
						reHrule.matcher(parser.currentLine.substring(parser.nextNonspace)).find()) {
					parser.closeUnmatchedBlocks();
					parser.addChild(CMarkNodeType.HORIZONTAL_RULE, parser.nextNonspace);
					parser.advanceOffset(parser.currentLine.length() - parser.offset, false);
					return 2;
				} else {
					return 0;
				}
			},

			// list item
			(parser, container) -> {
				ListData data;
				int i;
				if ((data = parseListMarker(parser.currentLine,
						parser.nextNonspace, parser.indent)) != null) {
					parser.closeUnmatchedBlocks();
					if (parser.indented && parser.tip.type() != CMarkNodeType.LIST) {
						return 0;
					}
					parser.advanceNextNonspace();
					// recalculate data.padding, taking into account tabs:
					i = parser.column;
					parser.advanceOffset(data.padding, false);
					data.padding = parser.column - i;

					// add the list if needed
					if (parser.tip.type() != CMarkNodeType.LIST ||
							!(listsMatch(container._listData, data))) {
						container = parser.addChild(CMarkNodeType.LIST, parser.nextNonspace);
						container._listData = data;
					}

					// add the list item
					container = parser.addChild(CMarkNodeType.ITEM, parser.nextNonspace);
					container._listData = data;
					return 1;
				} else {
					return 0;
				}
			},

			// indented code block
			(parser, container) -> {
				if (parser.indented &&
						parser.tip.type() != CMarkNodeType.PARAGRAPH &&
						!parser.blank) {
					// indented code
					parser.advanceOffset(CODE_INDENT, true);
					parser.closeUnmatchedBlocks();
					parser.addChild(CMarkNodeType.CODE_BLOCK, parser.offset);
					return 2;
				} else {
					return 0;
				}
			}
	};

	void advanceOffset(int count, boolean columns) {
		int i = 0;
		int cols = 0;
		String currentLine = this.currentLine;
		while (columns ? (cols < count) : (i < count)) {
			if (this.offset + i < currentLine.length() && currentLine.charAt(this.offset + i) == '\t') {
				cols += (4 - (this.column % 4));
			} else {
				cols += 1;
			}
			i++;
		}
		this.offset += i;
		this.column += cols;
	}

	void advanceNextNonspace() {
		this.offset = this.nextNonspace;
		this.column = this.nextNonspaceColumn;
	}

	void findNextNonspace() {
		String currentLine = this.currentLine;
		int i = this.offset;
		int cols = this.column;
		char c = '\0';

		while (i < currentLine.length()) {
			c = currentLine.charAt(i);
			if (c == ' ') {
				i++;
				cols++;
			} else if (c == '\t') {
				i++;
				cols += (4 - (cols % 4));
			} else {
				break;
			}
		}

		this.blank = (c == '\n' || c == '\r' || i >= currentLine.length());
		this.nextNonspace = i;
		this.nextNonspaceColumn = cols;
		this.indent = this.nextNonspaceColumn - this.column;
		this.indented = this.indent >= CODE_INDENT;
	}

	// Analyze a line of text and update the document appropriately.
	// We parse markdown text by calling this on each line of input,
	// then finalizing the document.
	void incorporateLine(String ln) {
		boolean all_matched = true;
		CMarkNodeType t;

		Node container = this.doc;
		this.oldtip = this.tip;
		this.offset = 0;
		this.lineNumber += 1;

		// replace NUL characters for security
		if (ln.indexOf('\u0000') != -1) {
			ln = ln.replaceAll("\\0", "\uFFFD");
		}

		this.currentLine = ln;

		// For each containing block, try to parse the associated line start.
		// Bail out on failure: container will point to the last matching block.
		// Set all_matched to false if not all containers match.
		Node lastChild;
		while ((lastChild = container._lastChild) != null && lastChild._open) {
			container = lastChild;

			this.findNextNonspace();

			switch (blocks.get(container.type()).continue_(this, container)) {
			case 0: // we've matched, keep going
				break;
			case 1: // we've failed to match a block
				all_matched = false;
				break;
			case 2: // we've hit end of line for fenced code close and can return
				this.lastLineLength = ln.length();
				return;
			default:
				throw new IllegalStateException("continue returned illegal value, must be 0, 1, or 2");
			}
			if (!all_matched) {
				container = container._parent; // back up to last matching block
				break;
			}
		}

		this.allClosed = (container == this.oldtip);
		this.lastMatchedContainer = container;

		// Check to see if we've hit 2nd blank line; if so break out of list:
		if (this.blank && container._lastLineBlank) {
			this.breakOutOfLists(container);
		}

		boolean matchedLeaf = container.type() != CMarkNodeType.PARAGRAPH &&
				blocks.get(container.type()).acceptsLines();
		BlockStart[] starts = blockStarts;
		int startsLen = starts.length;
		// Unless last matched container is a code block, try new container starts,
		// adding children to the last matched container:
		while (!matchedLeaf) {

			this.findNextNonspace();

			// this is a little performance optimization:
			if (!this.indented &&
					!reMaybeSpecial.matcher(this.nextNonspace < ln.length() ? ln.substring(this.nextNonspace) : "").find()) {
				this.advanceNextNonspace();
				break;
			}

			int i = 0;
			while (i < startsLen) {
				int res = starts[i].process(this, container);
				if (res == 1) {
					container = this.tip;
					break;
				} else if (res == 2) {
					container = this.tip;
					matchedLeaf = true;
					break;
				} else {
					i++;
				}
			}

			if (i == startsLen) { // nothing matched
				this.advanceNextNonspace();
				break;
			}
		}

		// What remains at the offset is a text line.  Add the text to the
		// appropriate container.

		// First check for a lazy paragraph continuation:
		if (!this.allClosed && !this.blank &&
				this.tip.type() == CMarkNodeType.PARAGRAPH) {
			// lazy paragraph continuation
			this.addLine();

		} else { // not a lazy continuation

			// finalize any blocks not matched
			this.closeUnmatchedBlocks();
			if (this.blank && container.lastChild() != null) {
				container.lastChild()._lastLineBlank = true;
			}

			t = container.type();

			// Block quote lines are never blank as they start with >
			// and we don't count blanks in fenced code for purposes of tight/loose
			// lists or breaking out of lists.  We also don't set _lastLineBlank
			// on an empty list item, or if we just closed a fenced block.
			boolean lastLineBlank = this.blank &&
					!(t == CMarkNodeType.BLOCK_QUOTE ||
					(t == CMarkNodeType.CODE_BLOCK && container._isFenced) ||
					(t == CMarkNodeType.ITEM &&
					container._firstChild == null &&
					container.sourcepos()[0][0] == this.lineNumber));

			// propagate lastLineBlank up through parents:
			Node cont = container;
			while (cont != null) {
				cont._lastLineBlank = lastLineBlank;
				cont = cont._parent;
			}

			if (blocks.get(t).acceptsLines()) {
				this.addLine();
				// if HtmlBlock, check for end condition
				if (t == CMarkNodeType.HTML_BLOCK &&
						container._htmlBlockType >= 1 &&
						container._htmlBlockType <= 5 &&
						reHtmlBlockClose[container._htmlBlockType].matcher(this.currentLine.substring(this.offset)).find()) {
					this.finalize(container, this.lineNumber);
				}

			} else if (this.offset < ln.length() && !this.blank) {
				// create paragraph container for line
				container = this.addChild(CMarkNodeType.PARAGRAPH, this.offset);
				this.advanceNextNonspace();
				this.addLine();
			}
		}
		this.lastLineLength = ln.length();
	}

	// Finalize a block.  Close it and do any necessary postprocessing,
	// e.g. creating string_content from strings, setting the 'tight'
	// or 'loose' status of a list, and parsing the beginnings
	// of paragraphs for reference definitions.  Reset the tip to the
	// parent of the closed block.
	void finalize(Node block, int lineNumber) {
		Node above = block._parent;
		block._open = false;
		block.sourcepos()[1] = new int[] { lineNumber, this.lastLineLength };

		blocks.get(block.type()).finalize(this, block);

		this.tip = above;
	}

	// Walk through a block & children recursively, parsing string content
	// into inline content where appropriate.
	void processInlines(Node block) {
		Node node; Event event; CMarkNodeType t;
		NodeWalker walker = block.walker();
		this.inlineParser.refmap = this.refmap;
		this.inlineParser.options = this.options;
		while ((event = walker.next()) != null) {
			node = event.node;
			t = node.type();
			if (!event.entering && (t == CMarkNodeType.PARAGRAPH || t == CMarkNodeType.HEADER)) {
				this.inlineParser.parse(node);
			}
		}
	}

	private static class Document extends Node {
		public Document() {
			super(CMarkNodeType.DOCUMENT, new int[][] {{1, 1}, {0, 0}});
		}
	}

	public Node parse(BufferedReader reader) throws IOException {
		this.doc = new Document();
		this.tip = this.doc;
		this.refmap = new HashMap<>();
		this.lineNumber = 0;
		this.lastLineLength = 0;
		this.offset = 0;
		this.column = 0;
		this.lastMatchedContainer = this.doc;
		this.currentLine = "";
		long time = 0L;
		if (this.options.time) { time = System.currentTimeMillis(); }

		String line = null;
		int len = 0;
		while ((line = reader.readLine()) != null) {
			this.incorporateLine(line);
			len++;
		}
		while (this.tip != null) {
			this.finalize(this.tip, len);
		}
		if (this.options.time) { System.out.println("block parsing: " + ((System.currentTimeMillis() - time) / 1000.0) + "s"); }
		if (this.options.time) { time = System.currentTimeMillis(); }
		this.processInlines(this.doc);
		if (this.options.time) { System.out.println("inline parsing: " + ((System.currentTimeMillis() - time) / 1000.0) + "s"); }
		return this.doc;
	}

	Node doc = new Document();
	Node tip = this.doc;
	Node oldtip = this.doc;
	String currentLine = "";
	int lineNumber = 0;
	int offset = 0;
	int column = 0;
	int nextNonspace = 0;
	int nextNonspaceColumn = 0;
	int indent = 0;
	boolean indented = false;
	boolean blank = false;
	boolean allClosed = true;
	Node lastMatchedContainer = this.doc;
	Map<String, Ref> refmap = new HashMap<>();
	int lastLineLength = 0;
	InlineParser inlineParser;
	Options options;

	public Parser() {
		this(null);
	}

	public Parser(Options options) {
		this.options = (options != null) ? options : new Options();
		this.inlineParser = new InlineParser(options);
	}

	public static class Options {
		boolean smart;
		boolean time;

		public Options smart(boolean flag) {
			smart = flag;
			return this;
		}

		public Options time(boolean flag) {
			time = flag;
			return this;
		}
	}

	static interface Block {
		public int continue_(Parser parser, Node container);

		public void finalize(Parser parser, Node block);

		public boolean canContain(CMarkNodeType t);

		public boolean acceptsLines();
	}

	static interface BlockStart {
		int process(Parser parser, Node container);
	}

}
