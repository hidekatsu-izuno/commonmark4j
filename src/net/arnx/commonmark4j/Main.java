/**
 * Copyright (c) 2015, Hidekatsu Izuno <hidekatsu.izuno@gmail.com>
 *
 * This software is released under the 2 clause BSD License, see LICENSE.txt.
 */
package net.arnx.commonmark4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import net.arnx.commonmark4j.impl.HtmlRenderer;
import net.arnx.commonmark4j.impl.Parser;
import net.arnx.commonmark4j.impl.XmlRenderer;

public final class Main {
	public static void main(String[] args) throws IOException {
		boolean time = false;
		boolean smart = false;

		boolean safe = false;
		boolean sourcepos = false;
		String softbreak = "\n";

		String type = "html";
		String src = null;
		String dest = null;

		String option = null;
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-?") || args[i].equals("-help")) {
				usage(System.out, null);
				System.exit(0);
			} else if (args[i].equals("-time")) {
				time = true;
			} else if (args[i].equals("-smart")) {
				smart = true;
			} else if (args[i].equals("-safe")) {
				safe = true;
			} else if (args[i].equals("-sourcepos")) {
				sourcepos = true;
			} else if (args[i].startsWith("-")) {
				option = args[i];
			} else if (option != null) {
				if (option.equals("-type")) {
					if (args[i].equals("xml") || args[i].equals("html")) {
						type = args[i];
					} else {
						usage(System.err, "invalid type: " + args[i]);
						System.exit(1);
					}
				} else if (option.equals("-softbreak")) {
					softbreak = args[i];
				} else {
					usage(System.err, "invalid option: " + option);
					System.exit(1);
				}
				option = null;
			} else if (i == args.length - 2) {
				src = args[i];
			} else if (i == args.length - 1) {
				if (src == null) {
					src = args[i];
				} else {
					dest = args[i];
				}
			} else {
				usage(System.err, "unrecognized argument: " + args[i]);
				System.exit(1);
			}
		}

		if (option != null) {
			usage(System.err, "invalid option: " + option);
			System.exit(1);
		}

		CMarkParser parser = new Parser(new Parser.Options()
				.smart(smart)
				.time(time));

		CMarkNode node;
		if (src != null) {
			try (BufferedReader reader = Files.newBufferedReader(Paths.get(src))) {
				node = parser.parse(reader);
			}
		} else {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in) {
				@Override
				public void close() throws IOException {
					// no handle
				}
			})) {
				node = parser.parse(reader);
			}
		}

		CMarkRenderer renderer;
		if (type.equals("xml")) {
			renderer = new XmlRenderer(new XmlRenderer.Options()
					.time(time)
					.safe(safe)
					.sourcepos(sourcepos)
					.softbreak(softbreak));
		} else {
			renderer = new HtmlRenderer(new HtmlRenderer.Options()
					.time(time)
					.safe(safe)
					.sourcepos(sourcepos)
					.softbreak(softbreak));
		}

		if (dest != null) {
			try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(dest))) {
				renderer.render(node, writer);
			}
		} else {
			try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out) {
				@Override
				public void close() throws IOException {
					// no handle
				}
			})) {
				renderer.render(node, writer);
			}
		}
	}

	private static void usage(PrintStream out, String message) {
		if (message != null) {
			out.println(message);
			out.println();
		}
		out.println("Usage: java -jar commonmark4j-0.8.0.jar [options] [source] [dest]");
		out.println("Options:");
		out.println("  -help             print this help message");
		out.println("  -smart            use smart characters");
		out.println("  -time             print total time");
		out.println("  -type {html,xml}  print as specified format (default: html)");
	}

	private Main() {
	}
}
