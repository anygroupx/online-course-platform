package com.course.platform.application.service.projectcenter;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.projectcenter.ProjectCenterTypes.*;

import java.util.List;

public interface ProjectCenterService {
    List<CatalogItem> catalog(Long providerId);

    IPage<ProjectView> projects(int page, int size, boolean admin);

    ProjectView save(Long id, ProjectForm form);

    AccountView refresh(String accountId);

    OperationView quote(Long projectId, QuoteForm form);

    OperationView confirm(String operationId);

    OperationView operation(String id, boolean admin);

    IPage<OperationView> operations(int page, int size, Long projectId, boolean admin);

    OperationView resolve(String id, ResolveForm form);
}
