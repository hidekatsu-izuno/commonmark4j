/**
 * Copyright (c) 2015, Hidekatsu Izuno <hidekatsu.izuno@gmail.com>
 *
 * This software is released under the 2 clause BSD License, see LICENSE.txt.
 */
package net.arnx.commonmark4j.impl;

import static net.arnx.commonmark4j.impl.Common.*;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.arnx.commonmark4j.CMarkNodeType;

class InlineParser {
	private static final int C_NEWLINE = 10;
	private static final int C_ASTERISK = 42;
	private static final int C_UNDERSCORE = 95;
	private static final int C_BACKTICK = 96;
	private static final int C_OPEN_BRACKET = 91;
	private static final int C_CLOSE_BRACKET = 93;
	private static final int C_LESSTHAN = 60;
	private static final int C_BANG = 33;
	private static final int C_BACKSLASH = 92;
	private static final int C_AMPERSAND = 38;
	private static final int C_OPEN_PAREN = 40;
	private static final int C_CLOSE_PAREN = 41;
	private static final int C_COLON = 58;
	private static final int C_SINGLEQUOTE = 39;
	private static final int C_DOUBLEQUOTE = 34;

	private static final String ESCAPABLE = Common.ESCAPABLE;
	private static final String ESCAPED_CHAR = "\\\\" + ESCAPABLE;
	private static final String REG_CHAR = "[^\\\\()\\x00-\\x20]";
	private static final String IN_PARENS_NOSP = "\\((" + REG_CHAR + '|' + ESCAPED_CHAR + "|\\\\)*\\)";

	private static final String ENTITY = Common.ENTITY;
	private static final Pattern reHtmlTag = Common.reHtmlTag;

	private static final Pattern rePunctuation = Pattern.compile("^[\\u2000-\\u206F\\u2E00-\\u2E7F\\\\'!\"#$%&()*+,./:;<=>?@\\[\\]^_`{|}~-]");

	private static final Pattern reLinkTitle = Pattern.compile(
			"^(?:\"(" + ESCAPED_CHAR + "|[^\"\\x00])*\"" +
					"|" +
					"'(" + ESCAPED_CHAR + "|[^'\\x00])*'" +
					"|" +
					"\\((" + ESCAPED_CHAR + "|[^)\\x00])*\\))");

	private static final Pattern reLinkDestinationBraces = Pattern.compile(
			"^(?:[<](?:[^<>\\n\\\\\\x00]|" + ESCAPED_CHAR + "|\\\\)*[>])");

	private static final Pattern reLinkDestination = Pattern.compile(
			"^(?:" + REG_CHAR + "+|" + ESCAPED_CHAR + "|\\\\|" + IN_PARENS_NOSP + ")*");

	private static final Pattern reEscapable = Pattern.compile("^" + ESCAPABLE);

	private static final Pattern reEntityHere = Pattern.compile("^" + ENTITY, Pattern.CASE_INSENSITIVE);

	private static final Pattern reTicks = Pattern.compile("`+");

	private static final Pattern reTicksHere = Pattern.compile("^`+");

	private static final Pattern reEllipses = Pattern.compile("\\.\\.\\.");

	private static final Pattern reDash = Pattern.compile("--+");

	private static final Pattern reEmailAutolink = Pattern.compile("^<([a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*)>");

	private static final Pattern reAutolink = Pattern.compile("^<(?:coap|doi|javascript|aaa|aaas|about|acap|cap|cid|crid|data|dav|dict|dns|file|ftp|geo|go|gopher|h323|http|https|iax|icap|im|imap|info|ipp|iris|iris.beep|iris.xpc|iris.xpcs|iris.lwz|ldap|mailto|mid|msrp|msrps|mtqp|mupdate|news|nfs|ni|nih|nntp|opaquelocktoken|pop|pres|rtsp|service|session|shttp|sieve|sip|sips|sms|snmp|soap.beep|soap.beeps|tag|tel|telnet|tftp|thismessage|tn3270|tip|tv|urn|vemmi|ws|wss|xcon|xcon-userid|xmlrpc.beep|xmlrpc.beeps|xmpp|z39.50r|z39.50s|adiumxtra|afp|afs|aim|apt|attachment|aw|beshare|bitcoin|bolo|callto|chrome|chrome-extension|com-eventbrite-attendee|content|cvs|dlna-playsingle|dlna-playcontainer|dtn|dvb|ed2k|facetime|feed|finger|fish|gg|git|gizmoproject|gtalk|hcp|icon|ipn|irc|irc6|ircs|itms|jar|jms|keyparc|lastfm|ldaps|magnet|maps|market|message|mms|ms-help|msnim|mumble|mvn|notes|oid|palm|paparazzi|platform|proxy|psyc|query|res|resource|rmi|rsync|rtmp|secondlife|sftp|sgn|skype|smb|soldat|spotify|ssh|steam|svn|teamspeak|things|udp|unreal|ut2004|ventrilo|view-source|webcal|wtai|wyciwyg|xfire|xri|ymsgr):[^<>\\x00-\\x20]*>", Pattern.CASE_INSENSITIVE);

	private static final Pattern reSpnl = Pattern.compile("^ *(?:\\n *)?");

	private static final Pattern reWhitespaceChar = Pattern.compile("^" + SPACE);

	private static final Pattern reWhitespace = Pattern.compile(SPACE + "+");

	private static final Pattern reFinalSpace = Pattern.compile(" *$");

	private static final Pattern reInitialSpace = Pattern.compile("^ *");

	private static final Pattern reSpaceAtEndOfLine = Pattern.compile("^ *(?:\\n|$)");

	private static final Pattern reLinkLabel = Pattern.compile("^\\[(?:[^\\\\\\[\\]]|" + ESCAPED_CHAR + "|\\\\){0,1000}\\]");

	// Matches a string of non-special characters.
	private static final Pattern reMain = Pattern.compile("^[^\\n`\\[\\]\\\\!<&*_'\"]+" /*m*/);

	private static Node text(String s) {
		Node node = new Node(CMarkNodeType.TEXT, (int[][])null);
		node._literal = s;
		return node;
	}

	// INLINE PARSER

	// These are methods of an InlineParser object, defined below.
	// An InlineParser keeps track of a subject (a string to be
	// parsed) and a position in that subject.

	// If re matches at current position in the subject, advance
	// position in subject and return the match; otherwise return null.
	String match(Pattern re) {
		Matcher m = re.matcher(this.subject.substring(pos));
		if (!m.find()) {
			return null;
		} else {
			this.pos += m.start() + m.group().length();
			return m.group();
		}
	}

	// Returns the code for the character at the current subject position, or -1
	// there are no more characters.
	int peek() {
		if (this.pos < this.subject.length()) {
			return this.subject.charAt(this.pos);
		} else {
			return -1;
		}
	}

	// Parse zero or more space characters, including at most one newline
	boolean spnl() {
		this.match(reSpnl);
		return true;
	}

	// All of the parsers below try to match something at the current position
	// in the subject.  If they succeed in matching anything, they
	// return the inline matched, advancing the subject.

	// Attempt to parse backticks, adding either a backtick code span or a
	// literal sequence of backticks.
	boolean parseBackticks(Node block) {
		String ticks = this.match(reTicksHere);
		if (ticks == null) {
			return false;
		}
		int afterOpenTicks = this.pos;
		String matched;
		Node node;
		while ((matched = this.match(reTicks)) != null) {
			if (matched.equals(ticks)) {
				node = new Node(CMarkNodeType.CODE, null);
				node._literal = Common.replace(this.subject.substring(afterOpenTicks,
						this.pos - ticks.length())
						.trim(), reWhitespace, " ");
				block.appendChild(node);
				return true;
			}
		}
		// If we got here, we didn't match a closing backtick sequence.
		this.pos = afterOpenTicks;
		block.appendChild(text(ticks));
		return true;
	}

	// Parse a backslash-escaped special character, adding either the escaped
	// character, a hard line break (if the backslash is followed by a newline),
	// or a literal backslash to the block's children.  Assumes current character
	// is a backslash.
	boolean parseBackslash(Node block) {
		String subj = this.subject;
		Node node;
		this.pos += 1;
		if (this.peek() == C_NEWLINE) {
			this.pos += 1;
			node = new Node(CMarkNodeType.HARDBREAK, null);
			block.appendChild(node);
		} else if (this.pos < subj.length() && reEscapable.matcher(subj.substring(this.pos, this.pos+1)).find()) {
			block.appendChild(text(subj.substring(this.pos, this.pos+1)));
			this.pos += 1;
		} else {
			block.appendChild(text("\\"));
		}
		return true;
	}

	// Attempt to parse an autolink (URL or email in pointy brackets).
	boolean parseAutolink(Node block) {
		String m;
		String dest;
		Node node;
		if ((m = this.match(reEmailAutolink)) != null) {
			dest = m.substring(1, m.length() - 1);
			node = new Node(CMarkNodeType.LINK, null);
			node._destination = normalizeURI("mailto:" + dest);
			node._title = "";
			node.appendChild(text(dest));
			block.appendChild(node);
			return true;
		} else if ((m = this.match(reAutolink)) != null) {
			dest = m.substring(1, m.length() - 1);
			node = new Node(CMarkNodeType.LINK, null);
			node._destination = normalizeURI(dest);
			node._title = "";
			node.appendChild(text(dest));
			block.appendChild(node);
			return true;
		} else {
			return false;
		}
	}

	// Attempt to parse a raw HTML tag.
	boolean parseHtmlTag(Node block) {
		String m = this.match(reHtmlTag);
		if (m == null) {
			return false;
		} else {
			Node node = new Node(CMarkNodeType.HTML, null);
			node._literal = m;
			block.appendChild(node);
			return true;
		}
	}

	// Scan a sequence of characters with code cc, and return information about
	// the number of delimiters and whether they are positioned such that
	// they can open and/or close emphasis or strong emphasis.  A utility
	// function for strong/emph parsing.
	Delimiters scanDelims(int cc) {
		int numdelims = 0;
		String char_before, char_after; int cc_after;
		int startpos = this.pos;
		boolean left_flanking, right_flanking, can_open, can_close;
		boolean after_is_whitespace, after_is_punctuation, before_is_whitespace, before_is_punctuation;

		if (cc == C_SINGLEQUOTE || cc == C_DOUBLEQUOTE) {
			numdelims++;
			this.pos++;
		} else {
			while (this.peek() == cc) {
				numdelims++;
				this.pos++;
			}
		}

		if (numdelims == 0) {
			return null;
		}

		char_before = startpos == 0 ? "\n" : this.subject.substring(startpos - 1, startpos);

		cc_after = this.peek();
		if (cc_after == -1) {
			char_after = "\n";
		} else {
			char_after = fromCodePoint(cc_after);
		}

		after_is_whitespace = reWhitespaceChar.matcher(char_after).find();
		after_is_punctuation = rePunctuation.matcher(char_after).find();
		before_is_whitespace = reWhitespaceChar.matcher(char_before).find();
		before_is_punctuation = rePunctuation.matcher(char_before).find();

		left_flanking = !after_is_whitespace &&
				!(after_is_punctuation && !before_is_whitespace && !before_is_punctuation);
		right_flanking = !before_is_whitespace &&
				!(before_is_punctuation && !after_is_whitespace && !after_is_punctuation);
		if (cc == C_UNDERSCORE) {
			can_open = left_flanking &&
					(!right_flanking || before_is_punctuation);
			can_close = right_flanking &&
					(!left_flanking || after_is_punctuation);
		} else {
			can_open = left_flanking;
			can_close = right_flanking;
		}
		this.pos = startpos;
		return new Delimiters(-1,
				numdelims,
				null,
				null,
				null,
				can_open,
				can_close,
				-1,
				false);
	}

	// Handle a delimiter marker for emphasis or a quote.
	boolean handleDelim(int cc, Node block) {
		Delimiters res = this.scanDelims(cc);
		if (res == null) {
			return false;
		}
		int numdelims = res.numdelims;
		int startpos = this.pos;
		String contents;

		this.pos += numdelims;
		if (cc == C_SINGLEQUOTE) {
			contents = "\u2019";
		} else if (cc == C_DOUBLEQUOTE) {
			contents = "\u201C";
		} else {
			contents = this.subject.substring(startpos, this.pos);
		}
		Node node = text(contents);
		block.appendChild(node);

		// Add entry to stack for this opener
		this.delimiters = new Delimiters(cc,
				numdelims,
				node,
				this.delimiters,
				null,
				res.can_open,
				res.can_close,
				-1,
				true);
		if (this.delimiters.previous != null) {
			this.delimiters.previous.next = this.delimiters;
		}

		return true;

	}

	void removeDelimiter(Delimiters delim) {
		if (delim.previous != null) {
			delim.previous.next = delim.next;
		}
		if (delim.next == null) {
			// top of stack
			this.delimiters = delim.previous;
		} else {
			delim.next.previous = delim.previous;
		}
	}

	void removeDelimitersBetween(Delimiters bottom, Delimiters top) {
		if (bottom.next != top) {
			bottom.next = top;
			top.previous = bottom;
		}
	}

	void processEmphasis(Delimiters stack_bottom) {
		Delimiters opener, closer, old_closer;
		Node opener_inl, closer_inl;
		Delimiters tempstack;
		int use_delims;
		Node tmp, next;
		boolean opener_found;
		Map<Integer, Delimiters> openers_bottom = new HashMap<>();

		openers_bottom.put(C_UNDERSCORE, stack_bottom);
		openers_bottom.put(C_ASTERISK, stack_bottom);
		openers_bottom.put(C_SINGLEQUOTE, stack_bottom);
		openers_bottom.put(C_DOUBLEQUOTE, stack_bottom);

		// find first closer above stack_bottom:
		closer = this.delimiters;
		while (closer != null && closer.previous != stack_bottom) {
			closer = closer.previous;
		}
		// move forward, looking for closers, and handling each
		while (closer != null) {
			int closercc = closer.cc;
			if (!(closer.can_close && (closercc == C_UNDERSCORE ||
					closercc == C_ASTERISK ||
					closercc == C_SINGLEQUOTE ||
					closercc == C_DOUBLEQUOTE))) {
				closer = closer.next;
			} else {
				// found emphasis closer. now look back for first matching opener:
				opener = closer.previous;
				opener_found = false;
				while (opener != null && opener != stack_bottom &&
						opener != openers_bottom.get(closercc)) {
					if (opener.cc == closer.cc && opener.can_open) {
						opener_found = true;
						break;
					}
					opener = opener.previous;
				}
				old_closer = closer;

				if (closercc == C_ASTERISK || closercc == C_UNDERSCORE) {
					if (!opener_found) {
						closer = closer.next;
					} else {
						// calculate actual number of delimiters used from closer
						if (closer.numdelims < 3 || opener.numdelims < 3) {
							use_delims = closer.numdelims <= opener.numdelims ?
									closer.numdelims : opener.numdelims;
						} else {
							use_delims = closer.numdelims % 2 == 0 ? 2 : 1;
						}

						opener_inl = opener.node;
						closer_inl = closer.node;

						// remove used delimiters from stack elts and inlines
						opener.numdelims -= use_delims;
						closer.numdelims -= use_delims;
						opener_inl._literal =
								opener_inl._literal.substring(0,
										opener_inl._literal.length() - use_delims);
						closer_inl._literal =
								closer_inl._literal.substring(0,
										closer_inl._literal.length() - use_delims);

						// build contents for new emph element
						Node emph = new Node(use_delims == 1 ? CMarkNodeType.EMPH : CMarkNodeType.STRONG, null);

						tmp = opener_inl._next;
						while (tmp != null && tmp != closer_inl) {
							next = tmp._next;
							tmp.unlink();
							emph.appendChild(tmp);
							tmp = next;
						}

						opener_inl.insertAfter(emph);

						// remove elts between opener and closer in delimiters stack
						removeDelimitersBetween(opener, closer);

						// if opener has 0 delims, remove it and the inline
						if (opener.numdelims == 0) {
							opener_inl.unlink();
							this.removeDelimiter(opener);
						}

						if (closer.numdelims == 0) {
							closer_inl.unlink();
							tempstack = closer.next;
							this.removeDelimiter(closer);
							closer = tempstack;
						}

					}

				} else if (closercc == C_SINGLEQUOTE) {
					closer.node._literal = "\u2019";
					if (opener_found) {
						opener.node._literal = "\u2018";
					}
					closer = closer.next;

				} else if (closercc == C_DOUBLEQUOTE) {
					closer.node._literal = "\u201D";
					if (opener_found) {
						opener.node.literal("\u201C");
					}
					closer = closer.next;

				}
				if (!opener_found) {
					// Set lower bound for future searches for openers:
					openers_bottom.put(closercc, old_closer.previous);
					if (!old_closer.can_open) {
						// We can remove a closer that can't be an opener,
						// once we've seen there's no matching opener:
						this.removeDelimiter(old_closer);
					}
				}
			}

		}

		// remove all delimiters
		while (this.delimiters != null && this.delimiters != stack_bottom) {
			this.removeDelimiter(this.delimiters);
		}
	}

	// Attempt to parse link title (sans quotes), returning the string
	// or null if no match.
	String parseLinkTitle() {
		String title = this.match(reLinkTitle);
		if (title == null) {
			return null;
		} else {
			// chop off quotes from title and unescape:
			return unescapeString(title.substring(1, 1 + title.length() - 2));
		}
	}

	// Attempt to parse link destination, returning the string or
	// null if no match.
	String parseLinkDestination() {
		String res = this.match(reLinkDestinationBraces);
		if (res == null) {
			res = this.match(reLinkDestination);
			if (res == null) {
				return null;
			} else {
				return normalizeURI(unescapeString(res));
			}
		} else {  // chop off surrounding <..>:
			return normalizeURI(unescapeString(res.substring(1, 1 + res.length() - 2)));
		}
	}

	// Attempt to parse a link label, returning number of characters parsed.
	int parseLinkLabel() {
		String m = this.match(reLinkLabel);
		if (m == null || m.length() > 1001) {
			return 0;
		} else {
			return m.length();
		}
	}

	// Add open bracket to delimiter stack and add a text node to block's children.
	boolean parseOpenBracket(Node block) {
		int startpos = this.pos;
		this.pos += 1;

		Node node = text("[");
		block.appendChild(node);

		// Add entry to stack for this opener
		this.delimiters = new Delimiters(C_OPEN_BRACKET,
				1,
				node,
				this.delimiters,
				null,
				true,
				false,
				startpos,
				true);
		if (this.delimiters.previous != null) {
			this.delimiters.previous.next = this.delimiters;
		}

		return true;

	}

	// IF next character is [, and ! delimiter to delimiter stack and
	// add a text node to block's children.  Otherwise just add a text node.
	boolean parseBang(Node block) {
		int startpos = this.pos;
		this.pos += 1;
		if (this.peek() == C_OPEN_BRACKET) {
			this.pos += 1;

			Node node = text("![");
			block.appendChild(node);

			// Add entry to stack for this opener
			this.delimiters = new Delimiters(C_BANG,
					1,
					node,
					this.delimiters,
					null,
					true,
					false,
					startpos + 1,
					true);
			if (this.delimiters.previous != null) {
				this.delimiters.previous.next = this.delimiters;
			}
		} else {
			block.appendChild(text("!"));
		}
		return true;
	}

	// Try to match close bracket against an opening in the delimiter
	// stack.  Add either a link or image, or a plain [ character,
	// to block's children.  If there is a matching delimiter,
	// remove it from the delimiter stack.
	boolean parseCloseBracket(Node block) {
		int startpos;
		boolean is_image;
		String dest = null;
		String title = null;
		boolean matched = false;
		String reflabel;
		Delimiters opener;

		this.pos += 1;
		startpos = this.pos;

		// look through stack of delimiters for a [ or ![
		opener = this.delimiters;

		while (opener != null) {
			if (opener.cc == C_OPEN_BRACKET || opener.cc == C_BANG) {
				break;
			}
			opener = opener.previous;
		}

		if (opener == null) {
			// no matched opener, just return a literal
			block.appendChild(text("]"));
			return true;
		}

		if (!opener.active) {
			// no matched opener, just return a literal
			block.appendChild(text("]"));
			// take opener off emphasis stack
			this.removeDelimiter(opener);
			return true;
		}

		// If we got here, open is a potential opener
		is_image = opener.cc == C_BANG;

		// Check to see if we have a link/image

		// Inline link?
		if (this.peek() == C_OPEN_PAREN) {
			this.pos++;
			if (this.spnl() &&
					((dest = this.parseLinkDestination()) != null) &&
					this.spnl() &&
					// make sure there's a space before the title:
					(reWhitespaceChar.matcher(this.subject.substring(this.pos - 1, this.pos)).find() &&
							(title = this.parseLinkTitle()) != null || true) &&
					this.spnl() &&
					this.peek() == C_CLOSE_PAREN) {
				this.pos += 1;
				matched = true;
			}
		} else {

			// Next, see if there's a link label
			int savepos = this.pos;
			this.spnl();
			int beforelabel = this.pos;
			int n = this.parseLinkLabel();
			if (n == 0 || n == 2) {
				// empty or missing second label
				reflabel = this.subject.substring(opener.index, startpos);
			} else {
				reflabel = this.subject.substring(beforelabel, beforelabel + n);
			}
			if (n == 0) {
				// If shortcut reference link, rewind before spaces we skipped.
				this.pos = savepos;
			}

			// lookup rawlabel in refmap
			Ref link = this.refmap.get(normalizeReference(reflabel));
			if (link != null) {
				dest = link.destination;
				title = link.title;
				matched = true;
			}
		}

		if (matched) {
			Node node = new Node(is_image ? CMarkNodeType.IMAGE : CMarkNodeType.LINK, null);
			node._destination = dest;
			node._title = title != null ? title : "";

			Node tmp, next;
			tmp = opener.node._next;
			while (tmp != null) {
				next = tmp._next;
				tmp.unlink();
				node.appendChild(tmp);
				tmp = next;
			}
			block.appendChild(node);
			this.processEmphasis(opener.previous);

			opener.node.unlink();

			// processEmphasis will remove this and later delimiters.
			// Now, for a link, we also deactivate earlier link openers.
			// (no links in links)
			if (!is_image) {
				opener = this.delimiters;
				while (opener != null) {
					if (opener.cc == C_OPEN_BRACKET) {
						opener.active = false; // deactivate this opener
					}
					opener = opener.previous;
				}
			}

			return true;

		} else { // no match

			this.removeDelimiter(opener);  // remove this opener from stack
			this.pos = startpos;
			block.appendChild(text("]"));
			return true;
		}

	}

	// Attempt to parse an entity.
	boolean parseEntity(Node block) {
		String m;
		if ((m = this.match(reEntityHere)) != null) {
			block.appendChild(text(decodeHTML(m)));
			return true;
		} else {
			return false;
		}
	}

	// Parse a run of ordinary characters, or a single character with
	// a special meaning in markdown, as a plain string.
	boolean parseString(Node block) {
		String m;
		if ((m = this.match(reMain)) != null) {
			if (this.options.smart) {
				block.appendChild(text(
						replace(replace(m, reEllipses, "\u2026"),
								reDash, (m2) -> {
									int enCount = 0;
									int emCount = 0;
									if (m2.group().length() % 3 == 0) { // If divisible by 3, use all em dashes
										emCount = m2.group().length() / 3;
									} else if (m2.group().length() % 2 == 0) { // If divisible by 2, use all en dashes
										enCount = m2.group().length() / 2;
									} else if (m2.group().length() % 3 == 2) { // If 2 extra dashes, use en dash for last 2; em dashes for rest
										enCount = 1;
										emCount = (m2.group().length() - 2) / 3;
									} else { // Use en dashes for last 4 hyphens; em dashes for rest
										enCount = 2;
										emCount = (m2.group().length() - 4) / 3;
									}
									return repeat("\u2014", emCount) + repeat("\u2013", enCount);
								})));
			} else {
				block.appendChild(text(m));
			}
			return true;
		} else {
			return false;
		}
	}

	// Parse a newline.  If it was preceded by two spaces, return a hard
	// line break; otherwise a soft line break.
	boolean parseNewline(Node block) {
		this.pos += 1; // assume we're at a \n
		// check previous node for trailing spaces
		Node lastc = block._lastChild;
		if (lastc != null && lastc.type() == CMarkNodeType.TEXT && lastc._literal.charAt(lastc._literal.length() - 1) == ' ') {
			boolean hardbreak = lastc._literal.charAt(lastc._literal.length() - 2) == ' ';
			lastc._literal = replace(lastc._literal, reFinalSpace, "");
			block.appendChild(new Node(hardbreak ? CMarkNodeType.HARDBREAK : CMarkNodeType.SOFTBREAK, null));
		} else {
			block.appendChild(new Node(CMarkNodeType.SOFTBREAK, null));
		}
		this.match(reInitialSpace); // gobble leading spaces in next line
		return true;
	}

	// Attempt to parse a link reference, modifying refmap.
	int parseReference(String s, Map<String, Ref> refmap) {
		this.subject = s;
		this.pos = 0;
		String rawlabel;
		String dest;
		String title;
		int matchChars;
		int startpos = this.pos;

		// label:
		matchChars = this.parseLinkLabel();
		if (matchChars == 0) {
			return 0;
		} else {
			rawlabel = this.subject.substring(0, matchChars);
		}

		// colon:
		if (this.peek() == C_COLON) {
			this.pos++;
		} else {
			this.pos = startpos;
			return 0;
		}

		//  link url
		this.spnl();

		dest = this.parseLinkDestination();
		if (dest == null || dest.length() == 0) {
			this.pos = startpos;
			return 0;
		}

		int beforetitle = this.pos;
		this.spnl();
		title = this.parseLinkTitle();
		if (title == null) {
			title = "";
			// rewind before spaces
			this.pos = beforetitle;
		}

		// make sure we're at line end:
		boolean atLineEnd = true;
		if (this.match(reSpaceAtEndOfLine) == null) {
			if (title.length() == 0) {
				atLineEnd = false;
			} else {
				// the potential title we found is not at the line end,
				// but it could still be a legal link reference if we
				// discard the title
				title = "";
				// rewind before spaces
				this.pos = beforetitle;
				// and instead check if the link URL is at the line end
				atLineEnd = this.match(reSpaceAtEndOfLine) != null;
			}
		}

		if (!atLineEnd) {
			this.pos = startpos;
			return 0;
		}

		String normlabel = normalizeReference(rawlabel);
		if (normlabel.isEmpty()) {
			// label must contain non-whitespace characters
			this.pos = startpos;
			return 0;
		}

		if (!refmap.containsKey(normlabel)) {
			refmap.put(normlabel, new Ref(dest, title));
		}
		return this.pos - startpos;
	}

	// Parse the next inline element in subject, advancing subject position.
	// On success, add the result to block's children and return true.
	// On failure, return false.
	boolean parseInline(Node block) {
		boolean res = false;
		int c = this.peek();
		if (c == -1) {
			return false;
		}
		switch(c) {
		case C_NEWLINE:
			res = this.parseNewline(block);
			break;
		case C_BACKSLASH:
			res = this.parseBackslash(block);
			break;
		case C_BACKTICK:
			res = this.parseBackticks(block);
			break;
		case C_ASTERISK:
		case C_UNDERSCORE:
			res = this.handleDelim(c, block);
			break;
		case C_SINGLEQUOTE:
		case C_DOUBLEQUOTE:
			res = this.options.smart && this.handleDelim(c, block);
			break;
		case C_OPEN_BRACKET:
			res = this.parseOpenBracket(block);
			break;
		case C_BANG:
			res = this.parseBang(block);
			break;
		case C_CLOSE_BRACKET:
			res = this.parseCloseBracket(block);
			break;
		case C_LESSTHAN:
			res = this.parseAutolink(block) || this.parseHtmlTag(block);
			break;
		case C_AMPERSAND:
			res = this.parseEntity(block);
			break;
		default:
			res = this.parseString(block);
			break;
		}
		if (!res) {
			this.pos += 1;
			block.appendChild(text(fromCodePoint(c)));
		}

		return true;
	}

	// Parse string content in block into inline children,
	// using refmap to resolve references.
	void parseInlines(Node block) {
		this.subject = block._string_content.trim();
		this.pos = 0;
		this.delimiters = null;
		while (this.parseInline(block)) {
		}
		block._string_content = null; // allow raw string to be garbage collected
		this.processEmphasis(null);
	}

	String subject = "";
	Delimiters delimiters;
	int pos = 0;
	Map<String, Ref> refmap = new HashMap<>();
	Parser.Options options;

	// The InlineParser object.
	InlineParser(Parser.Options options) {
		this.options = options;
	}

	public void parse(Node block) {
		parseInlines(block);
	}

	static class Delimiters {
		int cc;
		int numdelims;
		Node node;
		Delimiters previous;
		Delimiters next;
		boolean can_open;
		boolean can_close;
		int index;
		boolean active;

		Delimiters(int cc,
				int numdelims,
				Node node,
				Delimiters previous,
				Delimiters next,
				boolean can_open,
				boolean can_close,
				int index,
				boolean active) {

			this.cc = cc;
			this.numdelims = numdelims;
			this.node = node;
			this.previous = previous;
			this.next = next;
			this.can_open = can_open;
			this.can_close = can_close;
			this.index = index;
			this.active = active;
		}
	}

}
