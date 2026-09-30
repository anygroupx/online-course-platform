package com.course.platform.infra.projectclient;

import org.springframework.stereotype.Component;
import java.util.Base64;

/** Bounded local raster decoder. Never accepts URLs, writes files, or preserves source metadata. */
@Component
public class ProjectTicketImageCodec {
    public static final int MAX_INPUT = SafeRasterCodec.MAX_INPUT;
    public static final int MAX_OUTPUT = SafeRasterCodec.MAX_OUTPUT;
    public static final int MAX_PIXELS = SafeRasterCodec.MAX_PIXELS;
    public static final int MAX_EDGE = SafeRasterCodec.MAX_EDGE;
    private final SafeRasterCodec codec;

    public ProjectTicketImageCodec() {
        this(new SafeRasterCodec());
    }

    public ProjectTicketImageCodec(SafeRasterCodec codec) {
        this.codec = codec;
    }

    public record Image(byte[] png, int width, int height) {
        @Override public String toString() { return "TicketImage[REDACTED]"; }
    }

    public Image normalize(String data) {
        try {
            return image(codec.normalizeDataUrl(data));
        } catch (SafeRasterCodec.InvalidImageException exception) {
            throw new InvalidImageException(exception.getMessage());
        }
    }

    /** Bounded supplier echoes may contain a PNG expanded by our normalization step. */
    public Image normalizeSupplier(String data) {
        try {
            return image(codec.normalizeSupplierDataUrl(data));
        } catch (SafeRasterCodec.InvalidImageException exception) {
            throw new InvalidImageException(exception.getMessage());
        }
    }

    public static String dataUrl(Image image) {
        return image == null ? null : "data:image/png;base64," + Base64.getEncoder().encodeToString(image.png());
    }

    private static Image image(SafeRasterCodec.Image value) {
        return value == null ? null : new Image(value.png(), value.width(), value.height());
    }

    /** Kept for callers that distinguish unsupported supplier images from other business errors. */
    public static final class InvalidImageException extends com.course.platform.common.exception.BusinessException {
        private InvalidImageException(String message) {
            super(message);
        }
    }
}
