/**
 * Copyright (c) 2015, Hidekatsu Izuno <hidekatsu.izuno@gmail.com>
 *
 * This software is released under the 2 clause BSD License, see LICENSE.
 */
package net.arnx.commonmark4j;

import java.io.BufferedReader;
import java.io.IOException;

import net.arnx.commonmark4j.impl.Parser;

/**
 * Defines the requirements for an object that can be used as a CommonMark parser.
 *
 * @author Hidekatsu Izuno
 */
public interface CMarkParser {
	/**
	 * Creates a default CommonMark parser.
	 *
	 * @return a new CommonMark parser instance.
	 */
	public static CMarkParser newParser() {
		return new Parser();
	}

	/**
	 * Parse a CommonMark text.
	 *
	 * @param reader a source.
	 * @return a root node.
	 * @throws IOException an I/O error occures.
	 */
	public CMarkNode parse(BufferedReader reader) throws IOException;
}
