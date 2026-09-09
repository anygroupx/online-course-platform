package com.course.platform.application.service.projectcenter;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.projectcenter.ProjectTicketTypes.*;

public interface ProjectTicketService {
    IPage<TicketView> tickets(int page, int size, String accountId, boolean admin);

    TicketView ticket(String id, boolean admin);

    TicketView refresh(String id, boolean admin);

    OperationView submit(String accountId, SubmitForm form);

    OperationView reply(String ticketId, ReplyForm form);

    OperationView review(String ticketId, ReviewForm form);

    OperationView operation(String id, boolean admin);

    OperationView confirm(String id, boolean admin);

    OperationView resolve(String id, ResolveForm form);
}
