package com.course.platform.application.service.projectcenter;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.projectcenter.ServiceProjectAccount;

/** Trusted server-side context only. Never return this object through an HTTP controller. */
public interface ProjectAccountAccess {
    Context forTickets(String accountId, boolean admin);

    record Context(
            ServiceProjectAccount account,
            ApiProvider provider,
            String customerKey,
            String projectTitle) {
        @Override
        public String toString() {
            return "ProjectAccountContext[REDACTED]";
        }
    }
}
