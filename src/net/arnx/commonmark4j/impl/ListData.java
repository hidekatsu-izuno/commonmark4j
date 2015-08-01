/**
 * Copyright (c) 2015, Hidekatsu Izuno <hidekatsu.izuno@gmail.com>
 *
 * This software is released under the 2 clause BSD License, see LICENSE.txt.
 */
package net.arnx.commonmark4j.impl;

class ListData {
	ListType type;
	Boolean tight;
	char bulletChar;
	Integer start;
	Character delimiter;
	int padding;
	int markerOffset;

	public ListData(ListType type,
			Boolean tight,
			char bulletChar,
			Integer start,
			Character delimiter,
			int padding,
			int markerOffset) {
		this.type = type;
		this.tight = tight;
		this.bulletChar = bulletChar;
		this.start = start;
		this.delimiter = delimiter;
		this.padding = padding;
		this.markerOffset = markerOffset;
	}

}
