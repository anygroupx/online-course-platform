package com.course.platform.infra.projectclient;

import com.course.platform.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;

/** Bounded local raster decoder. Never accepts URLs, writes files, or preserves source metadata. */
@Component
public class ProjectTicketImageCodec {
    public static final int MAX_INPUT = 2 * 1024 * 1024;
    public static final int MAX_OUTPUT = 4 * 1024 * 1024;
    public static final int MAX_PIXELS = 4_000_000;
    public static final int MAX_EDGE = 4096;

    private final java.util.concurrent.Semaphore decoding = new java.util.concurrent.Semaphore(2);

    public record Image(byte[] png, int width, int height) {
        @Override public String toString() { return "TicketImage[REDACTED]"; }
    }

    public Image normalize(String data) { return normalize(data, MAX_INPUT); }

    /** Bounded supplier echoes may contain a PNG expanded by our normalization step. */
    public Image normalizeSupplier(String data) { return normalize(data, MAX_OUTPUT); }

    public static String dataUrl(Image image) {
        return image == null ? null : "data:image/png;base64," + Base64.getEncoder().encodeToString(image.png());
    }

    private Image normalize(String data, int maxInput) {
        if (data == null || data.isEmpty()) return null;
        if (data.length() > 4 * ((maxInput + 2) / 3) + 23) throw invalid();
        String prefix, expected;
        if (data.startsWith("data:image/png;base64,")) { prefix = "data:image/png;base64,"; expected = "png"; }
        else if (data.startsWith("data:image/jpeg;base64,")) { prefix = "data:image/jpeg;base64,"; expected = "JPEG"; }
        else throw invalid();
        if (!decoding.tryAcquire()) throw new BusinessException("图片处理繁忙，请稍后使用原请求编号重试");
        ImageReader reader = null;
        BufferedImage source = null, clean = null;
        try {
            byte[] input = Base64.getDecoder().decode(data.substring(prefix.length()));
            if (input.length == 0 || input.length > maxInput) throw invalid();
            try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw invalid();
                reader = readers.next();
                if (!reader.getFormatName().equalsIgnoreCase(expected)) throw invalid();
                reader.setInput(stream, true, true); // Seek forward, ignore all EXIF/text/application metadata.
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_EDGE || height > MAX_EDGE
                        || (long) width * height > MAX_PIXELS) throw invalid();
                source = reader.read(0);
                if (source == null) throw invalid();
                clean = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                // Copy exact straight-alpha pixels; repeated normalization must not round
                // transparent RGB channels and invalidate the supplier's image acknowledgement.
                int[] scanline = new int[width];
                for (int y = 0; y < height; y++) {
                    source.getRGB(0, y, width, 1, scanline, 0, width);
                    clean.setRGB(0, y, width, 1, scanline, 0, width);
                }
                var bytes = new ByteArrayOutputStream();
                // Re-encode pixels into a new PNG; trailing polyglot content and metadata are discarded.
                try (var limited = new OutputStream() {
                    @Override public void write(int value) throws IOException {
                        if (bytes.size() >= MAX_OUTPUT) throw new IOException("image limit");
                        bytes.write(value);
                    }
                    @Override public void write(byte[] value, int offset, int length) throws IOException {
                        if ((long) bytes.size() + length > MAX_OUTPUT) throw new IOException("image limit");
                        bytes.write(value, offset, length);
                    }
                }; var output = new javax.imageio.stream.MemoryCacheImageOutputStream(limited)) {
                    var writers = ImageIO.getImageWritersByFormatName("png");
                    if (!writers.hasNext()) throw invalid();
                    var writer = writers.next();
                    try { writer.setOutput(output); writer.write(null, new javax.imageio.IIOImage(clean, null, null), writer.getDefaultWriteParam()); }
                    finally { writer.dispose(); }
                }
                return new Image(bytes.toByteArray(), width, height);
            }
        } catch (BusinessException e) { throw e; }
        catch (IOException | RuntimeException e) { throw invalid(); }
        finally {
            if (reader != null) reader.dispose();
            if (source != null) source.flush();
            if (clean != null) clean.flush();
            decoding.release();
        }
    }

    public static final class InvalidImageException extends BusinessException {
        private InvalidImageException(String message) { super(message); }
    }

    private static BusinessException invalid() {
        return new InvalidImageException("图片须为真实PNG/JPEG，原图不超过2MiB，单边不超过4096、总像素不超过400万；请缩小图片后重试");
    }
}
