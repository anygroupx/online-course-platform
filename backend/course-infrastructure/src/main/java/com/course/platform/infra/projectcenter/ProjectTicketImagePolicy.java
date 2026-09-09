package com.course.platform.infra.projectcenter;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.infra.projectclient.ProjectTicketImageCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Supplier-ticket inline rasters. Remote URLs are opaque unsupported attachments, never fetched. */
@Component
@RequiredArgsConstructor
public class ProjectTicketImagePolicy {
    public static final int MAX_INLINE_CHARS = 5_592_432;
    public static final int MAX_RECEIPT_IMAGE_CHARS = 6 * 1024 * 1024;
    private final ProjectTicketImageCodec codec;

    public String upload(String data) {
        return ProjectTicketImageCodec.dataUrl(codec.normalize(data));
    }

    /** The stored upload may have expanded to a 4MiB PNG after stripping source metadata. */
    public String outgoing(String data) {
        return ProjectTicketImageCodec.dataUrl(codec.normalizeSupplier(data));
    }

    public byte[] stored(String data) {
        if (data == null || !data.startsWith("data:image/png;base64,"))
            throw new BusinessException("没有可安全显示的内嵌图片；不会打开或下载外部附件链接");
        var image = codec.normalizeSupplier(data);
        if (image == null) throw new BusinessException("图片缓存不可用，请检查原工单");
        return image.png();
    }

    public Budget receipt() { return new Budget(); }

    public final class Budget {
        private int used;
        public String image(String source) {
            if (source == null || source.isEmpty()) return null;
            if (source.length() > MAX_INLINE_CHARS) throw new BusinessException("上游图片回执超出安全限制");
            if (!source.startsWith("data:image/png;base64,") && !source.startsWith("data:image/jpeg;base64,")) return null;
            final String normalized;
            try { normalized = ProjectTicketImageCodec.dataUrl(codec.normalizeSupplier(source)); }
            catch (ProjectTicketImageCodec.InvalidImageException unsupported) { return null; }
            used += normalized == null ? 0 : normalized.length();
            if (used > MAX_RECEIPT_IMAGE_CHARS) throw new BusinessException("上游图片回执总量超出安全限制");
            return normalized;
        }
    }
}
