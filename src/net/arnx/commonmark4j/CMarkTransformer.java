package net.arnx.commonmark4j;

import java.io.BufferedReader;
import java.io.IOException;

import net.arnx.commonmark4j.impl.Transformer;

/**
 * Defines the requirements for an object that can be used as a CommonMark transformer.
 *
 * @author Hidekatsu Izuno
 */
public interface CMarkTransformer {
	/**
	 * Creates a default CommonMark transformer.
	 *
	 * @return a new CommonMark transformer instance.
	 */
	public static CMarkTransformer newTransformer() {
		return new Transformer();
	}

	/**
	 * Sets a format type. The default format is "html".
	 *
	 * @param format format type
	 * @return this instance
	 */
	public CMarkTransformer format(String format);

	/**
	 * Sets a safe option.
	 *
	 * @param value if true, removes dangerous text.
	 * @return this instance
	 */
	public CMarkTransformer safe(boolean value);

	/**
	 * Sets a smart option.
	 *
	 * @param value if true, replace redundan characters to a smart character.
	 * @return this instance
	 */
	public CMarkTransformer smart(boolean value);

	/**
	 * Sets a softbreak text. The default text is "\n"
	 *
	 * @param text a softbreak text.
	 * @return this instance
	 */
	public CMarkTransformer softbreak(String text);

	/**
	 * Sets a source position option.
	 *
	 * @param value if true, includes source position information in node.
	 * @return this instance
	 */
	public CMarkTransformer sourcepos(boolean value);

	/**
	 * Sets a time option.
	 *
	 * @param value if true, prints total time.
	 * @return this instance
	 */
	public CMarkTransformer time(boolean value);

	/**
	 * Transforms a CommmonMark text to a specified format.
	 *
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	public void transform(BufferedReader in, Appendable out) throws IOException;
}
