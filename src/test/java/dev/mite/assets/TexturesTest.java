package dev.mite.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.Inflater;
import org.junit.jupiter.api.Test;

/** Every PNG must decode to the size its header announces (the client shows a missing texture otherwise). */
class TexturesTest {
    @Test
    void pngsAreConsistent() throws Exception {
        List<Path> pngs;
        try (var s = Files.walk(Path.of("src/main/resources/assets"))) {
            pngs = s.filter(p -> p.toString().endsWith(".png")).toList();
        }
        for (Path p : pngs) {
            ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(p));
            int w = b.getInt(16), h = b.getInt(20), depth = b.get(24), colour = b.get(25);
            int channels = switch (colour) {
                case 0, 3 -> 1; // grey, palette
                case 2 -> 3; // RGB
                case 4 -> 2; // grey + alpha
                case 6 -> 4; // RGBA
                default -> throw new AssertionError(p + ": PNG colour type " + colour);
            };
            assertEquals(0, b.get(28), p + ": interlaced PNG");
            int expected = h * (1 + (w * channels * depth + 7) / 8); // filter byte + packed pixels per row
            ByteArrayOutputStream idat = new ByteArrayOutputStream();
            for (int i = 8; i < b.limit(); ) {
                int len = b.getInt(i);
                if (new String(b.array(), i + 4, 4).equals("IDAT")) idat.write(b.array(), i + 8, len);
                i += 12 + len;
            }
            Inflater inf = new Inflater();
            inf.setInput(idat.toByteArray());
            byte[] out = new byte[expected + 1];
            int n = 0;
            while (!inf.finished() && n < out.length) {
                int read = inf.inflate(out, n, out.length - n);
                if (read == 0 && (inf.needsInput() || inf.needsDictionary())) break; // truncated data: let the size check fail
                n += read;
            }
            assertEquals(expected, n, p.toString());
        }
    }
}
