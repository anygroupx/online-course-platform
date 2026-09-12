package com.course.platform.application.service.servicecommerce;

/** Internal maintenance only. No HTTP endpoint or user impersonation grants access to this API. */
public interface ServiceOrderStatusRefresh {
    boolean statusRefreshActive();

    /** Read a bounded batch of due, bound orders. Never dispatch purchases or settle money. */
    int refreshDueStatuses();
}
