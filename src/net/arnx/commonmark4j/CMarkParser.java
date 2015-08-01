package net.arnx.commonmark4j;

import java.io.BufferedReader;
import java.io.IOException;

import net.arnx.commonmark4j.impl.Parser;

public interface CMarkParser {
	public static CMarkParser newParser() {
		return new Parser();
	}

	public CMarkNode parse(BufferedReader reader) throws IOException;
}
