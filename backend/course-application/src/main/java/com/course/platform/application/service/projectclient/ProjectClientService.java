package com.course.platform.application.service.projectclient;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.projectclient.ProjectClientTypes.*;

public interface ProjectClientService {
    IPage<ProjectView> projects(Caller caller, int page, int size);

    IPage<ClientView> clients(Caller caller, Long projectId, int page, int size);

    ClientView client(Caller caller, String id);

    OperationView quote(Caller caller, QuoteForm form);

    OperationView confirm(Caller caller, String id, ConfirmForm form);

    OperationView operation(Caller caller, String id);

    OperationView byRequest(Caller caller, String requestId);

    IPage<OperationView> operations(Caller caller, String clientId, int page, int size);

    ClientView status(Caller caller, String id, StatusForm form);

    Stats stats(Caller caller);
}
