package net.arnx.commonmark4j;

import java.io.BufferedReader;
import java.io.IOException;

import net.arnx.commonmark4j.impl.Processor;

public interface CMarkProcessor {
	public static CMarkProcessor newProcessor() {
		return new Processor();
	}

	public CMarkProcessor format(String format);

	public CMarkProcessor safe(boolean value);

	public CMarkProcessor smart(boolean value);

	public CMarkProcessor softbreak(String text);

	public CMarkProcessor sourcepos(boolean value);

	public CMarkProcessor time(boolean value);

	public void process(BufferedReader in, Appendable out) throws IOException;
}
