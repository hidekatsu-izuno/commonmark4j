/**
 * Copyright (c) 2015, Hidekatsu Izuno <hidekatsu.izuno@gmail.com>
 *
 * This software is released under the 2 clause BSD License, see LICENSE.
 */
package net.arnx.commonmark4j.impl;

class Ref {
	String destination;
	String title;

	Ref(String destination, String title) {
		this.destination = destination;
		this.title = title;
	}
}