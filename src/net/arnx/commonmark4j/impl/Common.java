/**
 * Copyright (c) 2015, Hidekatsu Izuno <hidekatsu.izuno@gmail.com>
 *
 * This software is released under the 2 clause BSD License, see LICENSE.
 */
package net.arnx.commonmark4j.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Common {
	private static final char C_BACKSLASH = '\\';

	public static final String SPACE = "[ \\t\\n\\f\\r\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000]";

	public static final String ENTITY = "&(?:#x[a-f0-9]{1,8}|#[0-9]{1,8}|[a-z][a-z0-9]{1,31});";

	private static final String TAGNAME = "[A-Za-z][A-Za-z0-9-]*";
	private static final String ATTRIBUTENAME = "[a-zA-Z_:][a-zA-Z0-9:._-]*";
	private static final String UNQUOTEDVALUE = "[^\"'=<>`\\x00-\\x20]+";
	private static final String SINGLEQUOTEDVALUE = "'[^']*'";
	private static final String DOUBLEQUOTEDVALUE = "\"[^\"]*\"";
	private static final String ATTRIBUTEVALUE = "(?:" + UNQUOTEDVALUE + "|" + SINGLEQUOTEDVALUE + "|" + DOUBLEQUOTEDVALUE + ")";
	private static final String ATTRIBUTEVALUESPEC = "(?:" + SPACE + "*=" + SPACE + "*" + ATTRIBUTEVALUE + ")";
	private static final String ATTRIBUTE = "(?:" + SPACE + "+" + ATTRIBUTENAME + ATTRIBUTEVALUESPEC + "?)";
	public static final String OPENTAG = "<" + TAGNAME + ATTRIBUTE + "*" + SPACE + "*/?>";
	public static final String CLOSETAG = "</" + TAGNAME + SPACE + "*[>]";
	private static final String HTMLCOMMENT = "<!---->|<!--(?:-?[^>-])(?:-?[^-])*-->";
	private static final String PROCESSINGINSTRUCTION = "[<][?].*?[?][>]";
	private static final String DECLARATION = "<![A-Z]+" + SPACE + "+[^>]*>";
	private static final String CDATA = "<!\\[CDATA\\[.*?\\]\\]>";
	private static final String HTMLTAG = "(?:" + OPENTAG + "|" + CLOSETAG + "|" + HTMLCOMMENT + "|" +
			PROCESSINGINSTRUCTION + "|" + DECLARATION + "|" + CDATA + ")";
	public static final Pattern reHtmlTag = Pattern.compile("^" + HTMLTAG, Pattern.CASE_INSENSITIVE);

	public static final String ESCAPABLE = "[!\"#$%&'()*+,./:;<=>?@\\[\\\\\\]^_`{|}~-]";

	private static final Pattern reEntityOrEscapedChar = Pattern.compile("\\\\" + ESCAPABLE + '|' + ENTITY, Pattern.CASE_INSENSITIVE);

	private static final String XMLSPECIAL = "[&<>\"]";

	private static final Pattern reXmlSpecial = Pattern.compile(XMLSPECIAL);

	private static final Pattern reXmlSpecialOrEntity = Pattern.compile(ENTITY + '|' + XMLSPECIAL, Pattern.CASE_INSENSITIVE);

	private static final Pattern reEntityPattern = Pattern.compile("&(?:([a-zA-Z0-9]+)|#[xX]([0-9a-fA-F]{1,8})|#([0-9]{1,8}));");

	private static final Properties ENTITIES_MAP = new Properties();

	static {
		try (InputStream in = Common.class.getResourceAsStream("entities.properties")) {
			ENTITIES_MAP.load(in);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String unescapeChar(String s) {
		if (!s.isEmpty() && s.charAt(0) == C_BACKSLASH) {
			return s.substring(1);
		} else {
			return decodeHTML(s);
		}
	}

	/** Replace entities and backslash escapes with literal characters. */
	public static String unescapeString(String s) {
		if (s.indexOf('\\') != -1 || s.indexOf('&') != -1) {
			return replace(s, reEntityOrEscapedChar, (m) -> {
				return unescapeChar(m.group());
			});
		}
		return s;
	}

	public static String normalizeURI(String uri) {
		try {
			return mdurlEncode(mdurlDecode(uri));
		} catch (Exception e) {
			return uri;
		}
	}

	private static String replaceUnsafeChar(String s) {
		switch (s) {
		case "&":
			return "&amp;";
		case "<":
			return "&lt;";
		case ">":
			return "&gt;";
		case "\"":
			return "&quot;";
		default:
			return s;
		}
	}

	public static String escapeXml(String s, boolean preserve_entities) {
		Matcher m = reXmlSpecial.matcher(s);
		if (m.find()) {
			StringBuilder sb = new StringBuilder((int)(s.length() * 1.5));
			if (preserve_entities) {
				m = reXmlSpecialOrEntity.matcher(s);
				m.find();
			}
			int start = 0;
			do {
				if (start < m.start()) {
					sb.append(s, start, m.start());
				}
				sb.append((m.group().length() == 1) ? replaceUnsafeChar(m.group()) : m.group());
				start = m.end();
			} while (m.find());
			if (start < s.length()) {
				sb.append(s, start, s.length());
			}
			return sb.toString();
		}
		return s;
	}

	public static String decodeHTML(String s) {
		return replace(s, reEntityPattern, (m) -> {
			if (m.group(1) != null) {
				String c = ENTITIES_MAP.getProperty(m.group(1));
				return (c != null) ? c : m.group();
			} else if (m.group(2) != null) {
				int code = Integer.parseInt(m.group(2), 16);
				if (code == 0) return "\uFFFD";

				try {
					return String.valueOf(Character.toChars(code));
				} catch (IllegalArgumentException e) {
					return "\uFFFD";
				}
			} else if (m.group(3) != null) {
				int code = Integer.parseInt(m.group(3));
				if (code == 0) return "\uFFFD";

				try {
					return String.valueOf(Character.toChars(code));
				} catch (IllegalArgumentException e) {
					return "\uFFFD";
				}
			} else {
				throw new IllegalStateException();
			}
		});
	}

	static String mdurlEncode(String str) {
		StringBuilder sb = new StringBuilder((int)(str.length() * 1.5));
		CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		CharBuffer cb = CharBuffer.allocate(2);
		ByteBuffer bb = ByteBuffer.allocate(12);

		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);

			if (c == '%' && i + 2 < str.length()) {
				char c1 = str.charAt(i + 1);
				char c2 = str.charAt(i + 2);
				if (((c1 >= '0' && c1 <= '9') || (c1 >= 'A' && c1 <= 'F') || (c1 >= 'a' && c1 <= 'f'))
						&& ((c2 >= '0' && c2 <= '9') || (c2 >= 'A' && c2 <= 'F') || (c2 >= 'a' && c2 <= 'f'))) {
					sb.append(c).append(c1).append(c2);
					i += 2;
					continue;
				}
			}

			if (c < 128) {
				if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
						|| ";/?:@&=+$,-_.!~*'()#".indexOf(c) != -1) {
					sb.append(c);
					continue;
				}
			}

			if (Character.isHighSurrogate(c)) {
				if (i + 1 < str.length()) {
					char c2 = str.charAt(i + 1);
					if (Character.isLowSurrogate(c2)) {
						cb.append(c);
						cb.append(c2);
						i++;
					} else {
						cb.append('\uFFFD');
					}
				} else {
					cb.append('\uFFFD');
				}
			} else if (Character.isLowSurrogate(c)) {
				cb.append('\uFFFD');
			} else {
				cb.append(c);
			}

			cb.flip();
			encoder.reset().encode(cb, bb, true);
			cb.clear();

			bb.flip();
			for (int pos = bb.position(); pos < bb.limit(); pos++) {
				sb.append('%');
				sb.append("0123456789ABCDEF".charAt((bb.get(pos) >> 4) & 0x0F));
				sb.append("0123456789ABCDEF".charAt(bb.get(pos) & 0x0F));
			}
			bb.clear();
		}

		return sb.toString();
	}

	static String mdurlDecode(String str) {
		StringBuilder sb = new StringBuilder(str.length());
		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
		ByteBuffer bb = ByteBuffer.allocate(12);
		CharBuffer cb = CharBuffer.allocate(2);

		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (c == '%') {
				if (i + 2 < str.length()) {
					char c1 = str.charAt(++i);
					char c2 = str.charAt(++i);

					int h1 = (c1 >= '0' && c1 <= '9') ? c1-48 :
						(c1 >= 'A' && c1 <= 'F') ? c1-65+10 :
						(c1 >= 'a' && c1 <= 'f') ? c1-97+10 : -1;
					int h2 = (c2 >= '0' && c2 <= '9') ? c2-48 :
						(c2 >= 'A' && c2 <= 'F') ? c2-65+10 :
						(c2 >= 'a' && c2 <= 'f') ? c2-97+10 : -1;

					if (h1 != -1 && h2 != -1) {
						byte b = (byte)(h1 << 4 | h2);
						if (bb.position() == 0) {
							if ((b & 0b10000000) == 0b00000000) {
								bb.limit(1);
							} else if ((b & 0b11111100) == 0b11111100) {
								bb.limit(6);
							} else if ((b & 0b11111000) == 0b11111000) {
								bb.limit(5);
							} else if ((b & 0b11110000) == 0b11110000) {
								bb.limit(4);
							} else if ((b & 0b11100000) == 0b11100000) {
								bb.limit(3);
							} else if ((b & 0b11000000) == 0b11000000) {
								bb.limit(2);
							} else {
								sb.append('\uFFFD');
								continue;
							}
						}
						bb.put(b);

						if (bb.position() == bb.limit()) {
							bb.flip();
							decoder.reset().decode(bb, cb, true);
							bb.clear();

							cb.flip();
							sb.append(cb);
							cb.clear();
						}
					} else {
						sb.append(c).append(c1).append(c2);
					}
				} else {
					sb.append(c);
				}
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	public static String fromCodePoint(int c) {
		return String.valueOf(Character.toChars(c));
	}

	public static String replace(String s, Pattern pattern, String rep) {
		return replace(s, pattern, (m) -> {
			return rep;
		});
	}

	public static String replace(String s, Pattern pattern, Function<Matcher, String> func) {
		Matcher m = pattern.matcher(s);
		if (m.find()) {
			StringBuilder sb = new StringBuilder((int)(s.length() * 1.5));
			int start = 0;
			do {
				if (start < m.start()) {
					sb.append(s, start, m.start());
				}
				sb.append(func.apply(m));
				start = m.end();
			} while (m.find());
			if (start < s.length()) {
				sb.append(s, start, s.length());
			}
			return sb.toString();
		}
		return s;
	}

	public static String repeat(String s, int count) {
		if (count <= 0) {
			return "";
		} else if (count == 1) {
			return s;
		} else {
			StringBuilder sb = new StringBuilder(s.length() * count);
			for (int i = 0; i < count; i++) {
				sb.append(s);
			}
			return sb.toString();
		}
	}

	public static String normalizeReference(String s) {
		if (s.length() > 1) {
			s = s.substring(1, s.length() - 1).trim();
		}
		StringBuilder sb = new StringBuilder(s.length());
		char prev = '\0';
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (prev == '\uD801') {
				switch (c) {
				case '\uDC00': sb.append('\uDC28'); break;
				case '\uDC01': sb.append('\uDC29'); break;
				case '\uDC02': sb.append('\uDC2A'); break;
				case '\uDC03': sb.append('\uDC2B'); break;
				case '\uDC04': sb.append('\uDC2C'); break;
				case '\uDC05': sb.append('\uDC2D'); break;
				case '\uDC06': sb.append('\uDC2E'); break;
				case '\uDC07': sb.append('\uDC2F'); break;
				case '\uDC08': sb.append('\uDC30'); break;
				case '\uDC09': sb.append('\uDC31'); break;
				case '\uDC0A': sb.append('\uDC32'); break;
				case '\uDC0B': sb.append('\uDC33'); break;
				case '\uDC0C': sb.append('\uDC34'); break;
				case '\uDC0D': sb.append('\uDC35'); break;
				case '\uDC0E': sb.append('\uDC36'); break;
				case '\uDC0F': sb.append('\uDC37'); break;
				case '\uDC10': sb.append('\uDC38'); break;
				case '\uDC11': sb.append('\uDC39'); break;
				case '\uDC12': sb.append('\uDC3A'); break;
				case '\uDC13': sb.append('\uDC3B'); break;
				case '\uDC14': sb.append('\uDC3C'); break;
				case '\uDC15': sb.append('\uDC3D'); break;
				case '\uDC16': sb.append('\uDC3E'); break;
				case '\uDC17': sb.append('\uDC3F'); break;
				case '\uDC18': sb.append('\uDC40'); break;
				case '\uDC19': sb.append('\uDC41'); break;
				case '\uDC1A': sb.append('\uDC42'); break;
				case '\uDC1B': sb.append('\uDC43'); break;
				case '\uDC1C': sb.append('\uDC44'); break;
				case '\uDC1D': sb.append('\uDC45'); break;
				case '\uDC1E': sb.append('\uDC46'); break;
				case '\uDC1F': sb.append('\uDC47'); break;
				case '\uDC20': sb.append('\uDC48'); break;
				case '\uDC21': sb.append('\uDC49'); break;
				case '\uDC22': sb.append('\uDC4A'); break;
				case '\uDC23': sb.append('\uDC4B'); break;
				case '\uDC24': sb.append('\uDC4C'); break;
				case '\uDC25': sb.append('\uDC4D'); break;
				case '\uDC26': sb.append('\uDC4E'); break;
				case '\uDC27': sb.append('\uDC4F'); break;
				case ' ':
				case '\t':
				case '\r':
				case '\n':
					sb.append(' ');
					break;
				default: sb.append(c);
				}
			} else if (prev == '\uD806') {
				switch (c) {
				case '\uDCA0': sb.append('\uDCC0'); break;
				case '\uDCA1': sb.append('\uDCC1'); break;
				case '\uDCA2': sb.append('\uDCC2'); break;
				case '\uDCA3': sb.append('\uDCC3'); break;
				case '\uDCA4': sb.append('\uDCC4'); break;
				case '\uDCA5': sb.append('\uDCC5'); break;
				case '\uDCA6': sb.append('\uDCC6'); break;
				case '\uDCA7': sb.append('\uDCC7'); break;
				case '\uDCA8': sb.append('\uDCC8'); break;
				case '\uDCA9': sb.append('\uDCC9'); break;
				case '\uDCAA': sb.append('\uDCCA'); break;
				case '\uDCAB': sb.append('\uDCCB'); break;
				case '\uDCAC': sb.append('\uDCCC'); break;
				case '\uDCAD': sb.append('\uDCCD'); break;
				case '\uDCAE': sb.append('\uDCCE'); break;
				case '\uDCAF': sb.append('\uDCCF'); break;
				case '\uDCB0': sb.append('\uDCD0'); break;
				case '\uDCB1': sb.append('\uDCD1'); break;
				case '\uDCB2': sb.append('\uDCD2'); break;
				case '\uDCB3': sb.append('\uDCD3'); break;
				case '\uDCB4': sb.append('\uDCD4'); break;
				case '\uDCB5': sb.append('\uDCD5'); break;
				case '\uDCB6': sb.append('\uDCD6'); break;
				case '\uDCB7': sb.append('\uDCD7'); break;
				case '\uDCB8': sb.append('\uDCD8'); break;
				case '\uDCB9': sb.append('\uDCD9'); break;
				case '\uDCBA': sb.append('\uDCDA'); break;
				case '\uDCBB': sb.append('\uDCDB'); break;
				case '\uDCBC': sb.append('\uDCDC'); break;
				case '\uDCBD': sb.append('\uDCDD'); break;
				case '\uDCBE': sb.append('\uDCDE'); break;
				case '\uDCBF': sb.append('\uDCDF'); break;
				case ' ':
				case '\t':
				case '\r':
				case '\n':
					sb.append(' ');
					break;
				default: sb.append(c);
				}
			} else {
				switch (c) {
				case '\uD801': break;
				case '\uD806': break;
				case 'A': sb.append('a'); break;
				case 'B': sb.append('b'); break;
				case 'C': sb.append('c'); break;
				case 'D': sb.append('d'); break;
				case 'E': sb.append('e'); break;
				case 'F': sb.append('f'); break;
				case 'G': sb.append('g'); break;
				case 'H': sb.append('h'); break;
				case 'I': sb.append('i'); break;
				case 'J': sb.append('j'); break;
				case 'K': sb.append('k'); break;
				case 'L': sb.append('l'); break;
				case 'M': sb.append('m'); break;
				case 'N': sb.append('n'); break;
				case 'O': sb.append('o'); break;
				case 'P': sb.append('p'); break;
				case 'Q': sb.append('q'); break;
				case 'R': sb.append('r'); break;
				case 'S': sb.append('s'); break;
				case 'T': sb.append('t'); break;
				case 'U': sb.append('u'); break;
				case 'V': sb.append('v'); break;
				case 'W': sb.append('w'); break;
				case 'X': sb.append('x'); break;
				case 'Y': sb.append('y'); break;
				case 'Z': sb.append('z'); break;
				case '\u00B5': sb.append('\u03BC'); break;
				case '\u00C0': sb.append('\u00E0'); break;
				case '\u00C1': sb.append('\u00E1'); break;
				case '\u00C2': sb.append('\u00E2'); break;
				case '\u00C3': sb.append('\u00E3'); break;
				case '\u00C4': sb.append('\u00E4'); break;
				case '\u00C5': sb.append('\u00E5'); break;
				case '\u00C6': sb.append('\u00E6'); break;
				case '\u00C7': sb.append('\u00E7'); break;
				case '\u00C8': sb.append('\u00E8'); break;
				case '\u00C9': sb.append('\u00E9'); break;
				case '\u00CA': sb.append('\u00EA'); break;
				case '\u00CB': sb.append('\u00EB'); break;
				case '\u00CC': sb.append('\u00EC'); break;
				case '\u00CD': sb.append('\u00ED'); break;
				case '\u00CE': sb.append('\u00EE'); break;
				case '\u00CF': sb.append('\u00EF'); break;
				case '\u00D0': sb.append('\u00F0'); break;
				case '\u00D1': sb.append('\u00F1'); break;
				case '\u00D2': sb.append('\u00F2'); break;
				case '\u00D3': sb.append('\u00F3'); break;
				case '\u00D4': sb.append('\u00F4'); break;
				case '\u00D5': sb.append('\u00F5'); break;
				case '\u00D6': sb.append('\u00F6'); break;
				case '\u00D8': sb.append('\u00F8'); break;
				case '\u00D9': sb.append('\u00F9'); break;
				case '\u00DA': sb.append('\u00FA'); break;
				case '\u00DB': sb.append('\u00FB'); break;
				case '\u00DC': sb.append('\u00FC'); break;
				case '\u00DD': sb.append('\u00FD'); break;
				case '\u00DE': sb.append('\u00FE'); break;
				case '\u0100': sb.append('\u0101'); break;
				case '\u0102': sb.append('\u0103'); break;
				case '\u0104': sb.append('\u0105'); break;
				case '\u0106': sb.append('\u0107'); break;
				case '\u0108': sb.append('\u0109'); break;
				case '\u010A': sb.append('\u010B'); break;
				case '\u010C': sb.append('\u010D'); break;
				case '\u010E': sb.append('\u010F'); break;
				case '\u0110': sb.append('\u0111'); break;
				case '\u0112': sb.append('\u0113'); break;
				case '\u0114': sb.append('\u0115'); break;
				case '\u0116': sb.append('\u0117'); break;
				case '\u0118': sb.append('\u0119'); break;
				case '\u011A': sb.append('\u011B'); break;
				case '\u011C': sb.append('\u011D'); break;
				case '\u011E': sb.append('\u011F'); break;
				case '\u0120': sb.append('\u0121'); break;
				case '\u0122': sb.append('\u0123'); break;
				case '\u0124': sb.append('\u0125'); break;
				case '\u0126': sb.append('\u0127'); break;
				case '\u0128': sb.append('\u0129'); break;
				case '\u012A': sb.append('\u012B'); break;
				case '\u012C': sb.append('\u012D'); break;
				case '\u012E': sb.append('\u012F'); break;
				case '\u0132': sb.append('\u0133'); break;
				case '\u0134': sb.append('\u0135'); break;
				case '\u0136': sb.append('\u0137'); break;
				case '\u0139': sb.append('\u013A'); break;
				case '\u013B': sb.append('\u013C'); break;
				case '\u013D': sb.append('\u013E'); break;
				case '\u013F': sb.append('\u0140'); break;
				case '\u0141': sb.append('\u0142'); break;
				case '\u0143': sb.append('\u0144'); break;
				case '\u0145': sb.append('\u0146'); break;
				case '\u0147': sb.append('\u0148'); break;
				case '\u014A': sb.append('\u014B'); break;
				case '\u014C': sb.append('\u014D'); break;
				case '\u014E': sb.append('\u014F'); break;
				case '\u0150': sb.append('\u0151'); break;
				case '\u0152': sb.append('\u0153'); break;
				case '\u0154': sb.append('\u0155'); break;
				case '\u0156': sb.append('\u0157'); break;
				case '\u0158': sb.append('\u0159'); break;
				case '\u015A': sb.append('\u015B'); break;
				case '\u015C': sb.append('\u015D'); break;
				case '\u015E': sb.append('\u015F'); break;
				case '\u0160': sb.append('\u0161'); break;
				case '\u0162': sb.append('\u0163'); break;
				case '\u0164': sb.append('\u0165'); break;
				case '\u0166': sb.append('\u0167'); break;
				case '\u0168': sb.append('\u0169'); break;
				case '\u016A': sb.append('\u016B'); break;
				case '\u016C': sb.append('\u016D'); break;
				case '\u016E': sb.append('\u016F'); break;
				case '\u0170': sb.append('\u0171'); break;
				case '\u0172': sb.append('\u0173'); break;
				case '\u0174': sb.append('\u0175'); break;
				case '\u0176': sb.append('\u0177'); break;
				case '\u0178': sb.append('\u00FF'); break;
				case '\u0179': sb.append('\u017A'); break;
				case '\u017B': sb.append('\u017C'); break;
				case '\u017D': sb.append('\u017E'); break;
				case '\u017F': sb.append('s'); break;
				case '\u0181': sb.append('\u0253'); break;
				case '\u0182': sb.append('\u0183'); break;
				case '\u0184': sb.append('\u0185'); break;
				case '\u0186': sb.append('\u0254'); break;
				case '\u0187': sb.append('\u0188'); break;
				case '\u0189': sb.append('\u0256'); break;
				case '\u018A': sb.append('\u0257'); break;
				case '\u018B': sb.append('\u018C'); break;
				case '\u018E': sb.append('\u01DD'); break;
				case '\u018F': sb.append('\u0259'); break;
				case '\u0190': sb.append('\u025B'); break;
				case '\u0191': sb.append('\u0192'); break;
				case '\u0193': sb.append('\u0260'); break;
				case '\u0194': sb.append('\u0263'); break;
				case '\u0196': sb.append('\u0269'); break;
				case '\u0197': sb.append('\u0268'); break;
				case '\u0198': sb.append('\u0199'); break;
				case '\u019C': sb.append('\u026F'); break;
				case '\u019D': sb.append('\u0272'); break;
				case '\u019F': sb.append('\u0275'); break;
				case '\u01A0': sb.append('\u01A1'); break;
				case '\u01A2': sb.append('\u01A3'); break;
				case '\u01A4': sb.append('\u01A5'); break;
				case '\u01A6': sb.append('\u0280'); break;
				case '\u01A7': sb.append('\u01A8'); break;
				case '\u01A9': sb.append('\u0283'); break;
				case '\u01AC': sb.append('\u01AD'); break;
				case '\u01AE': sb.append('\u0288'); break;
				case '\u01AF': sb.append('\u01B0'); break;
				case '\u01B1': sb.append('\u028A'); break;
				case '\u01B2': sb.append('\u028B'); break;
				case '\u01B3': sb.append('\u01B4'); break;
				case '\u01B5': sb.append('\u01B6'); break;
				case '\u01B7': sb.append('\u0292'); break;
				case '\u01B8': sb.append('\u01B9'); break;
				case '\u01BC': sb.append('\u01BD'); break;
				case '\u01C4': sb.append('\u01C6'); break;
				case '\u01C5': sb.append('\u01C6'); break;
				case '\u01C7': sb.append('\u01C9'); break;
				case '\u01C8': sb.append('\u01C9'); break;
				case '\u01CA': sb.append('\u01CC'); break;
				case '\u01CB': sb.append('\u01CC'); break;
				case '\u01CD': sb.append('\u01CE'); break;
				case '\u01CF': sb.append('\u01D0'); break;
				case '\u01D1': sb.append('\u01D2'); break;
				case '\u01D3': sb.append('\u01D4'); break;
				case '\u01D5': sb.append('\u01D6'); break;
				case '\u01D7': sb.append('\u01D8'); break;
				case '\u01D9': sb.append('\u01DA'); break;
				case '\u01DB': sb.append('\u01DC'); break;
				case '\u01DE': sb.append('\u01DF'); break;
				case '\u01E0': sb.append('\u01E1'); break;
				case '\u01E2': sb.append('\u01E3'); break;
				case '\u01E4': sb.append('\u01E5'); break;
				case '\u01E6': sb.append('\u01E7'); break;
				case '\u01E8': sb.append('\u01E9'); break;
				case '\u01EA': sb.append('\u01EB'); break;
				case '\u01EC': sb.append('\u01ED'); break;
				case '\u01EE': sb.append('\u01EF'); break;
				case '\u01F1': sb.append('\u01F3'); break;
				case '\u01F2': sb.append('\u01F3'); break;
				case '\u01F4': sb.append('\u01F5'); break;
				case '\u01F6': sb.append('\u0195'); break;
				case '\u01F7': sb.append('\u01BF'); break;
				case '\u01F8': sb.append('\u01F9'); break;
				case '\u01FA': sb.append('\u01FB'); break;
				case '\u01FC': sb.append('\u01FD'); break;
				case '\u01FE': sb.append('\u01FF'); break;
				case '\u0200': sb.append('\u0201'); break;
				case '\u0202': sb.append('\u0203'); break;
				case '\u0204': sb.append('\u0205'); break;
				case '\u0206': sb.append('\u0207'); break;
				case '\u0208': sb.append('\u0209'); break;
				case '\u020A': sb.append('\u020B'); break;
				case '\u020C': sb.append('\u020D'); break;
				case '\u020E': sb.append('\u020F'); break;
				case '\u0210': sb.append('\u0211'); break;
				case '\u0212': sb.append('\u0213'); break;
				case '\u0214': sb.append('\u0215'); break;
				case '\u0216': sb.append('\u0217'); break;
				case '\u0218': sb.append('\u0219'); break;
				case '\u021A': sb.append('\u021B'); break;
				case '\u021C': sb.append('\u021D'); break;
				case '\u021E': sb.append('\u021F'); break;
				case '\u0220': sb.append('\u019E'); break;
				case '\u0222': sb.append('\u0223'); break;
				case '\u0224': sb.append('\u0225'); break;
				case '\u0226': sb.append('\u0227'); break;
				case '\u0228': sb.append('\u0229'); break;
				case '\u022A': sb.append('\u022B'); break;
				case '\u022C': sb.append('\u022D'); break;
				case '\u022E': sb.append('\u022F'); break;
				case '\u0230': sb.append('\u0231'); break;
				case '\u0232': sb.append('\u0233'); break;
				case '\u023A': sb.append('\u2C65'); break;
				case '\u023B': sb.append('\u023C'); break;
				case '\u023D': sb.append('\u019A'); break;
				case '\u023E': sb.append('\u2C66'); break;
				case '\u0241': sb.append('\u0242'); break;
				case '\u0243': sb.append('\u0180'); break;
				case '\u0244': sb.append('\u0289'); break;
				case '\u0245': sb.append('\u028C'); break;
				case '\u0246': sb.append('\u0247'); break;
				case '\u0248': sb.append('\u0249'); break;
				case '\u024A': sb.append('\u024B'); break;
				case '\u024C': sb.append('\u024D'); break;
				case '\u024E': sb.append('\u024F'); break;
				case '\u0345': sb.append('\u03B9'); break;
				case '\u0370': sb.append('\u0371'); break;
				case '\u0372': sb.append('\u0373'); break;
				case '\u0376': sb.append('\u0377'); break;
				case '\u037F': sb.append('\u03F3'); break;
				case '\u0386': sb.append('\u03AC'); break;
				case '\u0388': sb.append('\u03AD'); break;
				case '\u0389': sb.append('\u03AE'); break;
				case '\u038A': sb.append('\u03AF'); break;
				case '\u038C': sb.append('\u03CC'); break;
				case '\u038E': sb.append('\u03CD'); break;
				case '\u038F': sb.append('\u03CE'); break;
				case '\u0391': sb.append('\u03B1'); break;
				case '\u0392': sb.append('\u03B2'); break;
				case '\u0393': sb.append('\u03B3'); break;
				case '\u0394': sb.append('\u03B4'); break;
				case '\u0395': sb.append('\u03B5'); break;
				case '\u0396': sb.append('\u03B6'); break;
				case '\u0397': sb.append('\u03B7'); break;
				case '\u0398': sb.append('\u03B8'); break;
				case '\u0399': sb.append('\u03B9'); break;
				case '\u039A': sb.append('\u03BA'); break;
				case '\u039B': sb.append('\u03BB'); break;
				case '\u039C': sb.append('\u03BC'); break;
				case '\u039D': sb.append('\u03BD'); break;
				case '\u039E': sb.append('\u03BE'); break;
				case '\u039F': sb.append('\u03BF'); break;
				case '\u03A0': sb.append('\u03C0'); break;
				case '\u03A1': sb.append('\u03C1'); break;
				case '\u03A3': sb.append('\u03C3'); break;
				case '\u03A4': sb.append('\u03C4'); break;
				case '\u03A5': sb.append('\u03C5'); break;
				case '\u03A6': sb.append('\u03C6'); break;
				case '\u03A7': sb.append('\u03C7'); break;
				case '\u03A8': sb.append('\u03C8'); break;
				case '\u03A9': sb.append('\u03C9'); break;
				case '\u03AA': sb.append('\u03CA'); break;
				case '\u03AB': sb.append('\u03CB'); break;
				case '\u03C2': sb.append('\u03C3'); break;
				case '\u03CF': sb.append('\u03D7'); break;
				case '\u03D0': sb.append('\u03B2'); break;
				case '\u03D1': sb.append('\u03B8'); break;
				case '\u03D5': sb.append('\u03C6'); break;
				case '\u03D6': sb.append('\u03C0'); break;
				case '\u03D8': sb.append('\u03D9'); break;
				case '\u03DA': sb.append('\u03DB'); break;
				case '\u03DC': sb.append('\u03DD'); break;
				case '\u03DE': sb.append('\u03DF'); break;
				case '\u03E0': sb.append('\u03E1'); break;
				case '\u03E2': sb.append('\u03E3'); break;
				case '\u03E4': sb.append('\u03E5'); break;
				case '\u03E6': sb.append('\u03E7'); break;
				case '\u03E8': sb.append('\u03E9'); break;
				case '\u03EA': sb.append('\u03EB'); break;
				case '\u03EC': sb.append('\u03ED'); break;
				case '\u03EE': sb.append('\u03EF'); break;
				case '\u03F0': sb.append('\u03BA'); break;
				case '\u03F1': sb.append('\u03C1'); break;
				case '\u03F4': sb.append('\u03B8'); break;
				case '\u03F5': sb.append('\u03B5'); break;
				case '\u03F7': sb.append('\u03F8'); break;
				case '\u03F9': sb.append('\u03F2'); break;
				case '\u03FA': sb.append('\u03FB'); break;
				case '\u03FD': sb.append('\u037B'); break;
				case '\u03FE': sb.append('\u037C'); break;
				case '\u03FF': sb.append('\u037D'); break;
				case '\u0400': sb.append('\u0450'); break;
				case '\u0401': sb.append('\u0451'); break;
				case '\u0402': sb.append('\u0452'); break;
				case '\u0403': sb.append('\u0453'); break;
				case '\u0404': sb.append('\u0454'); break;
				case '\u0405': sb.append('\u0455'); break;
				case '\u0406': sb.append('\u0456'); break;
				case '\u0407': sb.append('\u0457'); break;
				case '\u0408': sb.append('\u0458'); break;
				case '\u0409': sb.append('\u0459'); break;
				case '\u040A': sb.append('\u045A'); break;
				case '\u040B': sb.append('\u045B'); break;
				case '\u040C': sb.append('\u045C'); break;
				case '\u040D': sb.append('\u045D'); break;
				case '\u040E': sb.append('\u045E'); break;
				case '\u040F': sb.append('\u045F'); break;
				case '\u0410': sb.append('\u0430'); break;
				case '\u0411': sb.append('\u0431'); break;
				case '\u0412': sb.append('\u0432'); break;
				case '\u0413': sb.append('\u0433'); break;
				case '\u0414': sb.append('\u0434'); break;
				case '\u0415': sb.append('\u0435'); break;
				case '\u0416': sb.append('\u0436'); break;
				case '\u0417': sb.append('\u0437'); break;
				case '\u0418': sb.append('\u0438'); break;
				case '\u0419': sb.append('\u0439'); break;
				case '\u041A': sb.append('\u043A'); break;
				case '\u041B': sb.append('\u043B'); break;
				case '\u041C': sb.append('\u043C'); break;
				case '\u041D': sb.append('\u043D'); break;
				case '\u041E': sb.append('\u043E'); break;
				case '\u041F': sb.append('\u043F'); break;
				case '\u0420': sb.append('\u0440'); break;
				case '\u0421': sb.append('\u0441'); break;
				case '\u0422': sb.append('\u0442'); break;
				case '\u0423': sb.append('\u0443'); break;
				case '\u0424': sb.append('\u0444'); break;
				case '\u0425': sb.append('\u0445'); break;
				case '\u0426': sb.append('\u0446'); break;
				case '\u0427': sb.append('\u0447'); break;
				case '\u0428': sb.append('\u0448'); break;
				case '\u0429': sb.append('\u0449'); break;
				case '\u042A': sb.append('\u044A'); break;
				case '\u042B': sb.append('\u044B'); break;
				case '\u042C': sb.append('\u044C'); break;
				case '\u042D': sb.append('\u044D'); break;
				case '\u042E': sb.append('\u044E'); break;
				case '\u042F': sb.append('\u044F'); break;
				case '\u0460': sb.append('\u0461'); break;
				case '\u0462': sb.append('\u0463'); break;
				case '\u0464': sb.append('\u0465'); break;
				case '\u0466': sb.append('\u0467'); break;
				case '\u0468': sb.append('\u0469'); break;
				case '\u046A': sb.append('\u046B'); break;
				case '\u046C': sb.append('\u046D'); break;
				case '\u046E': sb.append('\u046F'); break;
				case '\u0470': sb.append('\u0471'); break;
				case '\u0472': sb.append('\u0473'); break;
				case '\u0474': sb.append('\u0475'); break;
				case '\u0476': sb.append('\u0477'); break;
				case '\u0478': sb.append('\u0479'); break;
				case '\u047A': sb.append('\u047B'); break;
				case '\u047C': sb.append('\u047D'); break;
				case '\u047E': sb.append('\u047F'); break;
				case '\u0480': sb.append('\u0481'); break;
				case '\u048A': sb.append('\u048B'); break;
				case '\u048C': sb.append('\u048D'); break;
				case '\u048E': sb.append('\u048F'); break;
				case '\u0490': sb.append('\u0491'); break;
				case '\u0492': sb.append('\u0493'); break;
				case '\u0494': sb.append('\u0495'); break;
				case '\u0496': sb.append('\u0497'); break;
				case '\u0498': sb.append('\u0499'); break;
				case '\u049A': sb.append('\u049B'); break;
				case '\u049C': sb.append('\u049D'); break;
				case '\u049E': sb.append('\u049F'); break;
				case '\u04A0': sb.append('\u04A1'); break;
				case '\u04A2': sb.append('\u04A3'); break;
				case '\u04A4': sb.append('\u04A5'); break;
				case '\u04A6': sb.append('\u04A7'); break;
				case '\u04A8': sb.append('\u04A9'); break;
				case '\u04AA': sb.append('\u04AB'); break;
				case '\u04AC': sb.append('\u04AD'); break;
				case '\u04AE': sb.append('\u04AF'); break;
				case '\u04B0': sb.append('\u04B1'); break;
				case '\u04B2': sb.append('\u04B3'); break;
				case '\u04B4': sb.append('\u04B5'); break;
				case '\u04B6': sb.append('\u04B7'); break;
				case '\u04B8': sb.append('\u04B9'); break;
				case '\u04BA': sb.append('\u04BB'); break;
				case '\u04BC': sb.append('\u04BD'); break;
				case '\u04BE': sb.append('\u04BF'); break;
				case '\u04C0': sb.append('\u04CF'); break;
				case '\u04C1': sb.append('\u04C2'); break;
				case '\u04C3': sb.append('\u04C4'); break;
				case '\u04C5': sb.append('\u04C6'); break;
				case '\u04C7': sb.append('\u04C8'); break;
				case '\u04C9': sb.append('\u04CA'); break;
				case '\u04CB': sb.append('\u04CC'); break;
				case '\u04CD': sb.append('\u04CE'); break;
				case '\u04D0': sb.append('\u04D1'); break;
				case '\u04D2': sb.append('\u04D3'); break;
				case '\u04D4': sb.append('\u04D5'); break;
				case '\u04D6': sb.append('\u04D7'); break;
				case '\u04D8': sb.append('\u04D9'); break;
				case '\u04DA': sb.append('\u04DB'); break;
				case '\u04DC': sb.append('\u04DD'); break;
				case '\u04DE': sb.append('\u04DF'); break;
				case '\u04E0': sb.append('\u04E1'); break;
				case '\u04E2': sb.append('\u04E3'); break;
				case '\u04E4': sb.append('\u04E5'); break;
				case '\u04E6': sb.append('\u04E7'); break;
				case '\u04E8': sb.append('\u04E9'); break;
				case '\u04EA': sb.append('\u04EB'); break;
				case '\u04EC': sb.append('\u04ED'); break;
				case '\u04EE': sb.append('\u04EF'); break;
				case '\u04F0': sb.append('\u04F1'); break;
				case '\u04F2': sb.append('\u04F3'); break;
				case '\u04F4': sb.append('\u04F5'); break;
				case '\u04F6': sb.append('\u04F7'); break;
				case '\u04F8': sb.append('\u04F9'); break;
				case '\u04FA': sb.append('\u04FB'); break;
				case '\u04FC': sb.append('\u04FD'); break;
				case '\u04FE': sb.append('\u04FF'); break;
				case '\u0500': sb.append('\u0501'); break;
				case '\u0502': sb.append('\u0503'); break;
				case '\u0504': sb.append('\u0505'); break;
				case '\u0506': sb.append('\u0507'); break;
				case '\u0508': sb.append('\u0509'); break;
				case '\u050A': sb.append('\u050B'); break;
				case '\u050C': sb.append('\u050D'); break;
				case '\u050E': sb.append('\u050F'); break;
				case '\u0510': sb.append('\u0511'); break;
				case '\u0512': sb.append('\u0513'); break;
				case '\u0514': sb.append('\u0515'); break;
				case '\u0516': sb.append('\u0517'); break;
				case '\u0518': sb.append('\u0519'); break;
				case '\u051A': sb.append('\u051B'); break;
				case '\u051C': sb.append('\u051D'); break;
				case '\u051E': sb.append('\u051F'); break;
				case '\u0520': sb.append('\u0521'); break;
				case '\u0522': sb.append('\u0523'); break;
				case '\u0524': sb.append('\u0525'); break;
				case '\u0526': sb.append('\u0527'); break;
				case '\u0528': sb.append('\u0529'); break;
				case '\u052A': sb.append('\u052B'); break;
				case '\u052C': sb.append('\u052D'); break;
				case '\u052E': sb.append('\u052F'); break;
				case '\u0531': sb.append('\u0561'); break;
				case '\u0532': sb.append('\u0562'); break;
				case '\u0533': sb.append('\u0563'); break;
				case '\u0534': sb.append('\u0564'); break;
				case '\u0535': sb.append('\u0565'); break;
				case '\u0536': sb.append('\u0566'); break;
				case '\u0537': sb.append('\u0567'); break;
				case '\u0538': sb.append('\u0568'); break;
				case '\u0539': sb.append('\u0569'); break;
				case '\u053A': sb.append('\u056A'); break;
				case '\u053B': sb.append('\u056B'); break;
				case '\u053C': sb.append('\u056C'); break;
				case '\u053D': sb.append('\u056D'); break;
				case '\u053E': sb.append('\u056E'); break;
				case '\u053F': sb.append('\u056F'); break;
				case '\u0540': sb.append('\u0570'); break;
				case '\u0541': sb.append('\u0571'); break;
				case '\u0542': sb.append('\u0572'); break;
				case '\u0543': sb.append('\u0573'); break;
				case '\u0544': sb.append('\u0574'); break;
				case '\u0545': sb.append('\u0575'); break;
				case '\u0546': sb.append('\u0576'); break;
				case '\u0547': sb.append('\u0577'); break;
				case '\u0548': sb.append('\u0578'); break;
				case '\u0549': sb.append('\u0579'); break;
				case '\u054A': sb.append('\u057A'); break;
				case '\u054B': sb.append('\u057B'); break;
				case '\u054C': sb.append('\u057C'); break;
				case '\u054D': sb.append('\u057D'); break;
				case '\u054E': sb.append('\u057E'); break;
				case '\u054F': sb.append('\u057F'); break;
				case '\u0550': sb.append('\u0580'); break;
				case '\u0551': sb.append('\u0581'); break;
				case '\u0552': sb.append('\u0582'); break;
				case '\u0553': sb.append('\u0583'); break;
				case '\u0554': sb.append('\u0584'); break;
				case '\u0555': sb.append('\u0585'); break;
				case '\u0556': sb.append('\u0586'); break;
				case '\u10A0': sb.append('\u2D00'); break;
				case '\u10A1': sb.append('\u2D01'); break;
				case '\u10A2': sb.append('\u2D02'); break;
				case '\u10A3': sb.append('\u2D03'); break;
				case '\u10A4': sb.append('\u2D04'); break;
				case '\u10A5': sb.append('\u2D05'); break;
				case '\u10A6': sb.append('\u2D06'); break;
				case '\u10A7': sb.append('\u2D07'); break;
				case '\u10A8': sb.append('\u2D08'); break;
				case '\u10A9': sb.append('\u2D09'); break;
				case '\u10AA': sb.append('\u2D0A'); break;
				case '\u10AB': sb.append('\u2D0B'); break;
				case '\u10AC': sb.append('\u2D0C'); break;
				case '\u10AD': sb.append('\u2D0D'); break;
				case '\u10AE': sb.append('\u2D0E'); break;
				case '\u10AF': sb.append('\u2D0F'); break;
				case '\u10B0': sb.append('\u2D10'); break;
				case '\u10B1': sb.append('\u2D11'); break;
				case '\u10B2': sb.append('\u2D12'); break;
				case '\u10B3': sb.append('\u2D13'); break;
				case '\u10B4': sb.append('\u2D14'); break;
				case '\u10B5': sb.append('\u2D15'); break;
				case '\u10B6': sb.append('\u2D16'); break;
				case '\u10B7': sb.append('\u2D17'); break;
				case '\u10B8': sb.append('\u2D18'); break;
				case '\u10B9': sb.append('\u2D19'); break;
				case '\u10BA': sb.append('\u2D1A'); break;
				case '\u10BB': sb.append('\u2D1B'); break;
				case '\u10BC': sb.append('\u2D1C'); break;
				case '\u10BD': sb.append('\u2D1D'); break;
				case '\u10BE': sb.append('\u2D1E'); break;
				case '\u10BF': sb.append('\u2D1F'); break;
				case '\u10C0': sb.append('\u2D20'); break;
				case '\u10C1': sb.append('\u2D21'); break;
				case '\u10C2': sb.append('\u2D22'); break;
				case '\u10C3': sb.append('\u2D23'); break;
				case '\u10C4': sb.append('\u2D24'); break;
				case '\u10C5': sb.append('\u2D25'); break;
				case '\u10C7': sb.append('\u2D27'); break;
				case '\u10CD': sb.append('\u2D2D'); break;
				case '\u1E00': sb.append('\u1E01'); break;
				case '\u1E02': sb.append('\u1E03'); break;
				case '\u1E04': sb.append('\u1E05'); break;
				case '\u1E06': sb.append('\u1E07'); break;
				case '\u1E08': sb.append('\u1E09'); break;
				case '\u1E0A': sb.append('\u1E0B'); break;
				case '\u1E0C': sb.append('\u1E0D'); break;
				case '\u1E0E': sb.append('\u1E0F'); break;
				case '\u1E10': sb.append('\u1E11'); break;
				case '\u1E12': sb.append('\u1E13'); break;
				case '\u1E14': sb.append('\u1E15'); break;
				case '\u1E16': sb.append('\u1E17'); break;
				case '\u1E18': sb.append('\u1E19'); break;
				case '\u1E1A': sb.append('\u1E1B'); break;
				case '\u1E1C': sb.append('\u1E1D'); break;
				case '\u1E1E': sb.append('\u1E1F'); break;
				case '\u1E20': sb.append('\u1E21'); break;
				case '\u1E22': sb.append('\u1E23'); break;
				case '\u1E24': sb.append('\u1E25'); break;
				case '\u1E26': sb.append('\u1E27'); break;
				case '\u1E28': sb.append('\u1E29'); break;
				case '\u1E2A': sb.append('\u1E2B'); break;
				case '\u1E2C': sb.append('\u1E2D'); break;
				case '\u1E2E': sb.append('\u1E2F'); break;
				case '\u1E30': sb.append('\u1E31'); break;
				case '\u1E32': sb.append('\u1E33'); break;
				case '\u1E34': sb.append('\u1E35'); break;
				case '\u1E36': sb.append('\u1E37'); break;
				case '\u1E38': sb.append('\u1E39'); break;
				case '\u1E3A': sb.append('\u1E3B'); break;
				case '\u1E3C': sb.append('\u1E3D'); break;
				case '\u1E3E': sb.append('\u1E3F'); break;
				case '\u1E40': sb.append('\u1E41'); break;
				case '\u1E42': sb.append('\u1E43'); break;
				case '\u1E44': sb.append('\u1E45'); break;
				case '\u1E46': sb.append('\u1E47'); break;
				case '\u1E48': sb.append('\u1E49'); break;
				case '\u1E4A': sb.append('\u1E4B'); break;
				case '\u1E4C': sb.append('\u1E4D'); break;
				case '\u1E4E': sb.append('\u1E4F'); break;
				case '\u1E50': sb.append('\u1E51'); break;
				case '\u1E52': sb.append('\u1E53'); break;
				case '\u1E54': sb.append('\u1E55'); break;
				case '\u1E56': sb.append('\u1E57'); break;
				case '\u1E58': sb.append('\u1E59'); break;
				case '\u1E5A': sb.append('\u1E5B'); break;
				case '\u1E5C': sb.append('\u1E5D'); break;
				case '\u1E5E': sb.append('\u1E5F'); break;
				case '\u1E60': sb.append('\u1E61'); break;
				case '\u1E62': sb.append('\u1E63'); break;
				case '\u1E64': sb.append('\u1E65'); break;
				case '\u1E66': sb.append('\u1E67'); break;
				case '\u1E68': sb.append('\u1E69'); break;
				case '\u1E6A': sb.append('\u1E6B'); break;
				case '\u1E6C': sb.append('\u1E6D'); break;
				case '\u1E6E': sb.append('\u1E6F'); break;
				case '\u1E70': sb.append('\u1E71'); break;
				case '\u1E72': sb.append('\u1E73'); break;
				case '\u1E74': sb.append('\u1E75'); break;
				case '\u1E76': sb.append('\u1E77'); break;
				case '\u1E78': sb.append('\u1E79'); break;
				case '\u1E7A': sb.append('\u1E7B'); break;
				case '\u1E7C': sb.append('\u1E7D'); break;
				case '\u1E7E': sb.append('\u1E7F'); break;
				case '\u1E80': sb.append('\u1E81'); break;
				case '\u1E82': sb.append('\u1E83'); break;
				case '\u1E84': sb.append('\u1E85'); break;
				case '\u1E86': sb.append('\u1E87'); break;
				case '\u1E88': sb.append('\u1E89'); break;
				case '\u1E8A': sb.append('\u1E8B'); break;
				case '\u1E8C': sb.append('\u1E8D'); break;
				case '\u1E8E': sb.append('\u1E8F'); break;
				case '\u1E90': sb.append('\u1E91'); break;
				case '\u1E92': sb.append('\u1E93'); break;
				case '\u1E94': sb.append('\u1E95'); break;
				case '\u1E9B': sb.append('\u1E61'); break;
				case '\u1EA0': sb.append('\u1EA1'); break;
				case '\u1EA2': sb.append('\u1EA3'); break;
				case '\u1EA4': sb.append('\u1EA5'); break;
				case '\u1EA6': sb.append('\u1EA7'); break;
				case '\u1EA8': sb.append('\u1EA9'); break;
				case '\u1EAA': sb.append('\u1EAB'); break;
				case '\u1EAC': sb.append('\u1EAD'); break;
				case '\u1EAE': sb.append('\u1EAF'); break;
				case '\u1EB0': sb.append('\u1EB1'); break;
				case '\u1EB2': sb.append('\u1EB3'); break;
				case '\u1EB4': sb.append('\u1EB5'); break;
				case '\u1EB6': sb.append('\u1EB7'); break;
				case '\u1EB8': sb.append('\u1EB9'); break;
				case '\u1EBA': sb.append('\u1EBB'); break;
				case '\u1EBC': sb.append('\u1EBD'); break;
				case '\u1EBE': sb.append('\u1EBF'); break;
				case '\u1EC0': sb.append('\u1EC1'); break;
				case '\u1EC2': sb.append('\u1EC3'); break;
				case '\u1EC4': sb.append('\u1EC5'); break;
				case '\u1EC6': sb.append('\u1EC7'); break;
				case '\u1EC8': sb.append('\u1EC9'); break;
				case '\u1ECA': sb.append('\u1ECB'); break;
				case '\u1ECC': sb.append('\u1ECD'); break;
				case '\u1ECE': sb.append('\u1ECF'); break;
				case '\u1ED0': sb.append('\u1ED1'); break;
				case '\u1ED2': sb.append('\u1ED3'); break;
				case '\u1ED4': sb.append('\u1ED5'); break;
				case '\u1ED6': sb.append('\u1ED7'); break;
				case '\u1ED8': sb.append('\u1ED9'); break;
				case '\u1EDA': sb.append('\u1EDB'); break;
				case '\u1EDC': sb.append('\u1EDD'); break;
				case '\u1EDE': sb.append('\u1EDF'); break;
				case '\u1EE0': sb.append('\u1EE1'); break;
				case '\u1EE2': sb.append('\u1EE3'); break;
				case '\u1EE4': sb.append('\u1EE5'); break;
				case '\u1EE6': sb.append('\u1EE7'); break;
				case '\u1EE8': sb.append('\u1EE9'); break;
				case '\u1EEA': sb.append('\u1EEB'); break;
				case '\u1EEC': sb.append('\u1EED'); break;
				case '\u1EEE': sb.append('\u1EEF'); break;
				case '\u1EF0': sb.append('\u1EF1'); break;
				case '\u1EF2': sb.append('\u1EF3'); break;
				case '\u1EF4': sb.append('\u1EF5'); break;
				case '\u1EF6': sb.append('\u1EF7'); break;
				case '\u1EF8': sb.append('\u1EF9'); break;
				case '\u1EFA': sb.append('\u1EFB'); break;
				case '\u1EFC': sb.append('\u1EFD'); break;
				case '\u1EFE': sb.append('\u1EFF'); break;
				case '\u1F08': sb.append('\u1F00'); break;
				case '\u1F09': sb.append('\u1F01'); break;
				case '\u1F0A': sb.append('\u1F02'); break;
				case '\u1F0B': sb.append('\u1F03'); break;
				case '\u1F0C': sb.append('\u1F04'); break;
				case '\u1F0D': sb.append('\u1F05'); break;
				case '\u1F0E': sb.append('\u1F06'); break;
				case '\u1F0F': sb.append('\u1F07'); break;
				case '\u1F18': sb.append('\u1F10'); break;
				case '\u1F19': sb.append('\u1F11'); break;
				case '\u1F1A': sb.append('\u1F12'); break;
				case '\u1F1B': sb.append('\u1F13'); break;
				case '\u1F1C': sb.append('\u1F14'); break;
				case '\u1F1D': sb.append('\u1F15'); break;
				case '\u1F28': sb.append('\u1F20'); break;
				case '\u1F29': sb.append('\u1F21'); break;
				case '\u1F2A': sb.append('\u1F22'); break;
				case '\u1F2B': sb.append('\u1F23'); break;
				case '\u1F2C': sb.append('\u1F24'); break;
				case '\u1F2D': sb.append('\u1F25'); break;
				case '\u1F2E': sb.append('\u1F26'); break;
				case '\u1F2F': sb.append('\u1F27'); break;
				case '\u1F38': sb.append('\u1F30'); break;
				case '\u1F39': sb.append('\u1F31'); break;
				case '\u1F3A': sb.append('\u1F32'); break;
				case '\u1F3B': sb.append('\u1F33'); break;
				case '\u1F3C': sb.append('\u1F34'); break;
				case '\u1F3D': sb.append('\u1F35'); break;
				case '\u1F3E': sb.append('\u1F36'); break;
				case '\u1F3F': sb.append('\u1F37'); break;
				case '\u1F48': sb.append('\u1F40'); break;
				case '\u1F49': sb.append('\u1F41'); break;
				case '\u1F4A': sb.append('\u1F42'); break;
				case '\u1F4B': sb.append('\u1F43'); break;
				case '\u1F4C': sb.append('\u1F44'); break;
				case '\u1F4D': sb.append('\u1F45'); break;
				case '\u1F59': sb.append('\u1F51'); break;
				case '\u1F5B': sb.append('\u1F53'); break;
				case '\u1F5D': sb.append('\u1F55'); break;
				case '\u1F5F': sb.append('\u1F57'); break;
				case '\u1F68': sb.append('\u1F60'); break;
				case '\u1F69': sb.append('\u1F61'); break;
				case '\u1F6A': sb.append('\u1F62'); break;
				case '\u1F6B': sb.append('\u1F63'); break;
				case '\u1F6C': sb.append('\u1F64'); break;
				case '\u1F6D': sb.append('\u1F65'); break;
				case '\u1F6E': sb.append('\u1F66'); break;
				case '\u1F6F': sb.append('\u1F67'); break;
				case '\u1FB8': sb.append('\u1FB0'); break;
				case '\u1FB9': sb.append('\u1FB1'); break;
				case '\u1FBA': sb.append('\u1F70'); break;
				case '\u1FBB': sb.append('\u1F71'); break;
				case '\u1FBE': sb.append('\u03B9'); break;
				case '\u1FC8': sb.append('\u1F72'); break;
				case '\u1FC9': sb.append('\u1F73'); break;
				case '\u1FCA': sb.append('\u1F74'); break;
				case '\u1FCB': sb.append('\u1F75'); break;
				case '\u1FD8': sb.append('\u1FD0'); break;
				case '\u1FD9': sb.append('\u1FD1'); break;
				case '\u1FDA': sb.append('\u1F76'); break;
				case '\u1FDB': sb.append('\u1F77'); break;
				case '\u1FE8': sb.append('\u1FE0'); break;
				case '\u1FE9': sb.append('\u1FE1'); break;
				case '\u1FEA': sb.append('\u1F7A'); break;
				case '\u1FEB': sb.append('\u1F7B'); break;
				case '\u1FEC': sb.append('\u1FE5'); break;
				case '\u1FF8': sb.append('\u1F78'); break;
				case '\u1FF9': sb.append('\u1F79'); break;
				case '\u1FFA': sb.append('\u1F7C'); break;
				case '\u1FFB': sb.append('\u1F7D'); break;
				case '\u2126': sb.append('\u03C9'); break;
				case '\u212A': sb.append('k'); break;
				case '\u212B': sb.append('\u00E5'); break;
				case '\u2132': sb.append('\u214E'); break;
				case '\u2160': sb.append('\u2170'); break;
				case '\u2161': sb.append('\u2171'); break;
				case '\u2162': sb.append('\u2172'); break;
				case '\u2163': sb.append('\u2173'); break;
				case '\u2164': sb.append('\u2174'); break;
				case '\u2165': sb.append('\u2175'); break;
				case '\u2166': sb.append('\u2176'); break;
				case '\u2167': sb.append('\u2177'); break;
				case '\u2168': sb.append('\u2178'); break;
				case '\u2169': sb.append('\u2179'); break;
				case '\u216A': sb.append('\u217A'); break;
				case '\u216B': sb.append('\u217B'); break;
				case '\u216C': sb.append('\u217C'); break;
				case '\u216D': sb.append('\u217D'); break;
				case '\u216E': sb.append('\u217E'); break;
				case '\u216F': sb.append('\u217F'); break;
				case '\u2183': sb.append('\u2184'); break;
				case '\u24B6': sb.append('\u24D0'); break;
				case '\u24B7': sb.append('\u24D1'); break;
				case '\u24B8': sb.append('\u24D2'); break;
				case '\u24B9': sb.append('\u24D3'); break;
				case '\u24BA': sb.append('\u24D4'); break;
				case '\u24BB': sb.append('\u24D5'); break;
				case '\u24BC': sb.append('\u24D6'); break;
				case '\u24BD': sb.append('\u24D7'); break;
				case '\u24BE': sb.append('\u24D8'); break;
				case '\u24BF': sb.append('\u24D9'); break;
				case '\u24C0': sb.append('\u24DA'); break;
				case '\u24C1': sb.append('\u24DB'); break;
				case '\u24C2': sb.append('\u24DC'); break;
				case '\u24C3': sb.append('\u24DD'); break;
				case '\u24C4': sb.append('\u24DE'); break;
				case '\u24C5': sb.append('\u24DF'); break;
				case '\u24C6': sb.append('\u24E0'); break;
				case '\u24C7': sb.append('\u24E1'); break;
				case '\u24C8': sb.append('\u24E2'); break;
				case '\u24C9': sb.append('\u24E3'); break;
				case '\u24CA': sb.append('\u24E4'); break;
				case '\u24CB': sb.append('\u24E5'); break;
				case '\u24CC': sb.append('\u24E6'); break;
				case '\u24CD': sb.append('\u24E7'); break;
				case '\u24CE': sb.append('\u24E8'); break;
				case '\u24CF': sb.append('\u24E9'); break;
				case '\u2C00': sb.append('\u2C30'); break;
				case '\u2C01': sb.append('\u2C31'); break;
				case '\u2C02': sb.append('\u2C32'); break;
				case '\u2C03': sb.append('\u2C33'); break;
				case '\u2C04': sb.append('\u2C34'); break;
				case '\u2C05': sb.append('\u2C35'); break;
				case '\u2C06': sb.append('\u2C36'); break;
				case '\u2C07': sb.append('\u2C37'); break;
				case '\u2C08': sb.append('\u2C38'); break;
				case '\u2C09': sb.append('\u2C39'); break;
				case '\u2C0A': sb.append('\u2C3A'); break;
				case '\u2C0B': sb.append('\u2C3B'); break;
				case '\u2C0C': sb.append('\u2C3C'); break;
				case '\u2C0D': sb.append('\u2C3D'); break;
				case '\u2C0E': sb.append('\u2C3E'); break;
				case '\u2C0F': sb.append('\u2C3F'); break;
				case '\u2C10': sb.append('\u2C40'); break;
				case '\u2C11': sb.append('\u2C41'); break;
				case '\u2C12': sb.append('\u2C42'); break;
				case '\u2C13': sb.append('\u2C43'); break;
				case '\u2C14': sb.append('\u2C44'); break;
				case '\u2C15': sb.append('\u2C45'); break;
				case '\u2C16': sb.append('\u2C46'); break;
				case '\u2C17': sb.append('\u2C47'); break;
				case '\u2C18': sb.append('\u2C48'); break;
				case '\u2C19': sb.append('\u2C49'); break;
				case '\u2C1A': sb.append('\u2C4A'); break;
				case '\u2C1B': sb.append('\u2C4B'); break;
				case '\u2C1C': sb.append('\u2C4C'); break;
				case '\u2C1D': sb.append('\u2C4D'); break;
				case '\u2C1E': sb.append('\u2C4E'); break;
				case '\u2C1F': sb.append('\u2C4F'); break;
				case '\u2C20': sb.append('\u2C50'); break;
				case '\u2C21': sb.append('\u2C51'); break;
				case '\u2C22': sb.append('\u2C52'); break;
				case '\u2C23': sb.append('\u2C53'); break;
				case '\u2C24': sb.append('\u2C54'); break;
				case '\u2C25': sb.append('\u2C55'); break;
				case '\u2C26': sb.append('\u2C56'); break;
				case '\u2C27': sb.append('\u2C57'); break;
				case '\u2C28': sb.append('\u2C58'); break;
				case '\u2C29': sb.append('\u2C59'); break;
				case '\u2C2A': sb.append('\u2C5A'); break;
				case '\u2C2B': sb.append('\u2C5B'); break;
				case '\u2C2C': sb.append('\u2C5C'); break;
				case '\u2C2D': sb.append('\u2C5D'); break;
				case '\u2C2E': sb.append('\u2C5E'); break;
				case '\u2C60': sb.append('\u2C61'); break;
				case '\u2C62': sb.append('\u026B'); break;
				case '\u2C63': sb.append('\u1D7D'); break;
				case '\u2C64': sb.append('\u027D'); break;
				case '\u2C67': sb.append('\u2C68'); break;
				case '\u2C69': sb.append('\u2C6A'); break;
				case '\u2C6B': sb.append('\u2C6C'); break;
				case '\u2C6D': sb.append('\u0251'); break;
				case '\u2C6E': sb.append('\u0271'); break;
				case '\u2C6F': sb.append('\u0250'); break;
				case '\u2C70': sb.append('\u0252'); break;
				case '\u2C72': sb.append('\u2C73'); break;
				case '\u2C75': sb.append('\u2C76'); break;
				case '\u2C7E': sb.append('\u023F'); break;
				case '\u2C7F': sb.append('\u0240'); break;
				case '\u2C80': sb.append('\u2C81'); break;
				case '\u2C82': sb.append('\u2C83'); break;
				case '\u2C84': sb.append('\u2C85'); break;
				case '\u2C86': sb.append('\u2C87'); break;
				case '\u2C88': sb.append('\u2C89'); break;
				case '\u2C8A': sb.append('\u2C8B'); break;
				case '\u2C8C': sb.append('\u2C8D'); break;
				case '\u2C8E': sb.append('\u2C8F'); break;
				case '\u2C90': sb.append('\u2C91'); break;
				case '\u2C92': sb.append('\u2C93'); break;
				case '\u2C94': sb.append('\u2C95'); break;
				case '\u2C96': sb.append('\u2C97'); break;
				case '\u2C98': sb.append('\u2C99'); break;
				case '\u2C9A': sb.append('\u2C9B'); break;
				case '\u2C9C': sb.append('\u2C9D'); break;
				case '\u2C9E': sb.append('\u2C9F'); break;
				case '\u2CA0': sb.append('\u2CA1'); break;
				case '\u2CA2': sb.append('\u2CA3'); break;
				case '\u2CA4': sb.append('\u2CA5'); break;
				case '\u2CA6': sb.append('\u2CA7'); break;
				case '\u2CA8': sb.append('\u2CA9'); break;
				case '\u2CAA': sb.append('\u2CAB'); break;
				case '\u2CAC': sb.append('\u2CAD'); break;
				case '\u2CAE': sb.append('\u2CAF'); break;
				case '\u2CB0': sb.append('\u2CB1'); break;
				case '\u2CB2': sb.append('\u2CB3'); break;
				case '\u2CB4': sb.append('\u2CB5'); break;
				case '\u2CB6': sb.append('\u2CB7'); break;
				case '\u2CB8': sb.append('\u2CB9'); break;
				case '\u2CBA': sb.append('\u2CBB'); break;
				case '\u2CBC': sb.append('\u2CBD'); break;
				case '\u2CBE': sb.append('\u2CBF'); break;
				case '\u2CC0': sb.append('\u2CC1'); break;
				case '\u2CC2': sb.append('\u2CC3'); break;
				case '\u2CC4': sb.append('\u2CC5'); break;
				case '\u2CC6': sb.append('\u2CC7'); break;
				case '\u2CC8': sb.append('\u2CC9'); break;
				case '\u2CCA': sb.append('\u2CCB'); break;
				case '\u2CCC': sb.append('\u2CCD'); break;
				case '\u2CCE': sb.append('\u2CCF'); break;
				case '\u2CD0': sb.append('\u2CD1'); break;
				case '\u2CD2': sb.append('\u2CD3'); break;
				case '\u2CD4': sb.append('\u2CD5'); break;
				case '\u2CD6': sb.append('\u2CD7'); break;
				case '\u2CD8': sb.append('\u2CD9'); break;
				case '\u2CDA': sb.append('\u2CDB'); break;
				case '\u2CDC': sb.append('\u2CDD'); break;
				case '\u2CDE': sb.append('\u2CDF'); break;
				case '\u2CE0': sb.append('\u2CE1'); break;
				case '\u2CE2': sb.append('\u2CE3'); break;
				case '\u2CEB': sb.append('\u2CEC'); break;
				case '\u2CED': sb.append('\u2CEE'); break;
				case '\u2CF2': sb.append('\u2CF3'); break;
				case '\uA640': sb.append('\uA641'); break;
				case '\uA642': sb.append('\uA643'); break;
				case '\uA644': sb.append('\uA645'); break;
				case '\uA646': sb.append('\uA647'); break;
				case '\uA648': sb.append('\uA649'); break;
				case '\uA64A': sb.append('\uA64B'); break;
				case '\uA64C': sb.append('\uA64D'); break;
				case '\uA64E': sb.append('\uA64F'); break;
				case '\uA650': sb.append('\uA651'); break;
				case '\uA652': sb.append('\uA653'); break;
				case '\uA654': sb.append('\uA655'); break;
				case '\uA656': sb.append('\uA657'); break;
				case '\uA658': sb.append('\uA659'); break;
				case '\uA65A': sb.append('\uA65B'); break;
				case '\uA65C': sb.append('\uA65D'); break;
				case '\uA65E': sb.append('\uA65F'); break;
				case '\uA660': sb.append('\uA661'); break;
				case '\uA662': sb.append('\uA663'); break;
				case '\uA664': sb.append('\uA665'); break;
				case '\uA666': sb.append('\uA667'); break;
				case '\uA668': sb.append('\uA669'); break;
				case '\uA66A': sb.append('\uA66B'); break;
				case '\uA66C': sb.append('\uA66D'); break;
				case '\uA680': sb.append('\uA681'); break;
				case '\uA682': sb.append('\uA683'); break;
				case '\uA684': sb.append('\uA685'); break;
				case '\uA686': sb.append('\uA687'); break;
				case '\uA688': sb.append('\uA689'); break;
				case '\uA68A': sb.append('\uA68B'); break;
				case '\uA68C': sb.append('\uA68D'); break;
				case '\uA68E': sb.append('\uA68F'); break;
				case '\uA690': sb.append('\uA691'); break;
				case '\uA692': sb.append('\uA693'); break;
				case '\uA694': sb.append('\uA695'); break;
				case '\uA696': sb.append('\uA697'); break;
				case '\uA698': sb.append('\uA699'); break;
				case '\uA69A': sb.append('\uA69B'); break;
				case '\uA722': sb.append('\uA723'); break;
				case '\uA724': sb.append('\uA725'); break;
				case '\uA726': sb.append('\uA727'); break;
				case '\uA728': sb.append('\uA729'); break;
				case '\uA72A': sb.append('\uA72B'); break;
				case '\uA72C': sb.append('\uA72D'); break;
				case '\uA72E': sb.append('\uA72F'); break;
				case '\uA732': sb.append('\uA733'); break;
				case '\uA734': sb.append('\uA735'); break;
				case '\uA736': sb.append('\uA737'); break;
				case '\uA738': sb.append('\uA739'); break;
				case '\uA73A': sb.append('\uA73B'); break;
				case '\uA73C': sb.append('\uA73D'); break;
				case '\uA73E': sb.append('\uA73F'); break;
				case '\uA740': sb.append('\uA741'); break;
				case '\uA742': sb.append('\uA743'); break;
				case '\uA744': sb.append('\uA745'); break;
				case '\uA746': sb.append('\uA747'); break;
				case '\uA748': sb.append('\uA749'); break;
				case '\uA74A': sb.append('\uA74B'); break;
				case '\uA74C': sb.append('\uA74D'); break;
				case '\uA74E': sb.append('\uA74F'); break;
				case '\uA750': sb.append('\uA751'); break;
				case '\uA752': sb.append('\uA753'); break;
				case '\uA754': sb.append('\uA755'); break;
				case '\uA756': sb.append('\uA757'); break;
				case '\uA758': sb.append('\uA759'); break;
				case '\uA75A': sb.append('\uA75B'); break;
				case '\uA75C': sb.append('\uA75D'); break;
				case '\uA75E': sb.append('\uA75F'); break;
				case '\uA760': sb.append('\uA761'); break;
				case '\uA762': sb.append('\uA763'); break;
				case '\uA764': sb.append('\uA765'); break;
				case '\uA766': sb.append('\uA767'); break;
				case '\uA768': sb.append('\uA769'); break;
				case '\uA76A': sb.append('\uA76B'); break;
				case '\uA76C': sb.append('\uA76D'); break;
				case '\uA76E': sb.append('\uA76F'); break;
				case '\uA779': sb.append('\uA77A'); break;
				case '\uA77B': sb.append('\uA77C'); break;
				case '\uA77D': sb.append('\u1D79'); break;
				case '\uA77E': sb.append('\uA77F'); break;
				case '\uA780': sb.append('\uA781'); break;
				case '\uA782': sb.append('\uA783'); break;
				case '\uA784': sb.append('\uA785'); break;
				case '\uA786': sb.append('\uA787'); break;
				case '\uA78B': sb.append('\uA78C'); break;
				case '\uA78D': sb.append('\u0265'); break;
				case '\uA790': sb.append('\uA791'); break;
				case '\uA792': sb.append('\uA793'); break;
				case '\uA796': sb.append('\uA797'); break;
				case '\uA798': sb.append('\uA799'); break;
				case '\uA79A': sb.append('\uA79B'); break;
				case '\uA79C': sb.append('\uA79D'); break;
				case '\uA79E': sb.append('\uA79F'); break;
				case '\uA7A0': sb.append('\uA7A1'); break;
				case '\uA7A2': sb.append('\uA7A3'); break;
				case '\uA7A4': sb.append('\uA7A5'); break;
				case '\uA7A6': sb.append('\uA7A7'); break;
				case '\uA7A8': sb.append('\uA7A9'); break;
				case '\uA7AA': sb.append('\u0266'); break;
				case '\uA7AB': sb.append('\u025C'); break;
				case '\uA7AC': sb.append('\u0261'); break;
				case '\uA7AD': sb.append('\u026C'); break;
				case '\uA7B0': sb.append('\u029E'); break;
				case '\uA7B1': sb.append('\u0287'); break;
				case '\uFF21': sb.append('\uFF41'); break;
				case '\uFF22': sb.append('\uFF42'); break;
				case '\uFF23': sb.append('\uFF43'); break;
				case '\uFF24': sb.append('\uFF44'); break;
				case '\uFF25': sb.append('\uFF45'); break;
				case '\uFF26': sb.append('\uFF46'); break;
				case '\uFF27': sb.append('\uFF47'); break;
				case '\uFF28': sb.append('\uFF48'); break;
				case '\uFF29': sb.append('\uFF49'); break;
				case '\uFF2A': sb.append('\uFF4A'); break;
				case '\uFF2B': sb.append('\uFF4B'); break;
				case '\uFF2C': sb.append('\uFF4C'); break;
				case '\uFF2D': sb.append('\uFF4D'); break;
				case '\uFF2E': sb.append('\uFF4E'); break;
				case '\uFF2F': sb.append('\uFF4F'); break;
				case '\uFF30': sb.append('\uFF50'); break;
				case '\uFF31': sb.append('\uFF51'); break;
				case '\uFF32': sb.append('\uFF52'); break;
				case '\uFF33': sb.append('\uFF53'); break;
				case '\uFF34': sb.append('\uFF54'); break;
				case '\uFF35': sb.append('\uFF55'); break;
				case '\uFF36': sb.append('\uFF56'); break;
				case '\uFF37': sb.append('\uFF57'); break;
				case '\uFF38': sb.append('\uFF58'); break;
				case '\uFF39': sb.append('\uFF59'); break;
				case '\uFF3A': sb.append('\uFF5A'); break;
				case '\u00DF': sb.append("ss"); break;
				case '\u0130': sb.append("i\u0307"); break;
				case '\u0149': sb.append("\u02BCn"); break;
				case '\u01F0': sb.append("j\u030C"); break;
				case '\u0390': sb.append("\u03B9\u0308\u0301"); break;
				case '\u03B0': sb.append("\u03C5\u0308\u0301"); break;
				case '\u0587': sb.append("\u0565\u0582"); break;
				case '\u1E96': sb.append("h\u0331"); break;
				case '\u1E97': sb.append("t\u0308"); break;
				case '\u1E98': sb.append("w\u030A"); break;
				case '\u1E99': sb.append("y\u030A"); break;
				case '\u1E9A': sb.append("a\u02BE"); break;
				case '\u1E9E': sb.append("ss"); break;
				case '\u1F50': sb.append("\u03C5\u0313"); break;
				case '\u1F52': sb.append("\u03C5\u0313\u0300"); break;
				case '\u1F54': sb.append("\u03C5\u0313\u0301"); break;
				case '\u1F56': sb.append("\u03C5\u0313\u0342"); break;
				case '\u1F80': sb.append("\u1F00\u03B9"); break;
				case '\u1F81': sb.append("\u1F01\u03B9"); break;
				case '\u1F82': sb.append("\u1F02\u03B9"); break;
				case '\u1F83': sb.append("\u1F03\u03B9"); break;
				case '\u1F84': sb.append("\u1F04\u03B9"); break;
				case '\u1F85': sb.append("\u1F05\u03B9"); break;
				case '\u1F86': sb.append("\u1F06\u03B9"); break;
				case '\u1F87': sb.append("\u1F07\u03B9"); break;
				case '\u1F88': sb.append("\u1F00\u03B9"); break;
				case '\u1F89': sb.append("\u1F01\u03B9"); break;
				case '\u1F8A': sb.append("\u1F02\u03B9"); break;
				case '\u1F8B': sb.append("\u1F03\u03B9"); break;
				case '\u1F8C': sb.append("\u1F04\u03B9"); break;
				case '\u1F8D': sb.append("\u1F05\u03B9"); break;
				case '\u1F8E': sb.append("\u1F06\u03B9"); break;
				case '\u1F8F': sb.append("\u1F07\u03B9"); break;
				case '\u1F90': sb.append("\u1F20\u03B9"); break;
				case '\u1F91': sb.append("\u1F21\u03B9"); break;
				case '\u1F92': sb.append("\u1F22\u03B9"); break;
				case '\u1F93': sb.append("\u1F23\u03B9"); break;
				case '\u1F94': sb.append("\u1F24\u03B9"); break;
				case '\u1F95': sb.append("\u1F25\u03B9"); break;
				case '\u1F96': sb.append("\u1F26\u03B9"); break;
				case '\u1F97': sb.append("\u1F27\u03B9"); break;
				case '\u1F98': sb.append("\u1F20\u03B9"); break;
				case '\u1F99': sb.append("\u1F21\u03B9"); break;
				case '\u1F9A': sb.append("\u1F22\u03B9"); break;
				case '\u1F9B': sb.append("\u1F23\u03B9"); break;
				case '\u1F9C': sb.append("\u1F24\u03B9"); break;
				case '\u1F9D': sb.append("\u1F25\u03B9"); break;
				case '\u1F9E': sb.append("\u1F26\u03B9"); break;
				case '\u1F9F': sb.append("\u1F27\u03B9"); break;
				case '\u1FA0': sb.append("\u1F60\u03B9"); break;
				case '\u1FA1': sb.append("\u1F61\u03B9"); break;
				case '\u1FA2': sb.append("\u1F62\u03B9"); break;
				case '\u1FA3': sb.append("\u1F63\u03B9"); break;
				case '\u1FA4': sb.append("\u1F64\u03B9"); break;
				case '\u1FA5': sb.append("\u1F65\u03B9"); break;
				case '\u1FA6': sb.append("\u1F66\u03B9"); break;
				case '\u1FA7': sb.append("\u1F67\u03B9"); break;
				case '\u1FA8': sb.append("\u1F60\u03B9"); break;
				case '\u1FA9': sb.append("\u1F61\u03B9"); break;
				case '\u1FAA': sb.append("\u1F62\u03B9"); break;
				case '\u1FAB': sb.append("\u1F63\u03B9"); break;
				case '\u1FAC': sb.append("\u1F64\u03B9"); break;
				case '\u1FAD': sb.append("\u1F65\u03B9"); break;
				case '\u1FAE': sb.append("\u1F66\u03B9"); break;
				case '\u1FAF': sb.append("\u1F67\u03B9"); break;
				case '\u1FB2': sb.append("\u1F70\u03B9"); break;
				case '\u1FB3': sb.append("\u03B1\u03B9"); break;
				case '\u1FB4': sb.append("\u03AC\u03B9"); break;
				case '\u1FB6': sb.append("\u03B1\u0342"); break;
				case '\u1FB7': sb.append("\u03B1\u0342\u03B9"); break;
				case '\u1FBC': sb.append("\u03B1\u03B9"); break;
				case '\u1FC2': sb.append("\u1F74\u03B9"); break;
				case '\u1FC3': sb.append("\u03B7\u03B9"); break;
				case '\u1FC4': sb.append("\u03AE\u03B9"); break;
				case '\u1FC6': sb.append("\u03B7\u0342"); break;
				case '\u1FC7': sb.append("\u03B7\u0342\u03B9"); break;
				case '\u1FCC': sb.append("\u03B7\u03B9"); break;
				case '\u1FD2': sb.append("\u03B9\u0308\u0300"); break;
				case '\u1FD3': sb.append("\u03B9\u0308\u0301"); break;
				case '\u1FD6': sb.append("\u03B9\u0342"); break;
				case '\u1FD7': sb.append("\u03B9\u0308\u0342"); break;
				case '\u1FE2': sb.append("\u03C5\u0308\u0300"); break;
				case '\u1FE3': sb.append("\u03C5\u0308\u0301"); break;
				case '\u1FE4': sb.append("\u03C1\u0313"); break;
				case '\u1FE6': sb.append("\u03C5\u0342"); break;
				case '\u1FE7': sb.append("\u03C5\u0308\u0342"); break;
				case '\u1FF2': sb.append("\u1F7C\u03B9"); break;
				case '\u1FF3': sb.append("\u03C9\u03B9"); break;
				case '\u1FF4': sb.append("\u03CE\u03B9"); break;
				case '\u1FF6': sb.append("\u03C9\u0342"); break;
				case '\u1FF7': sb.append("\u03C9\u0342\u03B9"); break;
				case '\u1FFC': sb.append("\u03C9\u03B9"); break;
				case '\uFB00': sb.append("ff"); break;
				case '\uFB01': sb.append("fi"); break;
				case '\uFB02': sb.append("fl"); break;
				case '\uFB03': sb.append("ffi"); break;
				case '\uFB04': sb.append("ffl"); break;
				case '\uFB05': sb.append("st"); break;
				case '\uFB06': sb.append("st"); break;
				case '\uFB13': sb.append("\u0574\u0576"); break;
				case '\uFB14': sb.append("\u0574\u0565"); break;
				case '\uFB15': sb.append("\u0574\u056B"); break;
				case '\uFB16': sb.append("\u057E\u0576"); break;
				case '\uFB17': sb.append("\u0574\u056D"); break;
				case ' ':
				case '\t':
				case '\r':
				case '\n':
					if (prev != ' ' && prev != '\t' && prev != '\r' && prev != '\n') {
						sb.append(' ');
						break;
					}
				default: sb.append(c);
				}
			}
			prev = c;
		}
		return sb.toString();
	 }
}
