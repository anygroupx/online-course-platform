package com.course.platform.infra.projectclient;

import static org.junit.jupiter.api.Assertions.*;

import com.course.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.CRC32;

class SafeRasterCodecTest {
    private final SafeRasterCodec codec = new SafeRasterCodec();

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg"})
    void acceptsRealRasterAndAlwaysReturnsMetadataFreePng(String format) throws Exception {
        BufferedImage image = new BufferedImage(6, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff223344);
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, input));
        image.flush();

        BufferedImage originalDecoded = ImageIO.read(new ByteArrayInputStream(input.toByteArray()));
        int decodedPixel = originalDecoded.getRGB(0, 0);
        originalDecoded.flush();
        SafeRasterCodec.Image clean = codec.normalizeDataUrl(dataUrl(format, input.toByteArray()));

        assertEquals(6, clean.width());
        assertEquals(4, clean.height());
        assertEquals((byte) 0x89, clean.png()[0]);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(clean.png()));
        assertEquals(decodedPixel, decoded.getRGB(0, 0));
        decoded.flush();
        assertEquals("SafeRaster[REDACTED]", clean.toString());
    }

    @Test
    void stripsPngTextMetadataAndTrailingPayload() throws Exception {
        var writer = ImageIO.getImageWritersByFormatName("png").next();
        var input = new ByteArrayOutputStream();
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        var metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), writer.getDefaultWriteParam());
        var root = new IIOMetadataNode("javax_imageio_png_1.0");
        var text = new IIOMetadataNode("tEXt");
        var entry = new IIOMetadataNode("tEXtEntry");
        entry.setAttribute("keyword", "Comment");
        entry.setAttribute("value", "private-face-metadata-fixture");
        text.appendChild(entry);
        root.appendChild(text);
        metadata.mergeTree("javax_imageio_png_1.0", root);
        try (var output = ImageIO.createImageOutputStream(input)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, metadata), writer.getDefaultWriteParam());
        } finally {
            writer.dispose();
            image.flush();
        }
        input.write("<script>private-trailing-fixture</script>".getBytes(StandardCharsets.UTF_8));
        assertTrue(input.toString(StandardCharsets.ISO_8859_1).contains("private-face-metadata-fixture"));

        SafeRasterCodec.Image clean = codec.normalizeDataUrl(dataUrl("png", input.toByteArray()));
        String normalized = new String(clean.png(), StandardCharsets.ISO_8859_1);
        assertFalse(normalized.contains("private-face-metadata-fixture"));
        assertFalse(normalized.contains("private-trailing-fixture"));
        assertFalse(normalized.contains("tEXt"));
    }

    @Test
    void stripsJpegExifAndCommentsBeforeReturningPng() throws Exception {
        byte[] original = raster("jpeg");
        var annotated = new ByteArrayOutputStream();
        annotated.write(original, 0, 2);
        for (int marker : new int[] {0xe1, 0xfe}) {
            byte[] metadata = (marker == 0xe1 ? "Exif\0\0private-gps-fixture" : "private-face-comment").getBytes(StandardCharsets.UTF_8);
            annotated.write(0xff);
            annotated.write(marker);
            annotated.write((metadata.length + 2) >> 8);
            annotated.write((metadata.length + 2) & 0xff);
            annotated.write(metadata);
        }
        annotated.write(original, 2, original.length - 2);
        var clean = codec.normalizeDataUrl(dataUrl("jpeg", annotated.toByteArray()));
        String output = new String(clean.png(), StandardCharsets.ISO_8859_1);
        assertFalse(output.contains("Exif"));
        assertFalse(output.contains("private-gps-fixture"));
        assertFalse(output.contains("private-face-comment"));
        assertNotNull(ImageIO.read(new ByteArrayInputStream(clean.png())));
    }

    @Test
    void rejectsFakeMimeMismatchesTruncatedImagesAndPixelBombs() throws Exception {
        byte[] png = raster("png");
        assertThrows(BusinessException.class,
                () -> codec.normalizeDataUrl("data:image/png;base64," + Base64.getEncoder().encodeToString("not png".getBytes(StandardCharsets.UTF_8))));
        assertThrows(BusinessException.class,
                () -> codec.normalizeDataUrl("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(png)));
        assertThrows(BusinessException.class,
                () -> codec.normalizeDataUrl(dataUrl("png", java.util.Arrays.copyOf(png, 20))));

        ByteBuffer.wrap(png, 16, 8).putInt(4096).putInt(4096);
        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);
        ByteBuffer.wrap(png, 29, 4).putInt((int) crc.getValue());
        assertThrows(BusinessException.class, () -> codec.normalizeDataUrl(dataUrl("png", png)));
    }

    @Test
    void limitsUploadBytesBeforeImageDecode() {
        assertThrows(BusinessException.class, () -> codec.normalizeDataUrl(
                "data:image/png;base64," + "A".repeat(4 * ((SafeRasterCodec.MAX_INPUT + 2) / 3) + 24)));
    }

    private static byte[] raster(String format) throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        image.flush();
        return output.toByteArray();
    }

    private static String dataUrl(String format, byte[] bytes) {
        String mime = "jpeg".equals(format) ? "jpeg" : format;
        return "data:image/" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
