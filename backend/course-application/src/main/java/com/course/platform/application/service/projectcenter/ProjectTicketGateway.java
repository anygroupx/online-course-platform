package com.course.platform.application.service.projectcenter;

import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.projectcenter.ProjectTicketTypes.*;

public interface ProjectTicketGateway {
    Receipt submitTicket(
            ApiProvider provider, String projectId, String customerKey, SubmitForm form);

    Receipt ticket(ApiProvider provider, String projectId, String customerKey, String ticketId);

    Receipt replyTicket(
            ApiProvider provider,
            String projectId,
            String customerKey,
            String ticketId,
            String content);

    Receipt reviewTicket(
            ApiProvider provider, String projectId, String ticketId, String result, String note);
}
