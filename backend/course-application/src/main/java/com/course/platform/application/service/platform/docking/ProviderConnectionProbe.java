package com.course.platform.application.service.platform.docking;

import com.course.platform.domain.entity.ApiProvider;

/** A saved provider can be verified without also being a course-order adapter. */
public interface ProviderConnectionProbe {
    String getProviderType();

    /** Read-only, bounded, and never an order, financial action or automatic retry. */
    void testConnection(ApiProvider provider);
}
