package com.course.platform.application.service.catalogrefresh;

import com.course.platform.domain.catalogrefresh.CatalogRefreshTypes.*;

public interface CatalogRefreshService {
    View preview(PreviewForm form);

    View get(String id);

    View confirm(String id, ConfirmForm form);
}
