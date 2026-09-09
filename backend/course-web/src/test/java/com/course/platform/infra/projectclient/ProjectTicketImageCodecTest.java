package com.course.platform.infra.projectclient;

import static org.junit.jupiter.api.Assertions.*;
import com.course.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ProjectTicketImageCodecTest {
    private final ProjectTicketImageCodec codec = new ProjectTicketImageCodec();
    public static byte[] fixture(String format, int color) throws IOException {
        var image = new BufferedImage(6, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, color);
        var out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, out));
        image.flush();
        return out.toByteArray();
    }
    public static String data(String format, int color) throws IOException {
        return "data:image/" + format + ";base64," + Base64.getEncoder().encodeToString(fixture(format, color));
    }

    @ParameterizedTest @ValueSource(strings = {"png", "jpeg"})
    void acceptsRasterAndReturnsOnlyNewPngPixels(String format) throws Exception {
        var clean = codec.normalize(data(format, 0xFF223344));
        assertEquals(6, clean.width()); assertEquals(4, clean.height());
        assertEquals((byte) 0x89, clean.png()[0]);
        assertEquals(6, ImageIO.read(new ByteArrayInputStream(clean.png())).getWidth());
        assertFalse(clean.toString().contains("base64"));
    }

    @Test void stripsPngMetadataAndTrailingActivePayload() throws Exception {
        var writer = ImageIO.getImageWritersByFormatName("png").next();
        var out = new ByteArrayOutputStream();
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        var meta = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), writer.getDefaultWriteParam());
        var root = new IIOMetadataNode("javax_imageio_png_1.0");
        var text = new IIOMetadataNode("tEXt"); var entry = new IIOMetadataNode("tEXtEntry");
        entry.setAttribute("keyword", "Comment"); entry.setAttribute("value", "GPS-private-fixture-comment");
        text.appendChild(entry); root.appendChild(text); meta.mergeTree("javax_imageio_png_1.0", root);
        try (var stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream); writer.write(null, new IIOImage(image, null, meta), writer.getDefaultWriteParam());
        } finally { writer.dispose(); image.flush(); }
        out.write("<script>private-fixture-secret</script>".getBytes(StandardCharsets.UTF_8));
        assertTrue(out.toString(StandardCharsets.ISO_8859_1).contains("GPS-private"));
        var clean = codec.normalize("data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray()));
        String result = new String(clean.png(), StandardCharsets.ISO_8859_1);
        assertFalse(result.contains("GPS-private")); assertFalse(result.contains("private-fixture-secret")); assertFalse(result.contains("tEXt"));
    }

    @ParameterizedTest @ValueSource(strings = {"https://unused.example/x.png", "data:image/svg+xml;base64,PHN2Zy8+", "data:image/gif;base64,R0lGODlh", "file:///tmp/image.png", "data:text/html;base64,YQ==", "data:image/png;base64,@@@", "data:image/png;base64,", " "})
    void rejectsUrlsVectorsAndBadDataWithoutFetching(String data) {
        assertThrows(BusinessException.class, () -> codec.normalize(data));
    }

    @Test void doesNotTrustCallerMimeOrAcceptTruncatedHeaders() throws Exception {
        assertThrows(BusinessException.class, () -> codec.normalize(data("jpeg", 0).replace("image/jpeg", "image/png")));
        assertThrows(BusinessException.class, () -> codec.normalize("data:image/png;base64," + Base64.getEncoder().encodeToString(Arrays.copyOf(fixture("png", 0), 20))));
    }

    @Test void rejectsCompressedPixelBombBeforeAllocatingPixels() throws Exception {
        byte[] bytes = fixture("png", 0);
        java.nio.ByteBuffer.wrap(bytes, 16, 8).putInt(4096).putInt(4096);
        var crc = new java.util.zip.CRC32(); crc.update(bytes, 12, 17);
        java.nio.ByteBuffer.wrap(bytes, 29, 4).putInt((int) crc.getValue());
        assertThrows(BusinessException.class, () -> codec.normalize("data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)));
        java.nio.ByteBuffer.wrap(bytes, 16, 8).putInt(4097).putInt(1);
        crc.reset(); crc.update(bytes, 12, 17); java.nio.ByteBuffer.wrap(bytes, 29, 4).putInt((int) crc.getValue());
        assertThrows(BusinessException.class, () -> codec.normalize("data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)));
    }

    @Test void saturatedDecoderRejectsInsteadOfQueueingMorePixelBuffers() throws Exception {
        var field = ProjectTicketImageCodec.class.getDeclaredField("decoding"); field.setAccessible(true);
        var slots = (java.util.concurrent.Semaphore) field.get(codec); assertTrue(slots.tryAcquire(2));
        try { assertThrows(BusinessException.class, () -> codec.normalize(data("png", 0))); }
        finally { slots.release(2); }
        assertNotNull(codec.normalize(data("png", 0)));
    }

    @Test void boundsBase64BeforeDecodingAndAllowsNoImage() {
        assertNull(codec.normalize(null)); assertNull(codec.normalize(""));
        assertThrows(BusinessException.class, () -> codec.normalize("data:image/png;base64," + "A".repeat(2_796_257)));
    }
    @Test void repeatedNormalizationPreservesLowAlphaPixelIdentity() throws Exception {
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x01020304); image.setRGB(1, 0, 0x117AC310); image.setRGB(2, 0, 0x00FE13ED);
        var out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out); image.flush();
        var first = codec.normalize("data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray()));
        var second = codec.normalizeSupplier(ProjectTicketImageCodec.dataUrl(first));
        assertArrayEquals(first.png(), second.png());
        var decoded = ImageIO.read(new ByteArrayInputStream(second.png()));
        assertEquals(0x01020304, decoded.getRGB(0, 0)); assertEquals(0x00FE13ED, decoded.getRGB(2, 0)); decoded.flush();
    }

}
