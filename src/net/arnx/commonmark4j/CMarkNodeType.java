/**
 * Copyright (c) 2015, Hidekatsu Izuno <hidekatsu.izuno@gmail.com>
 *
 * This software is released under the 2 clause BSD License, see LICENSE.
 */
package net.arnx.commonmark4j;

/**
 * A CommonMark node type.
 *
 * @author Hidekatsu Izuno
 */
public enum CMarkNodeType {
	DOCUMENT,
	PARAGRAPH,
	LIST,
	BLOCK_QUOTE,
	ITEM,
	HEADER,
	CODE_BLOCK,
	HTML_BLOCK,
	HORIZONTAL_RULE,
	TEXT,
	SOFTBREAK,
	HARDBREAK,
	EMPH,
	STRONG,
	HTML,
	LINK,
	IMAGE,
	CODE;
}
