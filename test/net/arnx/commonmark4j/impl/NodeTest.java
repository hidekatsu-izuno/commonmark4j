package net.arnx.commonmark4j.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import net.arnx.commonmark4j.CMarkNode;
import net.arnx.commonmark4j.CMarkParser;

public class NodeTest {

	@Test
	public void testIterator() throws IOException {
		Path src = Paths.get("base/spec/spec.txt");

		try (BufferedReader reader = Files.newBufferedReader(src)) {
			CMarkNode node = CMarkParser.newParser().parse(reader);
			for (CMarkNode next : node) {
				System.out.println(next.type());
			}
		}
	}

}
