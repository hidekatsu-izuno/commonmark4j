commonmark4j
=============

Commonmark4j is a [CommonMark](http://commonmark.org/) transformer.

The specification of this implementaion is defined by [CommonMark Spec](http://spec.commonmark.org/).

The official reference implementation was written in C and JavaScript.
Commonmark4j is ported from [commonmark.js](https://github.com/jgm/commonmark.js).

Command Line
--------------

Commonmark4j can run from command line.

```
Usage: java -jar commonmark4j-[version].jar [options] [source] [dest]
Options:");
  -help               print this help message.
  -safe               remove dangerous code.
  -smart              use smart characters.
  -softbreak <text>   set softbreak characters. (default: \\n)
  -sourcepos          include source position information.
  -time               print total time.");
  -format {html,xml}  output as specified format. (default: html)
```

API Description
--------------

The basic usage, use a CMarkTransformer class.

```java
CMarkTransformer transformer = CMarkTransformer.newTransformer();

try (BufferedReader in = Files.newBufferedReader(Paths.get("..."));
    BufferedWriter out = Files.newBufferedWriter(Paths.get("..."))) {

    transformer.transform(in, out);
}
```

If you want to use in same way as commonmark.js, you can use CMarkParser and CMarkRenderer.

```java
CmarkNode node;

CMarkParser parser = CMarkParser.newParser();
try (BufferedReader in = Files.newBufferedReader(Paths.get("...")) {
    node = parser.parse(in);
}

CMarkRenderer renderer = CMarkRenderer.newHtmlRenderer();
try (BufferedWriter out = Files.newBufferedWriter(Paths.get("..."))) {
    renderer.render(out);
}
```

Maven
--------------

Commonmark4j is scheduled to become available in Maven central repository.

```xml
<groupId>net.arnx</groupId>
<artifactId>commonmark4j</artifactId>
```

License
--------------

Commonmark4j is licensed by The  2 clause BSD License, see [LICENSE](LICENSE).

