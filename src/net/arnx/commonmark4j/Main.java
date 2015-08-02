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

public final class Main {
	public static void main(String[] args) throws IOException {
		boolean time = false;
		boolean smart = false;

		boolean safe = false;
		boolean sourcepos = false;
		String softbreak = "\n";

		String format = "html";
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
				if (option.equals("-format")) {
					if (args[i].equals("xml") || args[i].equals("html")) {
						format = args[i];
					} else {
						usage(System.err, "invalid format: " + args[i]);
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

		CMarkProcessor processor = CMarkProcessor.newProcessor()
				.safe(safe)
				.smart(smart)
				.softbreak(softbreak)
				.sourcepos(sourcepos)
				.time(time)
				.format(format);

		BufferedReader reader = null;
		BufferedWriter writer = null;
		try {
			reader = (src != null) ? Files.newBufferedReader(Paths.get(src)) :
				new BufferedReader(new InputStreamReader(System.in));
			writer = (dest != null) ? Files.newBufferedWriter(Paths.get(dest)) :
				new BufferedWriter(new OutputStreamWriter(System.out));

			processor.process(reader, writer);
		} finally {
			try {
				if (writer != null) {
					if (src != null) {
						writer.close();
					} else {
						writer.flush();
					}
				}
			} finally {
				if (reader != null) {
					if (dest != null) {
						reader.close();
					}
				}
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
		out.println("  -help               print this help message");
		out.println("  -safe               remove dangerous code");
		out.println("  -smart              use smart characters");
		out.println("  -softbreak <text>   softbreak characters (default: \\n)");
		out.println("  -sourcepos          include source position information");
		out.println("  -time               print total time");
		out.println("  -format {html,xml}  output as specified format (default: html)");
	}

	private Main() {
	}
}
