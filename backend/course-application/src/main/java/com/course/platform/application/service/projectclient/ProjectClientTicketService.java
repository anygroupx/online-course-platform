package com.course.platform.application.service.projectclient;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.projectclient.ProjectClientTypes.Caller;
import com.course.platform.domain.projectclient.ProjectClientTicketTypes.*;

public interface ProjectClientTicketService {
    IPage<TicketView> list(Caller caller, String clientId, String status, String kind, int page, int size);
    TicketView ticket(Caller caller, String id);
    IPage<ReplyView> replies(Caller caller, String id, int page, int size);
    byte[] image(Caller caller, String id);
    Receipt create(Caller caller, CreateForm form);
    Receipt reply(Caller caller, String id, ReplyForm form);
    Receipt decide(Caller caller, String id, DecisionForm form);
    Receipt byRequest(Caller caller, String requestId);
}
