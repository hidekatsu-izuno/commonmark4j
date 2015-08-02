package net.arnx.commonmark4j;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class MainTest {

	@Test
	public void test() throws IOException {
		Path src = Paths.get("base/spec/spec.txt");
		Path dest = Files.createTempFile("test", ".tmp");

		try {
			Main.main(new String[] {
				src.toAbsolutePath().toString(),
				dest.toAbsolutePath().toString()
			});

			Path result = Paths.get("base/result/spec.html");
			assertEquals(Files.readAllLines(result), Files.readAllLines(dest));
		} finally {
			Files.delete(dest);
		}
	}

}
